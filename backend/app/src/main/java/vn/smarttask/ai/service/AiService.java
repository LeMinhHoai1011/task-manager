package vn.smarttask.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.smarttask.ai.AiErrors;
import vn.smarttask.ai.AiFeature;
import vn.smarttask.ai.AiJobLifecycle;
import vn.smarttask.ai.AiJobStatus;
import vn.smarttask.ai.AiSuggestionStatus;
import vn.smarttask.ai.ContextManifestValidator;
import vn.smarttask.ai.OllamaClient;
import vn.smarttask.ai.OllamaException;
import vn.smarttask.ai.OutputValidator;
import vn.smarttask.ai.ProjectApi;
import vn.smarttask.ai.PromptSanitizer;
import vn.smarttask.ai.entity.AiInteraction;
import vn.smarttask.ai.entity.AiJob;
import vn.smarttask.ai.entity.AiSuggestion;
import vn.smarttask.ai.repo.AiContextManifestRepository;
import vn.smarttask.ai.repo.AiInteractionRepository;
import vn.smarttask.ai.repo.AiJobRepository;
import vn.smarttask.ai.repo.AiModelConfigurationRepository;
import vn.smarttask.ai.repo.AiSuggestionRepository;
import vn.smarttask.common.AppException;
import vn.smarttask.common.UuidV7;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.common.service.JobService;
import vn.smarttask.task.service.TaskService;

/**
 * PH-AI minimal MANUAL service (baseline Nhom 07 + 05 S8 + 04 AI-01..05 + 03 EP-194..206).
 *
 * <p>HARD RULES (never relax):
 * <ul>
 *   <li>AI never mutates task / approval_decision / task.state. The only write path to
 *       tasks is {@link #accept(UUID, UUID)}, which builds a draft and calls the standard
 *       {@link TaskService#create} use case with actor=user (AI-03).</li>
 *   <li>No dependency on WorkflowService mutation paths (read + TaskService.accept only).</li>
 *   <li>Double re-authorization: once before context build, once (REAUTHORIZING) before
 *       delivery. Lost permission -&gt; CANCELLED_REVOKED + redacted suggestion + audit,
 *       never retried.</li>
 *   <li>Other users' jobs (even SA) read as 404 (AI-02).</li>
 * </ul>
 */
@Service
public class AiService {
  private final AiJobRepository jobs;
  private final AiSuggestionRepository suggestions;
  private final AiContextManifestRepository manifests;
  private final AiModelConfigurationRepository configs;
  private final AiInteractionRepository interactions;
  private final ProjectApi projectApi;
  private final TaskService taskService;
  private final OllamaClient ollama;
  private final AuditService audit;
  private final Clock clock;
  private final ObjectMapper om;
  // F3 optional wiring (setter-injected, absent in unit tests):
  // - jobRunner: production pipeline (Ollama strictly outside any tx).
  //   Null in unit tests -> legacy inline run() (same sync behaviour).
  // - txTemplate: short tx for the enqueue block. Null -> direct (tests).
  // - bgJobs: AI_GENERATE background-job enqueue. Null -> skipped (tests).
  private volatile AiJobRunner jobRunner;
  private volatile TransactionTemplate txTemplate;
  private volatile JobService bgJobs;

  public AiService(AiJobRepository jobs, AiSuggestionRepository suggestions,
      AiContextManifestRepository manifests, AiModelConfigurationRepository configs,
      AiInteractionRepository interactions, ProjectApi projectApi, TaskService taskService,
      OllamaClient ollama, AuditService audit, Clock clock, ObjectMapper om) {
    this.jobs = jobs;
    this.suggestions = suggestions;
    this.manifests = manifests;
    this.configs = configs;
    this.interactions = interactions;
    this.projectApi = projectApi;
    this.taskService = taskService;
    this.ollama = ollama;
    this.audit = audit;
    this.clock = clock;
    this.om = om;
  }

  // ---------------------------------------------------------------- request

  @Autowired(required = false)
  public void setJobRunner(AiJobRunner jobRunner) {
    this.jobRunner = jobRunner;
  }

  @Autowired(required = false)
  public void setTxTemplate(TransactionTemplate txTemplate) {
    this.txTemplate = txTemplate;
  }

  @Autowired(required = false)
  public void setBgJobs(JobService bgJobs) {
    this.bgJobs = bgJobs;
  }

  private <T> T inShortTx(java.util.function.Supplier<T> block) {
    TransactionTemplate t = txTemplate;
    if (t != null) return t.execute(st -> block.get());
    return block.get();
  }

