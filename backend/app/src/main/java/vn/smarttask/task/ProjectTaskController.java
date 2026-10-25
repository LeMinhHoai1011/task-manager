package vn.smarttask.task;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.smarttask.common.ApiResult;
import vn.smarttask.common.PageResponse;
import vn.smarttask.security.CurrentActor;
import vn.smarttask.security.PolicyEnforcer;
import vn.smarttask.security.RequiresPermission;
import vn.smarttask.task.entity.Task;

/**
 * F4 project-scoped task list (docs EP-089, additive).
 *
 * <p>Same filter/sort/cursor contract as {@code GET /api/v1/tasks} (single
 * {@code project_id} scope instead of the visibility union): project READ
 * gate first (404 when the actor must not know the project exists), then the
 * shared {@link TaskSort} allowlist ({@code sort=-field,field2} plus legacy
 * {@code sort+order}; unknown fields -&gt; 400 UNSUPPORTED_SORT_FIELD) and
 * {@link TaskCursor} keyset pagination. The legacy list endpoint is
 * untouched.
 */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectTaskController {

  private final PolicyEnforcer policy;
  private final vn.smarttask.task.repo.TaskRepository tasks;
  private final vn.smarttask.task.repo.TaskAssignmentRepository assignments;
  private final vn.smarttask.task.repo.TaskTagRepository tags;

  public ProjectTaskController(PolicyEnforcer policy,
      vn.smarttask.task.repo.TaskRepository tasks,
      vn.smarttask.task.repo.TaskAssignmentRepository assignments,
      vn.smarttask.task.repo.TaskTagRepository tags) {
    this.policy = policy;
    this.tasks = tasks;
    this.assignments = assignments;
    this.tags = tags;
  }

  public record TaskDto(@JsonProperty("id") UUID id,
      @JsonProperty("project_id") UUID projectId,
      @JsonProperty("title") String title,
      @JsonProperty("state") String state,
      @JsonProperty("blocked_flag") boolean blockedFlag,
      @JsonProperty("version") long version,
      @JsonProperty("updated_at") OffsetDateTime updatedAt) {}

  private static TaskDto dto(Task t) {
    return new TaskDto(t.getId(), t.getProjectId(), t.getTitle(), t.getState(),
        t.isBlockedFlag(), t.getVersion(), t.getUpdatedAt());
  }

  @GetMapping("/{id}/tasks")
  @RequiresPermission("task:read")
  public ResponseEntity<ApiResult<PageResponse<TaskDto>>> tasks(
      @PathVariable UUID id,
      @RequestParam(required = false) String state,
      @RequestParam(required = false, name = "assignee_id") UUID assigneeId,
      @RequestParam(required = false) String tag,
      @RequestParam(required = false) Boolean blocked,
      @RequestParam(required = false) Integer depth,
      @RequestParam(required = false) Boolean overdue,
      @RequestParam(required = false, defaultValue = "updated_at") String sort,
      @RequestParam(required = false, defaultValue = "desc") String order,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false, defaultValue = "20") int limit) {
    UUID actor = CurrentActor.userId();
    policy.requireProjectRead(actor, id);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    List<Task> filtered = new ArrayList<>();
    for (Task t : tasks.findByProjectId(id)) {
      if (t.getDeletedAt() != null) continue;
      if (state != null && !state.equalsIgnoreCase(t.getState())) continue;
      if (blocked != null && blocked != t.isBlockedFlag()) continue;
      if (depth != null && depth != t.getDepth()) continue;
      if (assigneeId != null && !assignments.existsByTaskIdAndUserId(t.getId(), assigneeId)) {
        continue;
      }
      if (tag != null && !tags.existsByTaskIdAndTag(t.getId(), tag)) continue;
      if (overdue != null) {
        boolean isOverdue = t.getDueAt() != null && t.getDueAt().isBefore(now)
            && !"COMPLETED".equals(t.getState()) && !"CANCELLED".equals(t.getState());
        if (overdue != isOverdue) continue;
      }
      filtered.add(t);
    }
    filtered.sort(TaskSort.comparator(sort, order));
    String cursorSortKey = sort != null && (sort.contains(",") || sort.startsWith("-")
        || sort.startsWith("+")) ? "updated_at" : sort;
    String cursorOrder = TaskSort.primaryOrder(sort, order);
    int pageSize = Math.min(Math.max(limit, 1), 100);
    int start = TaskCursor.resolveStart(filtered, cursor, cursorSortKey, cursorOrder);
    if (start > filtered.size()) start = filtered.size();
    int end = Math.min(start + pageSize, filtered.size());
    var page = filtered.subList(start, end).stream().map(ProjectTaskController::dto).toList();
    String next = end < filtered.size()
        ? TaskCursor.nextCursor(filtered.get(end - 1), cursorSortKey, cursorOrder)
        : null;
    Map<String, Object> filters = new LinkedHashMap<>();
    filters.put("project_id", id.toString());
    if (state != null) filters.put("state", state);
    if (assigneeId != null) filters.put("assignee_id", assigneeId.toString());
    if (tag != null) filters.put("tag", tag);
    if (blocked != null) filters.put("blocked", blocked);
    if (depth != null) filters.put("depth", depth);
    if (overdue != null) filters.put("overdue", overdue);
    filters.put("sort", sort == null ? "updated_at" : sort);
    return ResponseEntity.ok(ApiResult.ok(PageResponse.ofPage(page, next, next != null,
        pageSize, (long) filtered.size(), filters, null),
        "task.list.success"));
  }
}
