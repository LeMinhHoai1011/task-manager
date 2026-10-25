package vn.smarttask.common.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.smarttask.common.AppException;
import vn.smarttask.common.entity.BackgroundJob;
import vn.smarttask.common.repo.BackgroundJobRepository;
import vn.smarttask.common.UuidV7;

/**
 * F3 job service: idempotent enqueue, lease (owner + expiry), terminal
 * transitions with audit, retry with bounded backoff, timeout + crash
 * recovery. Structured errors only (error_code, never a stack trace).
 */
@Service
public class JobService {
  /** Terminal states: never re-leased. */
  public static final List<String> TERMINAL =
      List.of("SUCCEEDED", "FAILED", "CANCELLED");

  private final BackgroundJobRepository repo;
  private final AuditService audit;

  public JobService(BackgroundJobRepository repo, AuditService audit) {
    this.repo = repo; this.audit = audit;
  }

  /** Idempotent enqueue: a repeated key returns the original job, never a new row. */
  @Transactional
  public BackgroundJob enqueue(String kind, String payloadJson, UUID correlationId,
      String idempotencyKey) {
    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      var existing = repo.findByIdempotencyKey(idempotencyKey);
      if (existing.isPresent()) return existing.get();
    }
    BackgroundJob j = new BackgroundJob(UuidV7.generate(), kind,
        payloadJson == null ? "{}" : payloadJson);
    j.setStatus("QUEUED");
    j.setCorrelationId(correlationId);
    j.setIdempotencyKey(idempotencyKey);
    j.setMaxAttempts(5);
    j.setTimeoutMs(300000L);
    repo.save(j);
    audit.append(null, "job.enqueue", "background_job", j.getId(), "ALLOWED", kind,
        "{\"kind\":\"" + kind + "\"}");
    return j;
  }

  /**
   * Lease stub using FOR UPDATE SKIP LOCKED (kept for compat; prefer
   * {@link #claim(int, String, Duration)}).
   */
  @Transactional
  public List<BackgroundJob> lease(int limit) {
    List<BackgroundJob> jobs = repo.leaseQueued(limit);
    for (BackgroundJob j : jobs) {
      j.setStatus("LEASED");
      repo.save(j);
    }
    return jobs;
  }

  /**
   * F3 claim: QUEUED + due FAILED retries, stamped RUNNING with owner + lease
   * expiry. Exactly one winner per row across concurrent workers (SKIP LOCKED).
   */
  @Transactional
  public List<BackgroundJob> claim(int limit, String owner, Duration leaseTtl) {
    List<BackgroundJob> due = repo.claimDue(limit);
    List<BackgroundJob> out = new ArrayList<>();
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    for (BackgroundJob j : due) {
      j.setStatus("RUNNING");
      j.setOwner(owner);
      j.setLeaseExpiresAt(now.plus(leaseTtl));
      j.setAttempts(j.getAttempts() + 1);
      if (j.getStartedAt() == null) j.setStartedAt(now);
      repo.save(j);
      audit.append(null, "job.lease", "background_job", j.getId(), "ALLOWED", j.getKind(),
          "{\"owner\":\"" + owner + "\",\"attempt\":" + j.getAttempts() + "}");
      out.add(j);
    }
    return out;
  }

  /** Owner-guarded success: only the lease holder may complete. */
  @Transactional
  public BackgroundJob complete(UUID jobId, String owner) {
    BackgroundJob j = requireLease(jobId, owner, "job.complete");
    j.setStatus("SUCCEEDED");
    j.setFinishedAt(OffsetDateTime.now(ZoneOffset.UTC));
    j.setOwner(null);
    j.setLeaseExpiresAt(null);
    repo.save(j);
    audit.append(null, "job.complete", "background_job", jobId, "ALLOWED", j.getKind(), "{}");
    return j;
  }

  /**
   * Owner-guarded failure: retryable (attempt left) -> FAILED with backoff
   * (re-leased when due); exhausted -> FAILED terminal. Structured error_code.
   */
  @Transactional
  public BackgroundJob fail(UUID jobId, String owner, String errorCode, boolean retryable) {
    BackgroundJob j = requireLease(jobId, owner, "job.fail");
    j.setErrorCode(errorCode == null ? "JOB_FAILED" : errorCode);
    if (retryable && j.getAttempts() < j.getMaxAttempts()) {
      j.setStatus("FAILED");
      j.setNextAttemptAt(OffsetDateTime.now(ZoneOffset.UTC).plus(backoff(j.getAttempts())));
      j.setOwner(null);
      j.setLeaseExpiresAt(null);
      repo.save(j);
      audit.append(null, "job.retry", "background_job", jobId, "ALLOWED", j.getErrorCode(),
          "{\"attempt\":" + j.getAttempts() + "}");
    } else {
      j.setStatus("FAILED");
      j.setAttempts(j.getMaxAttempts()); // terminal: claimDue only re-leases attempts < max
      j.setFinishedAt(OffsetDateTime.now(ZoneOffset.UTC));
      j.setOwner(null);
      j.setLeaseExpiresAt(null);
      repo.save(j);
      audit.append(null, "job.fail", "background_job", jobId, "ALLOWED", j.getErrorCode(),
          "{\"attempt\":" + j.getAttempts() + "}");
    }
    return j;
  }

  /** Timeout: RUNNING past its deadline -> FAILED_TIMEOUT (retry if attempts left). */
  @Transactional
  public BackgroundJob failTimeout(UUID jobId, String owner) {
    BackgroundJob j = requireLease(jobId, owner, "job.timeout");
    return fail(jobId, owner, "FAILED_TIMEOUT", true);
  }

  /** Revoked mid-job -> CANCELLED (terminal, never retried). */
  @Transactional
  public BackgroundJob cancel(UUID jobId, String owner, String reason) {
    BackgroundJob j = repo.findById(jobId)
        .orElseThrow(() -> AppException.notFound("Job not found"));
    if (owner != null && j.getOwner() != null && !owner.equals(j.getOwner())) {
      audit.appendDenied(null, "job.cancel", "background_job", jobId, "LEASE_HELD_BY_OTHER");
      throw AppException.stateConflict("JOB_LEASE_HELD", "Job leased by another worker");
    }
    j.setStatus("CANCELLED");
    j.setErrorCode(reason == null ? "CANCELLED_REVOKED" : reason);
    j.setFinishedAt(OffsetDateTime.now(ZoneOffset.UTC));
    j.setOwner(null);
    j.setLeaseExpiresAt(null);
    repo.save(j);
    audit.append(null, "job.cancel", "background_job", jobId, "ALLOWED", j.getErrorCode(), "{}");
    return j;
  }

  /**
   * Startup crash recovery: RUNNING rows whose lease expired are re-queued
   * (attempt preserved; next claim retries when attempts remain).
   */
  @Transactional
  public int reclaimExpiredLeases(int limit, String owner) {
    List<BackgroundJob> stale = repo.claimStaleRunning(limit);
    for (BackgroundJob j : stale) {
      j.setStatus("FAILED");
      j.setErrorCode("WORKER_CRASH");
      j.setNextAttemptAt(OffsetDateTime.now(ZoneOffset.UTC));
      j.setOwner(null);
      j.setLeaseExpiresAt(null);
      repo.save(j);
      audit.append(null, "job.reclaim", "background_job", j.getId(), "ALLOWED",
          "WORKER_CRASH", "{\"prev_owner\":\"" + owner + "\"}");
    }
    return stale.size();
  }

  /** Sweep: RUNNING rows past their per-kind timeout (started_at + timeout_ms). */
  @Transactional
  public int sweepTimeouts(int limit) {
    List<BackgroundJob> running = repo.findByStatus("RUNNING");
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    int n = 0;
    for (BackgroundJob j : running) {
      if (n >= limit) break;
      OffsetDateTime deadline = j.getStartedAt() == null ? null
          : j.getStartedAt().plus(Duration.ofMillis(Math.max(1000L, j.getTimeoutMs())));
      boolean leaseDead = j.getLeaseExpiresAt() != null && !j.getLeaseExpiresAt().isAfter(now);
      if ((deadline != null && !deadline.isAfter(now)) || leaseDead) {
        j.setErrorCode("FAILED_TIMEOUT");
        if (j.getAttempts() < j.getMaxAttempts()) {
          j.setStatus("FAILED");
          j.setNextAttemptAt(now.plus(backoff(j.getAttempts())));
        } else {
          j.setStatus("FAILED");
          j.setAttempts(j.getMaxAttempts()); // terminal: never re-leased
          j.setFinishedAt(now);
        }
        j.setOwner(null);
        j.setLeaseExpiresAt(null);
        repo.save(j);
        audit.append(null, "job.timeout", "background_job", j.getId(), "ALLOWED",
            "FAILED_TIMEOUT", "{\"attempt\":" + j.getAttempts() + "}");
        n++;
      }
    }
    return n;
  }

  /** Bounded backoff: 2^attempt seconds, capped at 5 minutes. */
  public static Duration backoff(int attempt) {
    long exp = Math.min(Math.max(attempt, 1), 9);
    return Duration.ofSeconds(Math.min(1L << exp, 300L));
  }

  private BackgroundJob requireLease(UUID jobId, String owner, String action) {
    BackgroundJob j = repo.findById(jobId)
        .orElseThrow(() -> AppException.notFound("Job not found"));
    if (!"RUNNING".equals(j.getStatus()) && !"LEASED".equals(j.getStatus())) {
      throw AppException.stateConflict("JOB_NOT_RUNNING", "Job is not leased");
    }
    if (owner != null && j.getOwner() != null && !owner.equals(j.getOwner())) {
      audit.appendDenied(null, action, "background_job", jobId, "LEASE_HELD_BY_OTHER");
      throw AppException.stateConflict("JOB_LEASE_HELD", "Job leased by another worker");
    }
    return j;
  }

  /** Reauthorize before sensitive step: requires explicit reauth flag. */
  public void requireReauth(boolean reauthed, UUID actor, String action) {
    if (!reauthed) {
      audit.appendDenied(actor, action, "background_job", null, "REAUTH_REQUIRED");
      throw AppException.reauthRequired("Re-authentication required before " + action);
    }
  }
}
