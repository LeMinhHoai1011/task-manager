package vn.smarttask.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.smarttask.ai.AiJobLifecycle;
import vn.smarttask.ai.AiJobStatus;
import vn.smarttask.ai.AiSuggestionStatus;
import vn.smarttask.ai.entity.AiInteraction;
import vn.smarttask.ai.entity.AiJob;
import vn.smarttask.ai.entity.AiSuggestion;
import vn.smarttask.ai.repo.AiInteractionRepository;
import vn.smarttask.ai.repo.AiJobRepository;
import vn.smarttask.ai.repo.AiSuggestionRepository;
import vn.smarttask.common.AppException;
import vn.smarttask.common.UuidV7;
import vn.smarttask.common.service.AuditService;

/**
 * F3 AI transactional steps. Every method is its own short transaction; the
 * Ollama HTTP call always happens outside any tx (see {@code AiJobRunner}).
 */
@Service
public class AiJobStore {
  private final AiJobRepository jobs;
  private final AiSuggestionRepository suggestions;
  private final AiInteractionRepository interactions;
  private final AuditService audit;
  private final Clock clock;
  private final ObjectMapper om;

  public AiJobStore(AiJobRepository jobs, AiSuggestionRepository suggestions,
      AiInteractionRepository interactions, AuditService audit, Clock clock, ObjectMapper om) {
    this.jobs = jobs; this.suggestions = suggestions; this.interactions = interactions;
    this.audit = audit; this.clock = clock; this.om = om;
  }

  /** Detached snapshot for the tx-free orchestration in {@code AiJobRunner}. */
  @Transactional(readOnly = true)
  public Snapshot snapshot(UUID jobId) {
    AiJob job = jobs.findById(jobId)
        .orElseThrow(() -> AppException.notFound("AI job not found"));
    return new Snapshot(job.getId(), job.getPrincipalUserId(), job.getFeature(),
        job.getProjectId(), job.getInputHint(), job.getStatus(), job.getAttempt(),
        job.getMaxAttempts(), job.getTimeoutMs());
  }

  public record Snapshot(UUID id, UUID principalUserId, String feature, UUID projectId,
      String inputHint, String status, int attempt, int maxAttempts, long timeoutMs) {}

  @Transactional
  public Snapshot moveTo(UUID jobId, AiJobStatus to) {
    AiJob job = jobs.findById(jobId)
        .orElseThrow(() -> AppException.notFound("AI job not found"));
    AiJobStatus from = AiJobStatus.valueOf(job.getStatus());
    if (!AiJobLifecycle.canTransition(from, to)) {
      throw AppException.stateConflict("AI_BAD_TRANSITION",
          "Illegal AI transition " + from + "->" + to);
    }
    job.setStatus(to.name());
    jobs.save(job);
    return snapshotOf(job);
  }

  @Transactional
  public void saveInteraction(UUID jobId) {
    interactions.save(new AiInteraction(UuidV7.generate(), jobId,
        "private://ai-prompts/" + jobId + ".txt"));
  }

  @Transactional
  public Snapshot saveSuggestion(UUID jobId, String raw) {
    AiJob job = jobs.findById(jobId)
        .orElseThrow(() -> AppException.notFound("AI job not found"));
    AiSuggestion suggestion = new AiSuggestion(UuidV7.generate(), jobId, raw);
    suggestions.save(suggestion);
    job.setOutputRef("suggestion://" + suggestion.getId());
    jobs.save(job);
    return snapshotOf(job);
  }

  @Transactional
  public Snapshot fail(UUID jobId, UUID actor, AiJobStatus terminal, String reason) {
    moveTo(jobId, terminal);
    audit.append(actor, "ai.fail", "ai_job", jobId, "ALLOWED", reason, "{}");
    return snapshot(jobId);
  }

  /** Revoke before build (no suggestion row exists yet). */
  @Transactional
  public Snapshot cancelRevoked(UUID jobId, UUID actor, String reason) {
    moveTo(jobId, AiJobStatus.CANCELLED_REVOKED);
    audit.appendDenied(actor, "ai.job", "ai_job", jobId, reason);
    return snapshot(jobId);
  }

  /** Revoke before delivery: redact suggestion + cancel. */
  @Transactional
  public Snapshot redactAndCancel(UUID jobId, UUID actor, String reason) {
    AiJob job = jobs.findById(jobId)
        .orElseThrow(() -> AppException.notFound("AI job not found"));
    suggestions.findByJobId(jobId).ifPresent(s -> {
      s.setPayload("{}");
      s.setStatus(AiSuggestionStatus.REDACTED.name());
      suggestions.save(s);
    });
    moveTo(jobId, AiJobStatus.CANCELLED_REVOKED);
    audit.appendDenied(actor, "ai.job", "ai_job", jobId, reason);
    return snapshot(jobId);
  }

  @Transactional
  public Snapshot complete(UUID jobId, UUID actor, String title) {
    moveTo(jobId, AiJobStatus.COMPLETED);
    audit.append(actor, "ai.complete", "ai_job", jobId, "ALLOWED", null,
        "{\"title\":\"" + escape(title) + "\"}");
    return snapshot(jobId);
  }

  /** Retry gate: FAILED_TIMEOUT / FAILED_OLLAMA while attempt < max. */
  @Transactional
  public Snapshot enqueueRetry(UUID jobId, UUID actor) {
    AiJob job = jobs.findById(jobId)
        .orElseThrow(() -> AppException.notFound("AI job not found"));
    if (actor == null || !actor.equals(job.getPrincipalUserId())) {
      throw AppException.notFound("AI job not found");
    }
    AiJobStatus st = AiJobStatus.valueOf(job.getStatus());
    if (!AiJobLifecycle.canRetry(st, job.getAttempt(), job.getMaxAttempts())) {
      audit.appendDenied(actor, "ai.retry", "ai_job", job.getId(), "AI_RETRY_FORBIDDEN");
      throw AppException.stateConflict("AI_RETRY_FORBIDDEN", "Job cannot be retried");
    }
    job.setAttempt(job.getAttempt() + 1);
    job.setStatus(AiJobStatus.QUEUED.name());
    jobs.save(job);
    audit.append(actor, "ai.retry", "ai_job", job.getId(), "ALLOWED", null,
        "{\"attempt\":" + job.getAttempt() + "}");
    return snapshotOf(job);
  }

  private Snapshot snapshotOf(AiJob job) {
    return new Snapshot(job.getId(), job.getPrincipalUserId(), job.getFeature(),
        job.getProjectId(), job.getInputHint(), job.getStatus(), job.getAttempt(),
        job.getMaxAttempts(), job.getTimeoutMs());
  }

  private static String escape(String s) {
    return s == null ? "" : s.replace("\"", "'");
  }
}
