package vn.smarttask.collab;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.smarttask.common.ApiResult;
import vn.smarttask.common.AppException;
import vn.smarttask.common.PageResponse;
import vn.smarttask.security.CurrentActor;
import vn.smarttask.security.PolicyEnforcer;
import vn.smarttask.security.RequiresPermission;
import vn.smarttask.collab.service.ActivityService;
import vn.smarttask.collab.service.CommentService;

@RestController
@RequestMapping("/api/v1")
public class CollabController {

  private final CommentService comments;
  private final ActivityService activity;
  private final PolicyEnforcer policy;

  public CollabController(CommentService comments, ActivityService activity,
      PolicyEnforcer policy) {
    this.comments = comments; this.activity = activity; this.policy = policy;
  }

  @GetMapping("/tasks/{id}/comments")
  @RequiresPermission("comment:read")
  public ResponseEntity<ApiResult<PageResponse<Object>>> comments(@PathVariable UUID id,
      @RequestParam(required = false) String cursor) {
    UUID actor = CurrentActor.userId();
    policy.requireTaskRead(actor, id);
    // Viewer access checked by policy layer above; redaction applied when revoked.
    var list = comments.list(id, true).stream().map(c -> (Object) Map.of(
        "id", c.getId().toString(), "body", c.getBody(),
        "author_user_id", c.getAuthorUserId().toString())).toList();
    return ResponseEntity.ok(ApiResult.ok(PageResponse.ofPage(list, null, false,
        list.size(), (long) list.size(), null, null),
        "comment.list.success"));
  }

  @PostMapping("/tasks/{id}/comments")
  @RequiresPermission("comment:write")
  public ResponseEntity<ApiResult<Object>> addComment(@PathVariable UUID id,
      @RequestBody Map<String, Object> body,
      @RequestHeader(value = "Idempotency-Key", required = false) String idem) {
    if (idem == null || idem.isBlank()) throw AppException.badRequest("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key required");
    UUID actor = CurrentActor.userId();
    policy.requireTaskExecution(actor, id, "comment.create");
    var c = comments.add(actor, id, (String) body.get("body"),
        body.get("parent_comment_id") == null ? null : UUID.fromString((String) body.get("parent_comment_id")));
    activity.append(id, null, actor, "COMMENT_ADDED", "{\"comment_id\":\"" + c.getId() + "\"}");
    return ResponseEntity.ok(ApiResult.ok(Map.of("id", c.getId().toString()),
        "comment.create.success"));
  }

  @GetMapping("/tasks/{id}/activity")
  @RequiresPermission("task:read")
  public ResponseEntity<ApiResult<PageResponse<Object>>> activity(@PathVariable UUID id,
      @RequestParam(required = false) String cursor) {
    UUID actor = CurrentActor.userId();
    policy.requireTaskRead(actor, id);
    List<Object> items = activity.timeline(id, true).stream()
        .map(e -> (Object) Map.of("id", e.id().toString(), "kind", e.kind(),
            "payload", e.payloadJson(), "redacted", e.redacted())).toList();
    return ResponseEntity.ok(ApiResult.ok(PageResponse.ofPage(items, null, false,
        items.size(), (long) items.size(), null, null),
        "activity.list.success"));
  }
}
