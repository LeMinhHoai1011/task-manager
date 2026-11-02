package vn.smarttask.calendar;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.smarttask.calendar.repo.ReminderRuleRepository;
import vn.smarttask.calendar.service.CalendarService;
import vn.smarttask.common.ApiResult;
import vn.smarttask.common.AppException;
import vn.smarttask.security.CurrentActor;
import vn.smarttask.security.PolicyEnforcer;
import vn.smarttask.security.RequiresPermission;
import vn.smarttask.task.repo.TaskAssignmentRepository;

@RestController
@RequestMapping("/api/v1")
public class CalendarController {
  @org.springframework.beans.factory.annotation.Autowired
  private vn.smarttask.task.service.TaskOverviewService overview;

  private final CalendarService calendar;
  private final PolicyEnforcer policy;
  private final ReminderRuleRepository reminders;
  private final vn.smarttask.task.repo.TaskRepository tasks;
  private final vn.smarttask.project.repo.ProjectMembershipRepository projectMembers;
  private final TaskAssignmentRepository assignments;

  public CalendarController(CalendarService calendar, PolicyEnforcer policy,
      ReminderRuleRepository reminders, vn.smarttask.task.repo.TaskRepository tasks,
      vn.smarttask.project.repo.ProjectMembershipRepository projectMembers,
      TaskAssignmentRepository assignments) {
    this.calendar = calendar;
    this.policy = policy;
    this.reminders = reminders;
    this.tasks = tasks;
    this.projectMembers = projectMembers;
    this.assignments = assignments;
  }

  private static void requireIdem(String k) {
    if (k == null || k.isBlank()) throw AppException.badRequest("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key required");
  }

