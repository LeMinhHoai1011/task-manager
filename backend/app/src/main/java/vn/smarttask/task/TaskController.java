package vn.smarttask.task;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
import vn.smarttask.task.entity.Task;
import vn.smarttask.task.service.TaskService;

/** PH-2 TaskController: delegates to TaskService. Mutations require Idempotency-Key; writes require If-Match. */
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {
  @org.springframework.beans.factory.annotation.Autowired(required=false) private vn.smarttask.task.service.TaskCreationService creation;

  public record TaskDto(@JsonProperty("id") UUID id,
      @JsonProperty("project_id") UUID projectId,
      @JsonProperty("title") String title,
      @JsonProperty("state") String state,
      @JsonProperty("blocked_flag") boolean blockedFlag,
      @JsonProperty("version") long version,
      @JsonProperty("updated_at") OffsetDateTime updatedAt,
      @JsonProperty("priority") String priority,
      @JsonProperty("due_at") OffsetDateTime dueAt,
      @JsonProperty("description") String description) {}

  private final TaskService service;
  private final PolicyEnforcer policy;
  private final vn.smarttask.task.repo.TaskRepository tasks;
  private final vn.smarttask.task.repo.ChecklistItemRepository checklist;
  private final vn.smarttask.task.repo.TimeLogRepository timelogs;
  private final vn.smarttask.task.repo.TaskAssignmentRepository assignments;
  private final vn.smarttask.task.repo.TaskTagRepository tags;
  private final vn.smarttask.project.repo.ProjectMembershipRepository projectMembers;
  private final vn.smarttask.project.repo.ProjectRepository projects;

  public TaskController(TaskService service, PolicyEnforcer policy,
      vn.smarttask.task.repo.TaskRepository tasks,
      vn.smarttask.task.repo.ChecklistItemRepository checklist,
      vn.smarttask.task.repo.TimeLogRepository timelogs,
      vn.smarttask.task.repo.TaskAssignmentRepository assignments,
      vn.smarttask.task.repo.TaskTagRepository tags,
      vn.smarttask.project.repo.ProjectMembershipRepository projectMembers,
      vn.smarttask.project.repo.ProjectRepository projects) {
    this.service = service;
    this.policy = policy;
    this.tasks = tasks;
    this.checklist = checklist;
    this.timelogs = timelogs;
    this.assignments = assignments;
    this.tags = tags;
    this.projectMembers = projectMembers;
    this.projects = projects;
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

  private static TaskDto dto(Task t) {
    return new TaskDto(t.getId(), t.getProjectId(), t.getTitle(), t.getState(),
        t.isBlockedFlag(), t.getVersion(), t.getUpdatedAt(),t.getPriority(),t.getDueAt(),t.getDescription());
  }

  /** Java-call compatibility for existing controller consumers. */
  public ResponseEntity<ApiResult<PageResponse<TaskDto>>> list(UUID projectId,UUID alias,String state,UUID assignee,String tag,Boolean blocked,Integer depth,Boolean overdue,String sort,String order,String cursor,int limit) {
    return list(projectId,alias,state,null,assignee,tag,blocked,depth,overdue,sort,order,cursor,limit);
  }
  @GetMapping
  @RequiresPermission("task:read")
  public ResponseEntity<ApiResult<PageResponse<TaskDto>>> list(
      @RequestParam(required = false) UUID projectId,
      @RequestParam(required = false, name = "project_id") UUID projectIdAlias,
      @RequestParam(required = false) String state,
      @RequestParam(required = false) String priority,
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
    UUID projectFilter = projectId != null ? projectId : projectIdAlias;
    if (projectFilter != null) {
      policy.requireProjectRead(actor, projectFilter);
    }
    // Determine whether actor is a plain MEMBER/GUEST (not manager/owner/admin) of the project.
    // MEMBER/GUEST may only see tasks assigned to them.
    vn.smarttask.project.entity.ProjectMembership actorMembership = null;
    if (projectFilter != null) {
      actorMembership = projectMembers.findMembership(projectFilter, actor).orElse(null);
    }
    boolean actorIsMember = actorMembership != null
        && !"OWNER".equals(actorMembership.getRole())
        && !"ADMIN".equals(actorMembership.getRole())
        && !"MANAGER".equals(actorMembership.getRole());
    java.util.List<Task> candidates = new java.util.ArrayList<>();
    if (projectFilter != null) {
      candidates.addAll(tasks.findByProjectId(projectFilter));
    } else {
      // F2.5: visibility scope from indexed queries (never a full-table
      // scan of project_membership / task_assignment). Authorization is
      // unchanged: member projects + projects of assigned live tasks.
      java.util.Set<UUID> visibleProjects = new java.util.HashSet<>(
          projectMembers.findActiveProjectIdsByUser(actor));
      visibleProjects.addAll(tasks.findAssignedProjectIdsByUser(actor));
      if (!visibleProjects.isEmpty()) {
        java.util.Map<UUID, vn.smarttask.project.entity.Project> byId = new java.util.HashMap<>();
        for (var p : projects.findAllById(visibleProjects)) byId.put(p.getId(), p);
        for (UUID pid : visibleProjects) {
          var p = byId.get(pid);
          if (p == null || "DELETED".equals(p.getStatus())) continue;
          candidates.addAll(tasks.findByProjectId(pid));
        }
      }
    }
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    java.util.List<Task> filtered = new java.util.ArrayList<>();
    for (Task t : candidates) {
      if (t.getDeletedAt() != null) continue;
      if (state != null && !state.equalsIgnoreCase(t.getState())) continue;
      if (priority != null && !priority.equalsIgnoreCase(t.getPriority())) continue;
      if (blocked != null && blocked != t.isBlockedFlag()) continue;
      if (depth != null && depth != t.getDepth()) continue;
      // MEMBER/GUEST may only see tasks assigned to them.
      try {policy.requireTaskRead(actor,t.getId());}
      catch(AppException e){if(e.getHttpStatus().value()==403||e.getHttpStatus().value()==404)continue;throw e;}
      // F2.5: assignee/tag as EXISTS point queries (no entity load per row).
      if (assigneeId != null && !assignments.existsByTaskIdAndUserId(t.getId(), assigneeId)) continue;
      if (tag != null && !tags.existsByTaskIdAndTag(t.getId(), tag)) continue;
      if (overdue != null) {
        boolean isOverdue = t.getDueAt() != null && t.getDueAt().isBefore(now)
            && !"COMPLETED".equals(t.getState()) && !"CANCELLED".equals(t.getState());
        if (overdue != isOverdue) continue;
      }
      filtered.add(t);
    }
    // §7 unified sort: `sort=-field,field2` (docs) + legacy `sort+order`
    // fallback. Unknown fields -> 400 UNSUPPORTED_SORT_FIELD (no silent fallback).
    java.util.Comparator<Task> cmp = TaskSort.comparator(sort, order);
    filtered.sort(cmp);
    String cursorSortKey = sort != null && (sort.contains(",") || sort.startsWith("-")
        || sort.startsWith("+")) ? "updated_at" : sort;
    String cursorOrder = TaskSort.primaryOrder(sort, order);
    int pageSize = Math.min(Math.max(limit, 1), 100);
    // F2.5: seek cursor (keyset (sort,id) or legacy id). No deep offset.
    int start = TaskCursor.resolveStart(filtered, cursor, cursorSortKey, cursorOrder);
    if (start > filtered.size()) start = filtered.size();
    int end = Math.min(start + pageSize, filtered.size());
    var page = filtered.subList(start, end).stream().map(TaskController::dto).toList();
    String next = end < filtered.size()
        ? TaskCursor.nextCursor(filtered.get(end - 1), cursorSortKey, cursorOrder)
        : null;
    java.util.Map<String, Object> filters = new java.util.LinkedHashMap<>();
    if (projectFilter != null) filters.put("project_id", projectFilter.toString());
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

  @PostMapping
  @RequiresPermission("task:write")
  public ResponseEntity<ApiResult<TaskDto>> create(@RequestBody Map<String, Object> body,
      @RequestHeader(value = "Idempotency-Key", required = false) String idem) {
    requireIdem(idem);
    UUID actor = CurrentActor.userId();
    UUID projectId = UUID.fromString((String) body.get("project_id"));
    policy.requireProjectWrite(actor, projectId);
    Task t;
    if(creation==null)t=service.create(actor,projectId,(String)body.get("title"),(String)body.get("description"),(String)body.getOrDefault("priority","MEDIUM"),body.get("milestone_id")==null?null:UUID.fromString((String)body.get("milestone_id")));
    else {
      try {t=creation.create(actor,new vn.smarttask.task.service.TaskCreationService.Input(projectId,(String)body.get("title"),(String)body.get("description"),(String)body.getOrDefault("priority","MEDIUM"),body.get("milestone_id")==null?null:UUID.fromString((String)body.get("milestone_id")),body.get("due_date")==null?null:LocalDate.parse((String)body.get("due_date")),body.get("due_at")==null?null:OffsetDateTime.parse((String)body.get("due_at")),(String)body.get("due_timezone")),idem);}
      catch(java.time.DateTimeException e){throw AppException.badRequest("SCHEDULE_INVALID","Invalid deadline");}
    }
    return ResponseEntity.ok(ApiResult.ok(dto(t), "task.create.success"));
  }

  @GetMapping("/{id}")
  @RequiresPermission("task:read")
  public ResponseEntity<ApiResult<TaskDto>> get(@PathVariable UUID id) {
    UUID actor = CurrentActor.userId();
    policy.requireTaskRead(actor, id);
    Task t = tasks.findById(id).orElseThrow(() -> AppException.notFound("Task not found"));
    if (t.getDeletedAt() != null) throw AppException.notFound("Task not found");
    return ResponseEntity.ok(ApiResult.ok(dto(t), "task.get.success"));
  }

  @PatchMapping("/{id}")
  @RequiresPermission("task:write")
  public ResponseEntity<ApiResult<TaskDto>> update(@PathVariable UUID id,
      @RequestBody Map<String, Object> body,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      @RequestHeader(value = "Idempotency-Key", required = false) String idem) {
    requireIdem(idem);
    UUID actor = CurrentActor.userId();
    // Content update (title/description) requires MANAGER — MEMBER cannot change task content.
    policy.requireTaskManagerWrite(actor, id, "task.update", "task:write");
    Task t = service.updateContent(actor, id,
        (String) body.get("title"), (String) body.get("description"), ifMatch(ifMatch));
    return ResponseEntity.ok(ApiResult.ok(dto(t), "task.update.success"));
  }

  @PostMapping("/{id}/subtasks")
  @RequiresPermission("task:write")
  public ResponseEntity<ApiResult<TaskDto>> createSubtask(@PathVariable UUID id,
      @RequestBody Map<String, Object> body,
      @RequestHeader(value = "Idempotency-Key", required = false) String idem) {
    requireIdem(idem);
    UUID actor = CurrentActor.userId();
    policy.requireTaskWrite(actor, id);
    Task t = service.createSubtask(actor, id,
        (String) body.getOrDefault("title", "sub"), (String) body.get("description"));
    return ResponseEntity.ok(ApiResult.ok(dto(t), "task.subtask.create.success"));
  }

  @PostMapping("/{id}/assignments")
  @RequiresPermission("task:write")
  public ResponseEntity<ApiResult<Object>> assign(@PathVariable UUID id,
      @RequestBody Map<String, Object> body,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      @RequestHeader(value = "Idempotency-Key", required = false) String idem) {
    requireIdem(idem);
    UUID actor = CurrentActor.userId();
    // Assign requires MANAGER: MEMBER must not change who is assigned to a task.
    policy.requireTaskManagerWrite(actor, id, "task.assign", "task:write");
    service.assign(actor, id, UUID.fromString((String) body.get("user_id")), ifMatch(ifMatch));
    return ResponseEntity.ok(ApiResult.ok(null, "task.assign.success"));
  }

  @PostMapping("/{id}/dependencies")
  @RequiresPermission("task:write")
  public ResponseEntity<ApiResult<Object>> addDependency(@PathVariable UUID id,
      @RequestBody Map<String, Object> body,
      @RequestHeader(value = "Idempotency-Key", required = false) String idem) {
    requireIdem(idem);
    UUID actor = CurrentActor.userId();
    policy.requireTaskWrite(actor, id);
    service.addDependency(actor, id, UUID.fromString((String) body.get("depends_on_task_id")));
    return ResponseEntity.ok(ApiResult.ok(null, "task.dependency.add.success"));
  }

  @PostMapping("/{id}/checklist")
  @RequiresPermission("task:write")
  public ResponseEntity<ApiResult<Object>> addChecklist(@PathVariable UUID id,
      @RequestBody Map<String, Object> body,
      @RequestHeader(value = "Idempotency-Key", required = false) String idem) {
    requireIdem(idem);
    UUID actor = CurrentActor.userId();
    policy.requireTaskWrite(actor, id);
    service.addChecklist(actor, id, (String) body.get("title"));
    return ResponseEntity.ok(ApiResult.ok(null, "task.checklist.add.success"));
  }

  @PostMapping("/{id}/timelogs")
  @RequiresPermission("task:write")
  public ResponseEntity<ApiResult<Object>> addTimelog(@PathVariable UUID id,
      @RequestBody Map<String, Object> body,
      @RequestHeader(value = "Idempotency-Key", required = false) String idem) {
    requireIdem(idem);
    UUID actor = CurrentActor.userId();
    policy.requireTaskExecution(actor,id,"task.timelog.add");
    service.logTime(actor, id, LocalDate.parse((String) body.get("work_date")),
        ((Number) body.get("minutes")).intValue(), (String) body.get("note"));
    return ResponseEntity.ok(ApiResult.ok(null, "task.timelog.add.success"));
  }

  @PostMapping("/{id}/blocked")
  @RequiresPermission("task:write")
  public ResponseEntity<ApiResult<TaskDto>> blocked(@PathVariable UUID id,
      @RequestBody Map<String, Object> body,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      @RequestHeader(value = "Idempotency-Key", required = false) String idem) {
    requireIdem(idem);
    UUID actor = CurrentActor.userId();
    policy.requireTaskWrite(actor, id);
    Task t = service.setBlocked(actor, id, Boolean.TRUE.equals(body.get("blocked_flag")),
        (String) body.get("blocked_reason"), ifMatch(ifMatch));
    return ResponseEntity.ok(ApiResult.ok(dto(t), "task.blocked.success"));
  }

  @PostMapping("/{id}/deadline")
  @RequiresPermission("task:write")
  public ResponseEntity<ApiResult<TaskDto>> deadline(@PathVariable UUID id,
      @RequestBody Map<String, Object> body,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      @RequestHeader(value = "Idempotency-Key", required = false) String idem) {
    requireIdem(idem);
    UUID actor = CurrentActor.userId();
    // Deadline change requires MANAGER — MEMBER cannot change task deadline.
    policy.requireTaskManagerWrite(actor, id, "task.deadline.set", "task:write");
    LocalDate d = body.get("due_date") == null ? null : LocalDate.parse((String) body.get("due_date"));
    OffsetDateTime inst = body.get("due_at") == null ? null : OffsetDateTime.parse((String) body.get("due_at"));
    Task t = service.setDeadline(actor, id, d, inst, (String) body.get("due_timezone"), ifMatch(ifMatch));
    return ResponseEntity.ok(ApiResult.ok(dto(t), "task.deadline.success"));
  }

  @PostMapping("/{id}/delete")
  @RequiresPermission("task:write")
  public ResponseEntity<ApiResult<TaskDto>> softDelete(@PathVariable UUID id,
      @RequestHeader(value = "Idempotency-Key", required = false) String idem) {
    requireIdem(idem);
    UUID actor = CurrentActor.userId();
    policy.requireTaskWrite(actor, id);
    return ResponseEntity.ok(ApiResult.ok(dto(service.softDelete(actor, id)), "task.delete.success"));
  }

  @PostMapping("/{id}/restore")
  @RequiresPermission("task:write")
  public ResponseEntity<ApiResult<TaskDto>> restore(@PathVariable UUID id,
      @RequestHeader(value = "Idempotency-Key", required = false) String idem) {
    requireIdem(idem);
    UUID actor = CurrentActor.userId();
    policy.requireTaskWrite(actor, id);
    return ResponseEntity.ok(ApiResult.ok(dto(service.restore(actor, id)), "task.restore.success"));
  }

  @GetMapping("/{id}/checklist")
  @RequiresPermission("task:read")
  public ResponseEntity<ApiResult<List<Object>>> checklist(@PathVariable UUID id) {
    UUID actor = CurrentActor.userId();
    policy.requireTaskRead(actor, id);
    var items = checklist.findByTaskIdOrderByPositionAsc(id).stream()
        .map(c -> (Object) Map.of("id", c.getId().toString(),
            "task_id", c.getTaskId().toString(),
            "title", c.getTitle() == null ? "" : c.getTitle(),
            "done", c.isDone(),
            "position", c.getPosition()))
        .toList();
    return ResponseEntity.ok(ApiResult.ok(items, "task.checklist.success"));
  }
  public record ChecklistTick(boolean done){}
  @PatchMapping("/{id}/checklist/{itemId}")
  public ApiResult<?> tick(@PathVariable UUID id,@PathVariable UUID itemId,@RequestBody ChecklistTick body,@RequestHeader("If-Match") TaskVersion version) {
    var actor=CurrentActor.userId();policy.requireTaskExecution(actor,id,"task.checklist.tick");
    return ApiResult.ok(dto(service.tickChecklist(actor,id,itemId,body.done(),version.value())),"task.checklist.tick.success");
  }
  @GetMapping("/{id}/subtasks")
  public ApiResult<?> subtasks(@PathVariable UUID id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
    var actor=CurrentActor.userId();policy.requireTaskRead(actor,id);
    var rows=tasks.findByParentTaskIdAndDeletedAtIsNull(id,org.springframework.data.domain.PageRequest.of(Math.max(page,0),Math.max(1,Math.min(size,100)),org.springframework.data.domain.Sort.by("id")));
    var items=rows.getContent().stream().filter(t->{try{policy.requireTaskRead(actor,t.getId());return true;}catch(AppException e){if(e.getHttpStatus().value()==403||e.getHttpStatus().value()==404)return false;throw e;}}).map(TaskController::dto).toList();
    return ApiResult.ok(items,"task.subtasks.success");
  }

  @GetMapping("/{id}/timelogs")
  @RequiresPermission("task:read")
  public ResponseEntity<ApiResult<List<Object>>> timelogs(@PathVariable UUID id) {
    UUID actor = CurrentActor.userId();
    policy.requireTaskRead(actor, id);
    var items = timelogs.findByTaskId(id).stream()
        .map(l -> (Object) Map.of("id", l.getId().toString(),
            "task_id", l.getTaskId().toString(),
            "user_id", l.getUserId().toString(),
            "work_date", String.valueOf(l.getWorkDate()),
            "minutes", l.getMinutes()))
        .toList();
    return ResponseEntity.ok(ApiResult.ok(items, "task.timelogs.success"));
  }
}
