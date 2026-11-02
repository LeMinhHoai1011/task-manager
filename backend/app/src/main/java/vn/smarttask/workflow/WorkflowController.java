package vn.smarttask.workflow;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.smarttask.common.ApiResult;
import vn.smarttask.common.AppException;
import vn.smarttask.security.CurrentActor;
import vn.smarttask.security.PolicyEnforcer;
import vn.smarttask.security.RequiresPermission;
import vn.smarttask.task.entity.Task;
import vn.smarttask.workflow.service.WorkflowService;

/** PH-2 WorkflowController: start/submit/approve/reject/cancel/reopen. Idempotency-Key + If-Match required. Errors -> ErrorEnvelope via ApiExceptionHandler. */
@RestController
@RequestMapping("/api/v1/tasks/{id}/workflow")
public class WorkflowController {

  public record TransitionResult(
      @JsonProperty("task_id") UUID taskId,
      @JsonProperty("from_state") String fromState,
      @JsonProperty("to_state") String toState,
      @JsonProperty("transition_id") String transitionId,
      @JsonProperty("version") long version,
      @JsonProperty("at") OffsetDateTime at) {}

  private final WorkflowService service;
  private final PolicyEnforcer policy;
  private final vn.smarttask.task.repo.TaskRepository tasks;

  public WorkflowController(WorkflowService service, PolicyEnforcer policy,
      vn.smarttask.task.repo.TaskRepository tasks) {
    this.service = service;
    this.policy = policy;
    this.tasks = tasks;
  }

  private static long ifMatch(String v) {
    if (v == null) throw AppException.ifMatchRequired();
    try {
      return Long.parseLong(v.replace("W/", "").replace("\"", "").trim());
    } catch (NumberFormatException e) {
      throw AppException.badRequest("IF_MATCH_INVALID", "If-Match invalid");
    }
  }

  private static void requireIdem(String k) {
    if (k == null || k.isBlank()) throw AppException.badRequest("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key required");
  }

  private TransitionResult res(Task t, String from, String tid) {
    return new TransitionResult(t.getId(), from, t.getState(), tid, t.getVersion(),
        OffsetDateTime.now(java.time.ZoneOffset.UTC));
  }

  /** F1: real pre-transition state (no hardcoded fromState). */
  private String fromStateOf(UUID id) {
    return tasks.findById(id).map(Task::getState).orElse("UNKNOWN");
  }

  @PostMapping("/start")
  @RequiresPermission("task:write")
  public ResponseEntity<ApiResult<TransitionResult>> start(@PathVariable UUID id,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    requireIdem(key);
    UUID actor = CurrentActor.userId();
    policy.requireTaskExecution(actor, id, "workflow.start");
    String from = fromStateOf(id);
    Task t = service.start(actor, id, ifMatch(ifMatch));
    return ResponseEntity.ok(ApiResult.ok(res(t, from, "T-01"), "workflow.start.success"));
  }

  @PostMapping("/submit")
  @RequiresPermission("task:write")
  public ResponseEntity<ApiResult<TransitionResult>> submit(@PathVariable UUID id,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    requireIdem(key);
    UUID actor = CurrentActor.userId();
    policy.requireTaskExecution(actor, id, "workflow.submit");
    String from = fromStateOf(id);
    var r = service.submit(actor, id, ifMatch(ifMatch));
    String tid = r.round() == null ? "T-03" : "T-02";
    return ResponseEntity.ok(ApiResult.ok(res(r.task(), from, tid), "workflow.submit.success"));
  }

  @PostMapping("/approve")
  @RequiresPermission("task:approve")
  public ResponseEntity<ApiResult<TransitionResult>> approve(@PathVariable UUID id,
      @RequestBody(required = false) Map<String, Object> body,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    requireIdem(key);
    UUID actor = CurrentActor.userId();
    policy.requireTaskWrite(actor, id, "workflow.approve", "task:write");
    String from = fromStateOf(id);
    Task t = service.approve(actor, id, ifMatch(ifMatch));
    return ResponseEntity.ok(ApiResult.ok(res(t, from, "T-04"), "workflow.approve.success"));
  }

  @PostMapping("/reject")
  @RequiresPermission("task:approve")
  public ResponseEntity<ApiResult<TransitionResult>> reject(@PathVariable UUID id,
      @RequestBody(required = false) Map<String, Object> body,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    requireIdem(key);
    UUID actor = CurrentActor.userId();
    policy.requireTaskWrite(actor, id, "workflow.reject", "task:write");
    String reason = body == null ? null : (String) body.get("reason");
    String from = fromStateOf(id);
    Task t = service.reject(actor, id, reason, ifMatch(ifMatch));
    return ResponseEntity.ok(ApiResult.ok(res(t, from, "T-05"), "workflow.reject.success"));
  }

  @PostMapping("/cancel")
  @RequiresPermission("task:write")
  public ResponseEntity<ApiResult<TransitionResult>> cancel(@PathVariable UUID id,
      @RequestBody(required = false) Map<String, Object> body,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    requireIdem(key);
    UUID actor = CurrentActor.userId();
    policy.requireTaskWrite(actor, id, "workflow.cancel", "task:write");
    String reason = body == null ? null : (String) body.get("reason");
    String from = fromStateOf(id);
    Task t = service.cancel(actor, id, reason, ifMatch(ifMatch));
    return ResponseEntity.ok(ApiResult.ok(res(t, from, "T-06"), "workflow.cancel.success"));
  }

  @PostMapping("/reopen")
  @RequiresPermission("task:write")
  public ResponseEntity<ApiResult<TransitionResult>> reopen(@PathVariable UUID id,
      @RequestBody(required = false) Map<String, Object> body,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    requireIdem(key);
    UUID actor = CurrentActor.userId();
    policy.requireTaskWrite(actor, id, "workflow.reopen", "task:write");
    String reason = body == null ? null : (String) body.get("reason");
    String kind = body == null ? "completed" : String.valueOf(body.getOrDefault("kind", "completed"));
    String from = fromStateOf(id);
    Task t = "cancelled".equals(kind)
        ? service.reopenCancelled(actor, id, reason, ifMatch(ifMatch))
        : service.reopenCompleted(actor, id, reason, ifMatch(ifMatch));
    return ResponseEntity.ok(ApiResult.ok(res(t, from, "T-08"), "workflow.reopen.success"));
  }
}
