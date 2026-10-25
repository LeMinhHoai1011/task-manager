package vn.smarttask.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import vn.smarttask.ai.AiErrors;
import vn.smarttask.ai.AiFeature;
import vn.smarttask.ai.AiJobStatus;
import vn.smarttask.ai.ContextManifestValidator;
import vn.smarttask.ai.OllamaClient;
import vn.smarttask.ai.OllamaException;
import vn.smarttask.ai.OutputValidator;
import vn.smarttask.ai.ProjectApi;
import vn.smarttask.ai.PromptSanitizer;
import vn.smarttask.ai.repo.AiContextManifestRepository;
import vn.smarttask.ai.repo.AiJobRepository;
import vn.smarttask.common.AppException;

/**
 * F3 AI pipeline runner: orchestrates the QUEUED -> ... -> COMPLETED /
 * CANCELLED_REVOKED / FAILED_* lifecycle with the Ollama HTTP call strictly
 * outside any DB transaction (each persistence step is its own short tx via
 * {@link AiJobStore}). Returns the fresh job row. Never mutates Task /
 * Workflow rows (AI-03 holds: only accept() writes, via TaskService).
 */
@Service
public class AiJobRunner {
  private final AiJobStore store;
  private final AiJobRepository jobs;
  private final AiContextManifestRepository manifests;
  private final ProjectApi projectApi;
  private final OllamaClient ollama;
  private final ObjectMapper om;
  private final Clock clock;

  public AiJobRunner(AiJobStore store, AiJobRepository jobs,
      AiContextManifestRepository manifests, ProjectApi projectApi, OllamaClient ollama,
      ObjectMapper om, Clock clock) {
    this.store = store; this.jobs = jobs; this.manifests = manifests;
    this.projectApi = projectApi; this.ollama = ollama; this.om = om; this.clock = clock;
  }

  /**
   * Execute one AI job. Idempotent re-drive: a non-QUEUED job is returned
   * as-is (fresh row), never re-executed.
   */
  public vn.smarttask.ai.entity.AiJob execute(UUID jobId, UUID actor) {
    AiJobStore.Snapshot s = store.snapshot(jobId);
    if (!actor.equals(s.principalUserId())) throw AiErrors.notFound("AI job not found");
    if (!AiJobStatus.QUEUED.name().equals(s.status())) return fresh(jobId, actor);

    store.moveTo(jobId, AiJobStatus.CONTEXT_BUILDING);
    // Reauthorize #1 (before build): active user + still in scope.
    if (!projectApi.isActiveUser(actor)
        || (s.projectId() != null && !projectApi.hasParticipation(s.projectId(), actor))) {
      store.cancelRevoked(jobId, actor, "REAUTH_BEFORE_BUILD");
      throw AiErrors.notFound("AI job revoked");
    }
    // Re-validate manifest at build time (pre-enqueue check may be stale).
    validateManifest(AiFeature.valueOf(s.feature()), s.projectId(), actor);

    store.moveTo(jobId, AiJobStatus.RUNNING);
    String prompt = PromptSanitizer.buildPrompt(AiFeature.valueOf(s.feature()),
        s.inputHint(), List.of());
    store.saveInteraction(jobId);

    // ---- NO DB TRANSACTION HELD across this call ----
    String raw;
    try {
      raw = ollama.generate(prompt, s.timeoutMs());
    } catch (OllamaException e) {
      if (e.getKind() == OllamaException.Kind.TIMEOUT) {
        store.fail(jobId, actor, AiJobStatus.FAILED_TIMEOUT, "OLLAMA_TIMEOUT");
      } else {
        store.fail(jobId, actor, AiJobStatus.FAILED_OLLAMA, "OLLAMA_DOWN");
      }
      return fresh(jobId, actor);
    }

    store.moveTo(jobId, AiJobStatus.VALIDATING);
    OutputValidator.Draft draft;
    try {
      draft = OutputValidator.validate(raw, s.projectId(), projectApi, om);
    } catch (AppException e) {
      store.fail(jobId, actor, AiJobStatus.FAILED_VALIDATION, "OUTPUT_SCHEMA");
      return fresh(jobId, actor);
    }
    store.saveSuggestion(jobId, raw);

    store.moveTo(jobId, AiJobStatus.REAUTHORIZING);
    // Reauthorize #2 (before delivery): lost permission -> revoke + redact, no retry.
    if (!projectApi.isActiveUser(actor)
        || (s.projectId() != null && !projectApi.hasParticipation(s.projectId(), actor))) {
      store.redactAndCancel(jobId, actor, "REAUTH_BEFORE_DELIVERY");
      throw AiErrors.notFound("AI job revoked");
    }

    store.complete(jobId, actor, draft.title());
    return fresh(jobId, actor);
  }

  private vn.smarttask.ai.entity.AiJob fresh(UUID jobId, UUID actor) {
    var job = jobs.findById(jobId)
        .orElseThrow(() -> AppException.notFound("AI job not found"));
    if (!actor.equals(job.getPrincipalUserId())) throw AiErrors.notFound("AI job not found");
    return job;
  }

  /** Retry gate for the service/JobRunner path (own short tx inside the store). */
  public AiJobStore.Snapshot prepareRetry(UUID actor, UUID jobId) {
    return store.enqueueRetry(jobId, actor);
  }

  /** Fresh owned row for the JobRunner path. */
  public vn.smarttask.ai.entity.AiJob loadOwned(UUID jobId, UUID actor) {
    return fresh(jobId, actor);
  }

  /** Worker-internal load (ownership is enforced inside {@link #execute}). */
  public vn.smarttask.ai.entity.AiJob loadAny(UUID jobId) {
    return jobs.findById(jobId)
        .orElseThrow(() -> AppException.notFound("AI job not found"));
  }

  private void validateManifest(AiFeature feature, UUID projectId, UUID userId) {
    var manifest = manifests.findById(feature.name()).orElse(null);
    if (manifest == null) {
      throw AiErrors.contextDenied("No AI context manifest for " + feature);
    }
    ContextManifestValidator.validate(
        new ContextManifestValidator.ManifestData(manifest.isEnabled(),
            parseList(manifest.getAllowedFields()), parseList(manifest.getForbiddenPatterns())),
        List.of(), projectId, userId, projectApi);
  }

  private List<String> parseList(String json) {
    try {
      List<String> v = om.readValue(json == null ? "[]" : json,
          new TypeReference<List<String>>() {});
      return v == null ? List.of() : v;
    } catch (Exception e) {
      return null; // unparsable manifest -> validator denies (secure default)
    }
  }
}
