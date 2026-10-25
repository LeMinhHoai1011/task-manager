package vn.smarttask.workspace;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.smarttask.common.ApiResult;
import vn.smarttask.common.PageResponse;
import vn.smarttask.project.service.ProjectService;
import vn.smarttask.security.CurrentActor;
import vn.smarttask.security.PolicyEnforcer;
import vn.smarttask.security.RequiresPermission;

/**
 * F4 workspace-scoped project list (docs EP-062, additive).
 *
 * <p>Delegates to the same {@code listForUser(actor, workspaceId)} read path
 * as {@code GET /projects?workspace_id=}; the workspace READ gate
 * ({@link PolicyEnforcer#requireWorkspaceRead} — 404 when the actor must not
 * know the workspace exists) runs before any data is touched. The legacy
 * endpoints are untouched.
 */
@RestController
@RequestMapping("/api/v1/workspaces")
public class WorkspaceProjectController {

  private final ProjectService projects;
  private final PolicyEnforcer policy;

  public WorkspaceProjectController(ProjectService projects, PolicyEnforcer policy) {
    this.projects = projects;
    this.policy = policy;
  }

  public record ProjectDto(@JsonProperty("id") UUID id,
      @JsonProperty("workspace_id") UUID workspaceId,
      @JsonProperty("name") String name,
      @JsonProperty("status") String status,
      @JsonProperty("created_at") OffsetDateTime createdAt) {}

  @GetMapping("/{id}/projects")
  @RequiresPermission("project:read")
  public ResponseEntity<ApiResult<PageResponse<ProjectDto>>> projects(
      @PathVariable UUID id,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false, defaultValue = "20") int limit) {
    UUID actor = CurrentActor.userId();
    policy.requireWorkspaceRead(actor, id);
    int pageSize = Math.min(Math.max(limit, 1), 100);
    var all = projects.listForUser(actor, id);
    int start = 0;
    if (cursor != null && !cursor.isBlank()) {
      for (int i = 0; i < all.size(); i++) {
        if (all.get(i).getId().toString().equals(cursor)) { start = i + 1; break; }
      }
    }
    int end = Math.min(start + pageSize, all.size());
    List<ProjectDto> page = all.subList(start, end).stream()
        .map(p -> new ProjectDto(p.getId(), p.getWorkspaceId(), p.getName(), p.getStatus(),
            null))
        .toList();
    String next = end < all.size() ? all.get(end - 1).getId().toString() : null;
    return ResponseEntity.ok(ApiResult.ok(PageResponse.ofPage(page, next, next != null,
        pageSize, (long) all.size(), null, null),
        "project.list.success"));
  }
}
