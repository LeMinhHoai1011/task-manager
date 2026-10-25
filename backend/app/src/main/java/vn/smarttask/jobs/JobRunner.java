package vn.smarttask.jobs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.smarttask.common.AppException;
import vn.smarttask.common.entity.BackgroundJob;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.common.service.JobService;

/**
 * F3 job worker: QUEUED -> RUNNING (lease owner/expiry) -> SUCCEEDED / FAILED
 * (retry while attempts remain + backoff) / CANCELLED (revoked mid-job).
 * Timeout sweep + unknown-kind parking included. Every transition audited;
 * errors are structured codes, never stacks.
 */
@Component
public class JobRunner {
  public static final int BATCH = 10;
  public static final Duration LEASE_TTL = Duration.ofMinutes(5);

  private final JobService jobs;
  private final List<JobHandler> handlers;
  private final AuditService audit;
  private final ObjectMapper om;

  public JobRunner(JobService jobs, List<JobHandler> handlers, AuditService audit,
      ObjectMapper om) {
    this.jobs = jobs; this.handlers = handlers; this.audit = audit; this.om = om;
  }

  /** One worker pass: timeouts, claims, executions. Returns outcome counts. */
  public Map<String, Integer> processBatch(String owner) {
    Map<String, Integer> counts = new LinkedHashMap<>();
    counts.put("SUCCEEDED", 0);
    counts.put("FAILED", 0);
    counts.put("CANCELLED", 0);
    counts.put("PARKED", 0);
    jobs.sweepTimeouts(50);
    List<BackgroundJob> batch = jobs.claim(BATCH, owner, LEASE_TTL);
    for (BackgroundJob j : batch) {
      String outcome = execute(j, owner);
      counts.merge(outcome, 1, Integer::sum);
    }
    return counts;
  }

  public Map<String, Integer> processBatch() {
    return processBatch("worker-" + UUID.randomUUID().toString().substring(0, 8));
  }

  private String execute(BackgroundJob job, String owner) {
    JobHandler h = handlers.stream()
        .filter(x -> x.kind().equals(job.getKind()))
        .findFirst().orElse(null);
    if (h == null) {
      // Unknown kind: park with a delay (never hot-loop, never fail чужое).
      jobs.fail(job.getId(), owner, "UNKNOWN_KIND_PARKED", false);
      audit.append(null, "job.park", "background_job", job.getId(), "ALLOWED",
          job.getKind(), "{}");
      return "PARKED";
    }
    try {
      h.handle(job, owner);
      jobs.complete(job.getId(), owner);
      return "SUCCEEDED";
    } catch (JobRevokedException rev) {
      jobs.cancel(job.getId(), owner, rev.getErrorCode());
      return "CANCELLED";
    } catch (AppException ae) {
      jobs.fail(job.getId(), owner,
          ae.getReasonCode() == null ? "JOB_FAILED" : ae.getReasonCode(),
          isTransient(ae));
      return "FAILED";
    } catch (Exception ex) {
      jobs.fail(job.getId(), owner, "JOB_CRASH", true);
      return "FAILED";
    }
  }

  /** Transient HTTP statuses retry; domain denials do not. */
  static boolean isTransient(AppException ae) {
    if (ae.getHttpStatus() == null) return true;
    return switch (ae.getHttpStatus().value()) {
      case 408, 429, 500, 502, 503, 504 -> true;
      default -> false;
    };
  }

  /** Payload helper for handlers. */
  public JsonNode payloadOf(BackgroundJob job) {
    try {
      return om.readTree(job.getPayload() == null ? "{}" : job.getPayload());
    } catch (Exception e) {
      return om.createObjectNode();
    }
  }
}
