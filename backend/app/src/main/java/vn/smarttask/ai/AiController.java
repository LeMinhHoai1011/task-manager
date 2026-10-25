package vn.smarttask.ai;

import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.smarttask.ai.service.AiService;
import vn.smarttask.common.ApiResult;
import vn.smarttask.common.AppException;
import vn.smarttask.identity.service.IdentityService;
import vn.smarttask.security.CurrentActor;
import vn.smarttask.security.GrantService;
import vn.smarttask.security.RequiresPermission;

/**
 * Minimal AI panel API (draft + explicit confirm). AI never auto-mutates tasks:
 * writes happen only via accept(), which delegates to the standard TaskService
 * use case with actor=user (AI-03). Other users' jobs (even SA) read as 404 (AI-02).
 *
 * F0: actor is always the authenticated principal. Admin/config checks resolve
 * via {@link GrantService} (deny 403 without a real grant); re-auth is a
 * consumed ticket id bound to the principal ({@code X-Reauth-Ticket}).
 * Client trust headers ({@code X-User-Id}/{@code X-Is-Admin}/{@code X-Has-Grant}/
 * {@code X-Reauth} boolean) are not accepted.
 */
@RestController
@RequestMapping("/api/v1/ai")
public class AiController {
  private final AiService ai;
  private final IdentityService identity;
  private final GrantService grants;

  public AiController(AiService ai, IdentityService identity, GrantService grants) {
    this.ai = ai;
    this.identity = identity;
    this.grants = grants;
  }

  public record AiRequest(String feature, UUID projectId, String inputHint,
      List<String> requestedFields, String idempotencyKey) {}

  public record JobDto(UUID id, String feature, String status, int attempt, int maxAttempts) {}

  public record SuggestionDto(UUID jobId, String status, String payload) {}

  static JobDto toDto(vn.smarttask.ai.entity.AiJob j) {
    return new JobDto(j.getId(), j.getFeature(), j.getStatus(), j.getAttempt(), j.getMaxAttempts());
  }

  @PostMapping("/request")
  public ResponseEntity<ApiResult<JobDto>> request(@RequestBody AiRequest req) {
    UUID userId = CurrentActor.userId();
    var job = ai.request(userId, AiFeature.valueOf(req.feature()), req.projectId(),
        req.inputHint(), req.requestedFields(), req.idempotencyKey());
    return ResponseEntity.ok(ApiResult.ok(toDto(job), "ai.request.success"));
  }

  @GetMapping("/jobs/{id}")
  public ResponseEntity<ApiResult<JobDto>> status(@PathVariable UUID id) {
    UUID userId = CurrentActor.userId();
    return ResponseEntity.ok(ApiResult.ok(toDto(ai.status(userId, id)), "ai.status.success"));
  }

  @GetMapping("/jobs/{id}/suggestion")
  public ResponseEntity<ApiResult<SuggestionDto>> suggestion(@PathVariable UUID id) {
    UUID userId = CurrentActor.userId();
    var s = ai.suggestionOf(userId, id);
    return ResponseEntity.ok(ApiResult.ok(
        new SuggestionDto(s.getJobId(), s.getStatus(), s.getPayload()), "ai.suggestion.success"));
  }

  @PostMapping("/jobs/{id}/accept")
  public ResponseEntity<ApiResult<MapId>> accept(@PathVariable UUID id) {
    UUID userId = CurrentActor.userId();
    var task = ai.accept(userId, id);
    return ResponseEntity.ok(ApiResult.ok(new MapId(task.getId()), "ai.accept.success"));
  }

  public record MapId(UUID id) {}

  @PostMapping("/jobs/{id}/reject")
  public ResponseEntity<ApiResult<SuggestionDto>> reject(@PathVariable UUID id) {
    UUID userId = CurrentActor.userId();
    var s = ai.reject(userId, id);
    return ResponseEntity.ok(ApiResult.ok(
        new SuggestionDto(s.getJobId(), s.getStatus(), s.getPayload()), "ai.reject.success"));
  }

  @PostMapping("/jobs/{id}/retry")
  public ResponseEntity<ApiResult<JobDto>> retry(@PathVariable UUID id) {
    UUID userId = CurrentActor.userId();
    return ResponseEntity.ok(ApiResult.ok(toDto(ai.retry(userId, id)), "ai.retry.success"));
  }

  @GetMapping("/history")
  public ResponseEntity<ApiResult<List<JobDto>>> history() {
    UUID userId = CurrentActor.userId();
    return ResponseEntity.ok(ApiResult.ok(
        ai.history(userId).stream().map(AiController::toDto).toList(), "ai.history.success"));
  }

  @GetMapping("/config")
  @RequiresPermission("OLLAMA_CONFIG_MANAGE")
  public ResponseEntity<ApiResult<String>> config(@RequestParam String key) {
    UUID userId = CurrentActor.userId();
    grants.requireGrant(userId, "ai.config", "ai_model_configuration", null,
        "OLLAMA_CONFIG_MANAGE");
    boolean admin = grants.isSystemAdmin(userId);
    return ResponseEntity.ok(ApiResult.ok(
        ai.getConfig(userId, key, admin, true), "ai.config.success"));
  }

  @PutMapping("/config")
  @RequiresPermission("OLLAMA_CONFIG_MANAGE")
  public ResponseEntity<ApiResult<Void>> updateConfig(
      @RequestParam String key,
      @RequestBody String value,
      @RequestHeader(value = "X-Reauth-Ticket", required = false) UUID reauthTicketId) {
    UUID userId = CurrentActor.userId();
    grants.requireGrant(userId, "ai.config.update", "ai_model_configuration", null,
        "OLLAMA_CONFIG_MANAGE");
    if (reauthTicketId == null) {
      throw AppException.reauthRequired("Reauth required");
    }
    identity.consumeReauthTicket(reauthTicketId, userId, "ai.config.update", null);
    ai.updateConfig(userId, key, value, grants.isSystemAdmin(userId), true, true);
    return ResponseEntity.ok(ApiResult.ok(null, "ai.config.update.success"));
  }
}
