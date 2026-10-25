package vn.smarttask.workspace;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.smarttask.common.ApiResult;
import vn.smarttask.common.AppException;
import vn.smarttask.common.PageResponse;
import vn.smarttask.identity.service.IdentityService;
import vn.smarttask.security.CurrentActor;
import vn.smarttask.security.PolicyEnforcer;
import vn.smarttask.security.RequiresPermission;
import vn.smarttask.workspace.service.WorkspaceService;

@RestController
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {
  private final WorkspaceService workspaces;
  private final IdentityService identity;
  private final PolicyEnforcer policy;

  public WorkspaceController(WorkspaceService workspaces, IdentityService identity,
      PolicyEnforcer policy) {
    this.workspaces = workspaces;
    this.identity = identity;
    this.policy = policy;
  }

  public record WorkspaceDto(@JsonProperty("id") UUID id,
      @JsonProperty("name") String name,
      @JsonProperty("type") String type,
      @JsonProperty("status") String status,
      @JsonProperty("created_at") OffsetDateTime createdAt) {}
  public record CreateBody(@JsonProperty("name") @NotBlank String name,
      @JsonProperty("type") String type) {}

  private static WorkspaceDto toDto(vn.smarttask.workspace.entity.Workspace w) {
    return new WorkspaceDto(w.getId(), w.getName(), w.getType(), w.getStatus(), w.getCreatedAt());
  }

  @GetMapping
  @RequiresPermission("workspace:read")
  public ResponseEntity<ApiResult<PageResponse<WorkspaceDto>>> list(
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false, defaultValue = "20") int limit) {
    UUID actor = CurrentActor.userId();
    int pageSize = Math.min(Math.max(limit, 1), 100);
    var all = workspaces.listForUser(actor);
    int start = 0;
    if (cursor != null && !cursor.isBlank()) {
      for (int i = 0; i < all.size(); i++) {
        if (all.get(i).getId().toString().equals(cursor)) { start = i + 1; break; }
      }
    }
    int end = Math.min(start + pageSize, all.size());
    var page = all.subList(start, end).stream().map(WorkspaceController::toDto).toList();
    String next = end < all.size() ? all.get(end - 1).getId().toString() : null;
    return ResponseEntity.ok(ApiResult.ok(PageResponse.ofPage(page, next, next != null,
        pageSize, (long) all.size(), null, null),
        "workspace.list.success"));
  }

  @PostMapping
  @RequiresPermission("workspace:write")
  public ResponseEntity<ApiResult<WorkspaceDto>> create(@RequestBody Map<String, Object> body) {
    UUID actor = CurrentActor.userId();
    String name = String.valueOf(body.getOrDefault("name", "ws"));
    String type = String.valueOf(body.getOrDefault("type", "TEAM"));
    var w = "PERSONAL".equals(type)
        ? workspaces.createPersonal(actor)
        : workspaces.createTeam(actor, name);
    return ResponseEntity.ok(ApiResult.ok(toDto(w), "workspace.create.success"));
  }

  private static long ifMatch(String v) {
    if (v == null) throw AppException.ifMatchRequired();
    try {
      return Long.parseLong(v.replace("W/", "").replace("\"", "").trim());
    } catch (NumberFormatException e) {
      throw AppException.badRequest("IF_MATCH_INVALID", "If-Match invalid");
    }
  }

  @GetMapping("/{id}")
  @RequiresPermission("workspace:read")
  public ResponseEntity<ApiResult<WorkspaceDto>> get(@PathVariable UUID id) {
    UUID actor = CurrentActor.userId();
    policy.requireWorkspaceRead(actor, id);
    return ResponseEntity.ok(ApiResult.ok(toDto(workspaces.get(actor, id)),
        "workspace.get.success"));
  }

  @PatchMapping("/{id}")
  @RequiresPermission("workspace:write")
  public ResponseEntity<ApiResult<WorkspaceDto>> update(@PathVariable UUID id,
      @RequestBody Map<String, Object> body,
      @RequestHeader(value = "If-Match", required = false) String ifMatch) {
    UUID actor = CurrentActor.userId();
    policy.requireWorkspaceWrite(actor, id);
    var w = workspaces.update(actor, id,
        body.get("name") == null ? null : String.valueOf(body.get("name")), ifMatch(ifMatch));
    return ResponseEntity.ok(ApiResult.ok(toDto(w), "workspace.update.success"));
  }

  @DeleteMapping("/{id}")
  @RequiresPermission("workspace:write")
  public ResponseEntity<ApiResult<Object>> delete(@PathVariable UUID id,
      @RequestHeader(value = "If-Match", required = false) String ifMatch) {
    UUID actor = CurrentActor.userId();
    policy.requireWorkspaceWrite(actor, id);
    workspaces.delete(actor, id, ifMatch(ifMatch));
    return ResponseEntity.ok(ApiResult.ok(null, "workspace.delete.success"));
  }

  @PostMapping("/{id}/invites")
  @RequiresPermission("workspace:write")
  public ResponseEntity<ApiResult<Object>> invite(
      @PathVariable UUID id, @RequestBody Map<String, Object> body) {
    UUID actor = CurrentActor.userId();
    policy.requireWorkspaceWrite(actor, id);
    var inv = workspaces.invite(actor, id,
        String.valueOf(body.get("email")), String.valueOf(body.getOrDefault("role", "MEMBER")), 7);
    return ResponseEntity.ok(ApiResult.ok(Map.of("invitation_id", inv.getId()),
        "workspace.invite.success"));
  }

  @PostMapping("/{id}/transfer")
  @RequiresPermission("workspace:write")
  public ResponseEntity<ApiResult<Object>> transfer(
      @PathVariable UUID id, @RequestBody Map<String, Object> body) {
    UUID actor = CurrentActor.userId();
    policy.requireWorkspaceWrite(actor, id);
    UUID target = UUID.fromString(String.valueOf(body.get("target_user_id")));
    boolean emergency = Boolean.parseBoolean(String.valueOf(body.getOrDefault("emergency", "false")));
    String reason = body.get("reason") == null ? null : String.valueOf(body.get("reason"));
    String ticket = body.get("reauth_ticket_id") == null ? null
        : String.valueOf(body.get("reauth_ticket_id"));
    // Reauth ticket is consumed bound to the principal (userId=principal), never a header id.
    workspaces.ownerTransfer(actor, id, target, emergency, reason,
        ticket == null ? null : UUID.fromString(ticket), identity, "workspace.owner.transfer");
    return ResponseEntity.ok(ApiResult.ok(null, "workspace.transfer.success"));
  }
}