  /**
   * F3: validate + enqueue in ONE short tx (QUEUED row + AI_GENERATE
   * background job), then execute. Production executes via
   * {@link AiJobRunner} (Ollama outside any tx); without a runner (unit
   * tests) the legacy inline pipeline runs synchronously.
   */
  public AiJob request(UUID principal, AiFeature feature, UUID projectId, String inputHint,
      List<String> requestedFields, String idempotencyKey) {
    if (principal == null || !projectApi.isActiveUser(principal)) {
      throw AppException.notFound("User not found");
    }
    if (inputHint != null && inputHint.length() > PromptSanitizer.MAX_INPUT_HINT) {
      throw AppException.badRequest("INPUT_HINT_TOO_LONG", "input_hint must be <= 2000 chars");
    }
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw AppException.badRequest("IDEMPOTENCY_REQUIRED", "idempotency_key required");
    }
    AiJob job = inShortTx(() -> enqueueJob(principal, feature, projectId, inputHint,
        requestedFields, idempotencyKey));
    AiJobRunner r = jobRunner;
    if (r != null) return r.execute(job.getId(), principal);
    return run(job.getId(), principal);
  }

  /** Short-tx enqueue block: idempotency check + manifest gate + QUEUED row + bg job. */
  private AiJob enqueueJob(UUID principal, AiFeature feature, UUID projectId, String inputHint,
      List<String> requestedFields, String idempotencyKey) {
    var existing = jobs.findByIdempotencyKey(idempotencyKey);
    if (existing.isPresent()) return existing.get();

    // Fail-fast manifest + scope gate BEFORE any job row (403 AI_CONTEXT_DENIED).
    validateManifest(feature, projectId, principal, requestedFields);

    AiJob job = new AiJob(UuidV7.generate(), principal, feature.name(), idempotencyKey);
    job.setProjectId(projectId);
    job.setInputHint(PromptSanitizer.sanitizeHint(inputHint));
    job.setMaxAttempts(readIntConfig("ai.max_attempts", 3));
    job.setTimeoutMs(readLongConfig("ollama.timeout_ms", 30000L));
    jobs.save(job);
    JobService bg = bgJobs;
    if (bg != null) {
      bg.enqueue("AI_GENERATE", "{\"ai_job_id\":\"" + job.getId() + "\"}", null,
          "ai-" + job.getId());
    }
    audit.append(principal, "ai.request", "ai_job", job.getId(), "ALLOWED", null,
        "{\"at\":\"" + clock.instant() + "\"}");
    return job;
  }

  // ---------------------------------------------------------------- pipeline

  /** Synchronous minimal pipeline: CONTEXT_BUILDING -> RUNNING -> VALIDATING -> REAUTHORIZING. */
  @Transactional
  public AiJob run(UUID jobId, UUID actor) {
    AiJob job = requireOwned(jobId, actor);
    move(job, AiJobStatus.CONTEXT_BUILDING);
    // Reauthorize #1 (before build): active user + still in scope.
    if (!projectApi.isActiveUser(actor)
        || (job.getProjectId() != null && !projectApi.hasParticipation(job.getProjectId(), actor))) {
      return cancelRevoked(job, actor, "REAUTH_BEFORE_BUILD");
    }
    // Re-validate manifest at build time (defense in depth; pre-enqueue check may be stale).
    validateManifest(AiFeature.valueOf(job.getFeature()), job.getProjectId(), actor, List.of());

    move(job, AiJobStatus.RUNNING);
    String prompt = PromptSanitizer.buildPrompt(AiFeature.valueOf(job.getFeature()),
        job.getInputHint(), List.of());
    interactions.save(new AiInteraction(UuidV7.generate(), job.getId(),
        "private://ai-prompts/" + job.getId() + ".txt"));

    String raw;
    try {
      raw = ollama.generate(prompt, job.getTimeoutMs());
    } catch (OllamaException e) {
      if (e.getKind() == OllamaException.Kind.TIMEOUT) {
        return fail(job, actor, AiJobStatus.FAILED_TIMEOUT, "OLLAMA_TIMEOUT");
      }
      return fail(job, actor, AiJobStatus.FAILED_OLLAMA, "OLLAMA_DOWN");
    }

    move(job, AiJobStatus.VALIDATING);
    OutputValidator.Draft draft;
    try {
      draft = OutputValidator.validate(raw, job.getProjectId(), projectApi, om);
    } catch (AppException e) {
      return fail(job, actor, AiJobStatus.FAILED_VALIDATION, "OUTPUT_SCHEMA");
    }
    AiSuggestion suggestion = new AiSuggestion(UuidV7.generate(), job.getId(), raw);
    suggestions.save(suggestion);
    job.setOutputRef("suggestion://" + suggestion.getId());
    jobs.save(job);

    move(job, AiJobStatus.REAUTHORIZING);
    // Reauthorize #2 (before delivery): lost permission -> revoke + redact + audit, no retry.
    if (!projectApi.isActiveUser(actor)
        || (job.getProjectId() != null && !projectApi.hasParticipation(job.getProjectId(), actor))) {
      suggestion.setPayload("{}");
      suggestion.setStatus(AiSuggestionStatus.REDACTED.name());
      suggestions.save(suggestion);
      return cancelRevoked(job, actor, "REAUTH_BEFORE_DELIVERY");
    }

    move(job, AiJobStatus.COMPLETED);
    audit.append(actor, "ai.complete", "ai_job", job.getId(), "ALLOWED", null,
        "{\"title\":\"" + escape(draft.title()) + "\"}");
    return job;
  }

  // ---------------------------------------------------------------- read

  /** AI-02: someone else's job (even SA) reads as 404. Revoked scope also reads as 404. */
  @Transactional(readOnly = true)
  public AiJob status(UUID actor, UUID jobId) {
    return requireVisible(jobId, actor);
  }

  @Transactional(readOnly = true)
  public AiSuggestion suggestionOf(UUID actor, UUID jobId) {
    AiJob job = requireVisible(jobId, actor);
    return suggestions.findByJobId(job.getId())
        .orElseThrow(() -> AppException.notFound("AI suggestion not found"));
  }

  @Transactional(readOnly = true)
  public List<AiJob> history(UUID actor) {
    return jobs.findByPrincipalUserIdOrderByCreatedAtDesc(actor);
  }

  // ---------------------------------------------------------------- accept / reject (AI-03)

  /**
   * AI-03: accept only materializes the validated DRAFT through the standard
   * TaskService.create use case with actor=user. AI itself writes no task row.
   */
  @Transactional
  public vn.smarttask.task.entity.Task accept(UUID actor, UUID jobId) {
    AiJob job = requireVisible(jobId, actor);
    if (!AiJobStatus.COMPLETED.name().equals(job.getStatus())) {
      throw AppException.stateConflict("AI_JOB_NOT_COMPLETED", "Only COMPLETED jobs can be accepted");
    }
    AiSuggestion suggestion = suggestions.findByJobId(job.getId())
        .orElseThrow(() -> AppException.notFound("AI suggestion not found"));
    if (!AiSuggestionStatus.DRAFT.name().equals(suggestion.getStatus())) {
      throw AppException.stateConflict("AI_SUGGESTION_NOT_DRAFT", "Suggestion is not a draft");
    }
    OutputValidator.Draft draft =
        OutputValidator.validate(suggestion.getPayload(), job.getProjectId(), projectApi, om);
    if (job.getProjectId() == null) {
      throw AppException.badRequest("AI_NO_PROJECT", "Draft has no project scope");
    }
    var task = taskService.create(actor, job.getProjectId(), draft.title(),
        draft.description(), draft.priority(), null);
    suggestion.setStatus(AiSuggestionStatus.ACCEPTED.name());
    suggestions.save(suggestion);
    audit.append(actor, "ai.accept", "ai_job", job.getId(), "ALLOWED", null,
        "{\"task_id\":\"" + task.getId() + "\"}");
    return task;
  }

  @Transactional
  public AiSuggestion reject(UUID actor, UUID jobId) {
    AiJob job = requireVisible(jobId, actor);
    AiSuggestion suggestion = suggestions.findByJobId(job.getId())
        .orElseThrow(() -> AppException.notFound("AI suggestion not found"));
    if (!AiSuggestionStatus.DRAFT.name().equals(suggestion.getStatus())) {
      throw AppException.stateConflict("AI_SUGGESTION_NOT_DRAFT", "Suggestion is not a draft");
    }
    suggestion.setStatus(AiSuggestionStatus.REJECTED.name());
    suggestions.save(suggestion);
    audit.append(actor, "ai.reject", "ai_job", job.getId(), "ALLOWED", null, "{}");
    return suggestion;
  }

  // ---------------------------------------------------------------- retry

  /** Retry only FAILED_TIMEOUT / FAILED_OLLAMA while attempt &lt; maxAttempts. */
  @Transactional
  public AiJob retry(UUID actor, UUID jobId) {
    AiJobRunner r = jobRunner;
    if (r != null) {
      inShortTx(() -> {
        r.prepareRetry(actor, jobId);
        JobService bg = bgJobs;
        if (bg != null) {
          bg.enqueue("AI_GENERATE", "{\"ai_job_id\":\"" + jobId + "\"}", null,
              "ai-retry-" + jobId + "-" + System.nanoTime());
        }
        return null;
      });
      return r.execute(jobId, actor);
    }
    AiJob job = requireOwned(jobId, actor);
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
    return run(job.getId(), actor);
  }

  // ---------------------------------------------------------------- config (SA + grant + reauth)

  @Transactional(readOnly = true)
  public String getConfig(UUID actor, String key, boolean isSystemAdmin, boolean hasGrant) {
    requireConfigAdmin(actor, isSystemAdmin, hasGrant);
    return configs.findById(key)
        .orElseThrow(() -> AppException.notFound("AI config not found")).getValue();
  }

  @Transactional
  public void updateConfig(UUID actor, String key, String value, boolean isSystemAdmin,
      boolean hasGrant, boolean reauthed) {
    requireConfigAdmin(actor, isSystemAdmin, hasGrant);
    if (!reauthed) {
      audit.appendDenied(actor, "ai.config.update", "ai_model_configuration", null, "REAUTH_REQUIRED");
      throw AppException.reauthRequired("Reauth required");
    }
    var cfg = configs.findById(key).orElse(new vn.smarttask.ai.entity.AiModelConfiguration(key, value));
    cfg.setValue(value);
    cfg.setUpdatedBy(actor);
    configs.save(cfg);
    audit.append(actor, "ai.config.update", "ai_model_configuration", null, "ALLOWED", null,
        "{\"key\":\"" + key + "\"}");
  }

  // ---------------------------------------------------------------- internals

  private void requireConfigAdmin(UUID actor, boolean isSystemAdmin, boolean hasGrant) {
    if (!isSystemAdmin || !hasGrant) {
      audit.appendDenied(actor, "ai.config", "ai_model_configuration", null, "OLLAMA_CONFIG_DENIED");
      throw AppException.forbidden("OLLAMA_CONFIG_MANAGE grant required");
    }
  }

  private AiJob requireOwned(UUID jobId, UUID actor) {
    AiJob job = jobs.findById(jobId).orElseThrow(() -> AppException.notFound("AI job not found"));
    if (actor == null || !actor.equals(job.getPrincipalUserId())) {
      throw AiErrors.notFound("AI job not found");
    }
    return job;
  }

  /** Owner + still in scope; used by read/accept paths so revoked scope reads as 404. */
  private AiJob requireVisible(UUID jobId, UUID actor) {
    AiJob job = requireOwned(jobId, actor);
    if (job.getProjectId() != null && !projectApi.hasParticipation(job.getProjectId(), actor)) {
      throw AiErrors.notFound("AI job not found");
    }
    return job;
  }

  private void move(AiJob job, AiJobStatus to) {
    AiJobStatus from = AiJobStatus.valueOf(job.getStatus());
    if (!AiJobLifecycle.canTransition(from, to)) {
      throw AppException.stateConflict("AI_BAD_TRANSITION", "Illegal AI transition " + from + "->" + to);
    }
    job.setStatus(to.name());
    jobs.save(job);
  }

  private AiJob fail(AiJob job, UUID actor, AiJobStatus terminal, String reason) {
    move(job, terminal);
    audit.append(actor, "ai.fail", "ai_job", job.getId(), "ALLOWED", reason, "{}");
    return job;
  }

  private AiJob cancelRevoked(AiJob job, UUID actor, String reason) {
    move(job, AiJobStatus.CANCELLED_REVOKED);
    audit.appendDenied(actor, "ai.job", "ai_job", job.getId(), reason);
    jobs.save(job);
    throw AiErrors.notFound("AI job revoked");
  }

  private void validateManifest(AiFeature feature, UUID projectId, UUID userId,
      List<String> requestedFields) {
    var manifest = manifests.findById(feature.name()).orElse(null);
    if (manifest == null) {
      throw AiErrors.contextDenied("No AI context manifest for " + feature);
    }
    ContextManifestValidator.validate(
        new ContextManifestValidator.ManifestData(manifest.isEnabled(),
            parseList(manifest.getAllowedFields()), parseList(manifest.getForbiddenPatterns())),
        requestedFields, projectId, userId, projectApi);
  }

  private List<String> parseList(String json) {
    try {
      List<String> v = om.readValue(json == null ? "[]" : json, new TypeReference<List<String>>() {});
      return v == null ? List.of() : v;
    } catch (Exception e) {
      return null; // unparsable manifest -> validator treats null as deny (secure default)
    }
  }

  private int readIntConfig(String key, int def) {
    try {
      return configs.findById(key).map(c -> Integer.parseInt(c.getValue().trim())).orElse(def);
    } catch (Exception e) {
      return def;
    }
  }

  private long readLongConfig(String key, long def) {
    try {
      return configs.findById(key).map(c -> Long.parseLong(c.getValue().trim())).orElse(def);
    } catch (Exception e) {
      return def;
    }
  }

  private static String escape(String s) {
    return s == null ? "" : s.replace("\"", "'");
  }
}