  public ResponseEntity<ApiResult<List<Object>>> calendar(String from,String to,String view,String date,String timezone) {
    return calendar(from,to,view,date,timezone,"all",null,null,0,200);
  }
  @GetMapping("/calendar")
  @RequiresPermission("calendar:read")
  public ResponseEntity<ApiResult<List<Object>>> calendar(
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(defaultValue = "month") String view,
      @RequestParam(required = false) String date,
      @RequestParam(required = false) String timezone,
      @RequestParam(defaultValue="all") String scope,
      @RequestParam(required=false) UUID projectId,@RequestParam(required=false) UUID teamId,
      @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="200") int size) {
    UUID actor = CurrentActor.userId();
    if(!java.util.Set.of("day","week","month","unscheduled").contains(view))throw AppException.badRequest("CALENDAR_VIEW_UNKNOWN","Unknown view");
    LocalDate anchor;
    try {anchor=date==null?null:LocalDate.parse(date);}
    catch(java.time.DateTimeException e){throw AppException.badRequest("INVALID_DATE","Invalid calendar date");}
    var items = overview.calendar(actor,view,anchor,timezone,scope,projectId,teamId,page,size).stream().map(row->(Object)row).toList();
    return ResponseEntity.ok(ApiResult.ok(items, "calendar.list.success"));
  }

  public ResponseEntity<ApiResult<List<Object>>> reminders(){return reminders(0,100);}
  @GetMapping("/reminders")
  @RequiresPermission("calendar:read")
  public ResponseEntity<ApiResult<List<Object>>> reminders(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="100") int size) {
    UUID actor = CurrentActor.userId();
    var items = reminders.findByUserId(actor,org.springframework.data.domain.PageRequest.of(Math.max(page,0),Math.max(1,Math.min(size,100)),org.springframework.data.domain.Sort.by("createdAt").descending().and(org.springframework.data.domain.Sort.by("id")))).getContent().stream()
        .map(r -> (Object) Map.of("id", r.getId().toString(),
            "task_id", r.getTaskId().toString(),
            "remind_at", String.valueOf(r.getRemindAt()),
            "status", r.getStatus(),
            "paused", r.isPaused()))
        .toList();
    return ResponseEntity.ok(ApiResult.ok(items, "reminder.list.success"));
  }

  @PostMapping("/reminders")
  @RequiresPermission("calendar:write")
  public ResponseEntity<ApiResult<Object>> createReminder(
      @RequestBody java.util.Map<String, Object> body,
      @RequestHeader(value = "Idempotency-Key", required = false) String idem) {
    requireIdem(idem);
    UUID actor = CurrentActor.userId();
    UUID taskId = UUID.fromString((String) body.get("task_id"));
    requireReminderAccess(actor,taskId);
    var r = calendar.createRule(actor, taskId,
        java.time.OffsetDateTime.parse((String) body.get("remind_at")));
    return ResponseEntity.ok(ApiResult.ok(java.util.Map.of("id", r.getId().toString()),
        "reminder.create.success"));
  }

  @PostMapping("/reminders/{id}/pause")
  @RequiresPermission("calendar:write")
  public ResponseEntity<ApiResult<Object>> pauseReminder(@PathVariable UUID id,
      @RequestHeader(value = "Idempotency-Key", required = false) String idem) {
    requireIdem(idem);
    UUID actor = CurrentActor.userId();
    var rule = reminders.findById(id)
        .orElseThrow(() -> AppException.notFound("Reminder not found"));
    requireReminderAccess(actor,rule.getTaskId());
    calendar.pause(actor, id);
    return ResponseEntity.ok(ApiResult.ok(null, "reminder.pause.success"));
  }

  @PostMapping("/reminders/{id}/resume")
  @RequiresPermission("calendar:write")
  public ResponseEntity<ApiResult<Object>> resumeReminder(@PathVariable UUID id,
      @RequestHeader(value = "Idempotency-Key", required = false) String idem) {
    requireIdem(idem);
    UUID actor = CurrentActor.userId();
    var rule = reminders.findById(id)
        .orElseThrow(() -> AppException.notFound("Reminder not found"));
    requireReminderAccess(actor,rule.getTaskId());
    int digests = calendar.resume(actor, id);
    return ResponseEntity.ok(ApiResult.ok(Map.of("digests_sent", digests),
        "reminder.resume.success"));
  }

  @GetMapping(value = "/calendar/ics", produces = "text/calendar")
  @RequiresPermission("calendar:read")
  public ResponseEntity<String> ics() {
    UUID actor = CurrentActor.userId();
    // F2.5: actor-scoped project ids + project-scoped task read.
    java.util.List<UUID> visible = projectMembers.findActiveProjectIdsByUser(actor);
    // Per-project manager set: MANAGER/OWNER/ADMIN see all tasks in their project.
    java.util.Set<UUID> managerProjects = new java.util.HashSet<>();
    for (UUID pid : visible) {
      var mem = projectMembers.findMembership(pid, actor).orElse(null);
      if (mem != null && ("OWNER".equals(mem.getRole()) || "ADMIN".equals(mem.getRole())
          || "MANAGER".equals(mem.getRole()))) {
        managerProjects.add(pid);
      }
    }
    // actor's assigned task ids (for MEMBER/GUEST per-task filtering)
    java.util.Set<UUID> assignedTaskIds = new java.util.HashSet<>();
    if (!visible.isEmpty()) {
      for (var t : tasks.findByProjectIdInAndDeletedAtIsNull(visible)) {
        if (assignments.existsByTaskIdAndUserId(t.getId(), actor)) {
          assignedTaskIds.add(t.getId());
        }
      }
    }
    java.util.Set<UUID> visibleSet = new java.util.HashSet<>(visible);
    StringBuilder sb = new StringBuilder(
        "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//smarttask//EN\r\n");
    java.util.List<vn.smarttask.task.entity.Task> scoped = visible.isEmpty() ? List.of()
        : tasks.findByProjectIdInAndDeletedAtIsNull(visible);
    for (var t : scoped) {
      if (t.getDeletedAt() != null || t.getDueAt() == null) continue;
      try {policy.requireTaskRead(actor,t.getId());}
      catch(AppException e){if(e.getHttpStatus().value()==403||e.getHttpStatus().value()==404)continue;throw e;}
      if (!visibleSet.contains(t.getProjectId())) continue;
      // MEMBER/GUEST: only include tasks assigned to them.
      if (!managerProjects.contains(t.getProjectId()) && !assignedTaskIds.contains(t.getId())) {
        continue;
      }
      sb.append("BEGIN:VEVENT\r\n");
      sb.append("UID:").append(t.getId()).append("@smarttask\r\n");
      sb.append("DTSTAMP:").append(fmt(t.getDueAt())).append("\r\n");
      sb.append("DTSTART:").append(fmt(t.getDueAt())).append("\r\n");
      sb.append("SUMMARY:").append(escape(t.getTitle())).append("\r\n");
      sb.append("END:VEVENT\r\n");
    }
    sb.append("END:VCALENDAR\r\n");
    return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/calendar"))
        .body(sb.toString());
  }

  private void requireReminderAccess(UUID actor,UUID taskId) {
    var task=tasks.findById(taskId).filter(t->t.getDeletedAt()==null).orElseThrow(()->AppException.notFound("Task not found"));
    if("PERSONAL".equals(task.getScope())) {
      if(!actor.equals(task.getOwnerUserId()))throw AppException.notFound("Task not found");
    }else policy.requireTaskRead(actor,taskId);
  }
  private static String fmt(java.time.OffsetDateTime dt) {
    return dt.withOffsetSameInstant(java.time.ZoneOffset.UTC)
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
  }

  private static String escape(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")
        .replace("\n", "\\n").replace("\r", "");
  }
}
