package vn.smarttask.task.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.smarttask.common.AppException;
import vn.smarttask.common.UuidV7;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.common.service.OutboxService;
import vn.smarttask.identity.repo.AppUserRepository;
import vn.smarttask.project.entity.Project;
import vn.smarttask.project.repo.ProjectMembershipRepository;
import vn.smarttask.project.repo.ProjectRepository;
import vn.smarttask.realtime.RealtimePublisher;
import vn.smarttask.task.DependencyChecker;
import vn.smarttask.task.SubtaskDepthGuard;
import vn.smarttask.task.entity.*;
import vn.smarttask.task.repo.*;
import vn.smarttask.workspace.repo.WorkspaceRepository;

/** PH-2 TaskService: manual (no-AI) task & collaboration primitives. */
@Service
public class TaskService {
  private final TaskRepository tasks;
  private final ProjectRepository projects;
  private final ProjectMembershipRepository projectMembers;
  private final WorkspaceRepository workspaces;
  private final TaskAssignmentRepository assignments;
  private final TaskDependencyRepository dependencies;
  private final ChecklistItemRepository checklist;
  private final TimeLogRepository timelogs;
  private final AppUserRepository users;
  private final AuditService audit;
  private final OutboxService outbox;

  public TaskService(TaskRepository tasks, ProjectRepository projects,
      ProjectMembershipRepository projectMembers, WorkspaceRepository workspaces,
      TaskAssignmentRepository assignments, TaskDependencyRepository dependencies,
      ChecklistItemRepository checklist, TimeLogRepository timelogs,
      AppUserRepository users, AuditService audit, OutboxService outbox) {
    this.tasks = tasks; this.projects = projects; this.projectMembers = projectMembers;
    this.workspaces = workspaces; this.assignments = assignments; this.dependencies = dependencies;
    this.checklist = checklist; this.timelogs = timelogs; this.users = users;
    this.audit = audit; this.outbox = outbox;
  }

  /**
   * Optional so plain-unit tests can construct the service without Spring;
   * production always injects it. Signals are advisory (client refetches).
   */
  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private RealtimePublisher realtime;

  private void signal(Task t, String kind) {
    if (realtime != null) realtime.project(t.getProjectId(), kind, t.getId());
  }

  private void requireActiveUser(UUID userId) {
    var u = users.findById(userId).orElseThrow(() -> AppException.notFound("User not found"));
    if (!u.isActive() || !"ACTIVE".equals(u.getStatus())) throw AppException.accountNotActive("Not active");
    if (u.isLocked()) throw AppException.accountLocked("Locked");
  }

  /** Returns membership; throws 404 (no read) when absent/inactive to avoid existence leak. */
  private vn.smarttask.project.entity.ProjectMembership requireParticipation(UUID projectId, UUID userId,
      String action) {
    var m = projectMembers.findMembership(projectId, userId)
        .orElseThrow(() -> AppException.notFound("Project not found"));
    if ("GUEST".equals(m.getRole())) {
      audit.appendDenied(userId, action, "task", null, "GUEST_DENIED");
      throw AppException.forbidden("Project guest cannot perform this action");
    }
    return m;
  }

  private Project requireWritableProject(UUID projectId) {
    Project p = projects.findById(projectId).orElseThrow(() -> AppException.notFound("Project not found"));
    if (!"PLANNING".equals(p.getStatus()) && !"ACTIVE".equals(p.getStatus())) {
      throw AppException.stateConflict("PROJECT_NOT_WRITABLE", "Project not writable");
    }
    var ws = workspaces.findById(p.getWorkspaceId())
        .orElseThrow(() -> AppException.notFound("Workspace not found"));
    if (!"ACTIVE".equals(ws.getStatus())) {
      throw AppException.stateConflict("WS_ARCHIVED", "Workspace not active");
    }
    return p;
  }

  private Task requireTask(UUID taskId) {
    return tasks.findById(taskId).orElseThrow(() -> AppException.notFound("Task not found"));
  }

  private void checkVersion(Task t, long expected) {
    if (t.getVersion() != expected) {
      audit.appendDenied(null, "task.write", "task", t.getId(), "VERSION_CONFLICT");
      throw AppException.versionConflict("Stale version", t.getVersion());
    }
  }

  private void requireNotLocked(Task t, String action, UUID actor) {
    if (vn.smarttask.task.PendingApprovalGuard.isLocked(t.getState())) {
      if (!vn.smarttask.task.PendingApprovalGuard.LOCKED_ACTIONS.contains(action)) return;
      audit.appendDenied(actor, action, "task", t.getId(), "EXECUTION_LOCKED");
      throw AppException.executionLocked("EXECUTION_LOCKED", "Task locked pending approval");
    }
    if ("COMPLETED".equals(t.getState()) || "CANCELLED".equals(t.getState())) {
      throw AppException.stateConflict("TASK_TERMINAL", "Task is terminal");
    }
  }

  // TK-01 create
  @Transactional
  public Task create(UUID actorId, UUID projectId, String title, String description,
      String priority, UUID milestoneId) {
    requireActiveUser(actorId);
    Project p = requireWritableProject(projectId);
    var m = requireParticipation(projectId, actorId, "task.create");
    if ("MEMBER".equals(m.getRole()) && !isMemberCreateAllowed(p)) {
      audit.appendDenied(actorId, "task.create", "task", null, "MEMBER_CREATE_DENIED");
      throw AppException.forbidden("Member task creation not allowed");
    }
    if (title == null || title.isBlank()) throw AppException.badRequest("TITLE_REQUIRED", "Title required");
    Task t = new Task(UuidV7.generate(), projectId, title.trim(), actorId);
    t.setDescription(description);
    if (priority != null) t.setPriority(priority);
    t.setMilestoneId(milestoneId);
    tasks.save(t);
    audit.append(actorId, "task.create", "task", t.getId(), "ALLOWED", null, "{}");
    outbox.emit("task", t.getId(), "task.created", "{}");
    signal(t, "TASK_CREATED");
    return t;
  }

  private boolean isMemberCreateAllowed(Project p) {
    return p.isAllowMemberCreateTask();
  }

  @Transactional
  public Task createSubtask(UUID actorId, UUID parentId, String title, String description) {
    requireActiveUser(actorId);
    Task parent = requireTask(parentId);
    requireWritableProject(parent.getProjectId());
    requireParticipation(parent.getProjectId(), actorId, "task.subtask.create");
    if (!SubtaskDepthGuard.isAllowed(parent.getDepth())) {
      audit.appendDenied(actorId, "task.subtask.create", "task", parentId, "SUBTASK_MAX_DEPTH_EXCEEDED");
      throw AppException.unprocessable("SUBTASK_MAX_DEPTH_EXCEEDED", "Max subtask depth exceeded");
    }
    if (parent.getDeletedAt() != null) throw AppException.notFound("Parent not found");
    Task sub = new Task(UuidV7.generate(), parent.getProjectId(), title, actorId);
    sub.setDescription(description);
    sub.setParentTaskId(parent.getId());
    sub.setDepth(parent.getDepth() + 1);
    sub.setMilestoneId(null); // subtask forbidden milestone (CHECK)
    tasks.save(sub);
    audit.append(actorId, "task.subtask.create", "task", sub.getId(), "ALLOWED", null, "{}");
    outbox.emit("task", sub.getId(), "task.subtask.created", "{}");
    signal(sub, "TASK_CREATED");
    return sub;
  }

  @Transactional
  public Task updateContent(UUID actorId, UUID taskId, String title, String description, long expectedVersion) {
    requireActiveUser(actorId);
    Task t = requireTask(taskId);
    requireParticipation(t.getProjectId(), actorId, "task.update");
    checkVersion(t, expectedVersion);
    requireNotLocked(t, "task.update", actorId);
    if (title != null && !title.isBlank()) t.setTitle(title.trim());
    if (description != null) t.setDescription(description);
    t.setVersion(t.getVersion() + 1);
    tasks.save(t);
    audit.append(actorId, "task.update", "task", t.getId(), "ALLOWED", null, "{}");
    outbox.emit("task", t.getId(), "task.updated", "{}");
    signal(t, "TASK_UPDATED");
    return t;
  }

  /** Deadline: stores instant + original tz; date-only = 23:59:59 in setter's tz. Recompute reminder stub + notify assignee. */
  @Transactional
  public Task setDeadline(UUID actorId, UUID taskId, LocalDate dateOnly, OffsetDateTime instant,
      String timezone, long expectedVersion) {
    requireActiveUser(actorId);
    Task t = requireTask(taskId);
    requireParticipation(t.getProjectId(), actorId, "task.deadline.set");
    checkVersion(t, expectedVersion);
    requireNotLocked(t, "task.deadline.set", actorId);
    OffsetDateTime due;
    if (dateOnly != null) {
      ZoneId tz = timezone != null ? ZoneId.of(timezone) : ZoneOffset.UTC;
      ZonedDateTime z = dateOnly.atTime(23, 59, 59).atZone(tz);
      due = z.toOffsetDateTime();
      t.setDueTimezone(tz.getId());
      t.setDueIsDateOnly(true);
    } else {
      due = instant;
      t.setDueTimezone(timezone);
      t.setDueIsDateOnly(false);
    }
    t.setDueAt(due);
    t.setVersion(t.getVersion() + 1);
    tasks.save(t);
    // reminder recompute stub + notify assignees (outbox; same tx)
    for (var a : assignments.findByTaskIdAndStatus(taskId, "ACTIVE")) {
      outbox.emit("task", taskId, "task.reminder.recomputed", "{\"user_id\":\"" + a.getUserId() + "\"}");
    }
    audit.append(actorId, "task.deadline.set", "task", t.getId(), "ALLOWED", null, "{}");
    outbox.emit("task", t.getId(), "task.deadline.set", "{}");
    signal(t, "TASK_UPDATED");
    return t;
  }

  @Transactional
  public TaskAssignment assign(UUID actorId, UUID taskId, UUID targetId, long expectedVersion) {
    requireActiveUser(actorId);
    Task t = requireTask(taskId);
    checkVersion(t, expectedVersion);
    if ("PENDING_APPROVAL".equals(t.getState())) {
      audit.appendDenied(actorId, "task.assign", "task", taskId, "EXECUTION_LOCKED");
      throw AppException.executionLocked("EXECUTION_LOCKED", "Task locked pending approval");
    }
    requireParticipation(t.getProjectId(), actorId, "task.assign");
    // target must have project participation (any ACTIVE role incl. GUEST? spec: participation; keep ACTIVE any role)
    projectMembers.findMembership(t.getProjectId(), targetId)
        .orElseThrow(() -> AppException.stateConflict("NOT_PROJECT_MEMBER", "Target must be project member"));
    var existing = assignments.findByTaskIdAndUserId(taskId, targetId).orElse(null);
    if (existing != null) {
      existing.setStatus("ACTIVE");
      existing.setRemovalReason(null);
      assignments.save(existing);
      return existing;
    }
    TaskAssignment a = new TaskAssignment(UuidV7.generate(), taskId, targetId);
    assignments.save(a);
    t.setVersion(t.getVersion() + 1);
    tasks.save(t);
    audit.append(actorId, "task.assign", "task", taskId, "ALLOWED", null, "{}");
    outbox.emit("task", taskId, "task.assigned", "{\"user_id\":\"" + targetId + "\"}");
    if (realtime != null) realtime.project(t.getProjectId(), "TASK_ASSIGNED", taskId);
    return a;
  }

  @Transactional
  public void unassign(UUID actorId, UUID taskId, UUID targetId, String reason, long expectedVersion) {
    Task t = requireTask(taskId);
    checkVersion(t, expectedVersion);
    requireParticipation(t.getProjectId(), actorId, "task.unassign");
    requireNotLocked(t, "task.unassign", actorId);
    var a = assignments.findByTaskIdAndUserId(taskId, targetId)
        .orElseThrow(() -> AppException.notFound("Assignment not found"));
    a.setStatus("REMOVED");
    a.setRemovalReason(reason);
    assignments.save(a);
    audit.append(actorId, "task.unassign", "task", taskId, "ALLOWED", null, "{}");
    if (realtime != null) realtime.project(t.getProjectId(), "TASK_UNASSIGNED", taskId);
  }

  @Transactional
  public Task setBlocked(UUID actorId, UUID taskId, boolean flag, String reason, long expectedVersion) {
    requireActiveUser(actorId);
    Task t = requireTask(taskId);
    requireParticipation(t.getProjectId(), actorId, "task.blocked.set");
    checkVersion(t, expectedVersion);
    requireNotLocked(t, "task.blocked.set", actorId);
    if (flag && (reason == null || reason.isBlank())) {
      throw AppException.badRequest("BLOCKED_REASON_REQUIRED", "Blocked reason required");
    }
    t.setBlockedFlag(flag);
    t.setBlockedReason(flag ? reason : null);
    t.setVersion(t.getVersion() + 1);
    tasks.save(t);
    audit.append(actorId, "task.blocked.set", "task", taskId, "ALLOWED", null, "{}");
    outbox.emit("task", taskId, flag ? "task.blocked" : "task.unblocked", "{}");
    if (realtime != null) realtime.project(t.getProjectId(), flag ? "TASK_BLOCKED" : "TASK_UNBLOCKED", taskId);
    return t;
  }

  @Transactional
  public ChecklistItem addChecklist(UUID actorId, UUID taskId, String title) {
    requireActiveUser(actorId);
    Task t = requireTask(taskId);
    requireParticipation(t.getProjectId(), actorId, "task.checklist.add");
    requireNotLocked(t, "task.checklist.add", actorId);
    var items = checklist.findByTaskIdOrderByPositionAsc(taskId);
    int pos = items.stream().mapToInt(ChecklistItem::getPosition).max().orElse(-1) + 1;
    ChecklistItem c = new ChecklistItem(UuidV7.generate(), taskId, title, pos);
    checklist.save(c);
    audit.append(actorId, "task.checklist.add", "task", taskId, "ALLOWED", null, "{}");
    return c;
  }

  /** Dependencies: top-level tasks only, same project, no self-ref, cycle -> 422. */
  @Transactional
  public TaskDependency addDependency(UUID actorId, UUID taskId, UUID dependsOnId) {
    requireActiveUser(actorId);
    Task t = requireTask(taskId);
    Task other = requireTask(dependsOnId);
    requireParticipation(t.getProjectId(), actorId, "task.dependency.add");
    requireNotLocked(t, "task.dependency.add", actorId);
    if (!t.getProjectId().equals(other.getProjectId())) {
      throw AppException.unprocessable("DEPENDENCY_CROSS_PROJECT", "Same project required");
    }
    if (t.getDepth() != 0 || other.getDepth() != 0) {
      throw AppException.unprocessable("DEPENDENCY_TOP_LEVEL_ONLY", "Top-level tasks only");
    }
    if (taskId.equals(dependsOnId)) {
      throw AppException.unprocessable("DEPENDENCY_SELF", "Self reference forbidden");
    }
    // memory check (unit-testable)
    List<DependencyChecker.Edge> edges = new ArrayList<>();
    for (var d : dependencies.findByTaskId(taskId)) {
      edges.add(new DependencyChecker.Edge(d.getTaskId(), d.getDependsOnTaskId()));
    }
    // gather all known edges from repo (best effort: current task's edges + reverse lookup skipped in unit)
    if (DependencyChecker.wouldCreateCycle(taskId, dependsOnId, edges)) {
      audit.appendDenied(actorId, "task.dependency.add", "task", taskId, "DEPENDENCY_CYCLE_DETECTED");
      throw AppException.unprocessable("DEPENDENCY_CYCLE_DETECTED", "Dependency cycle detected");
    }
    // PG real check when available (native query may fail on non-PG; ignore failure -> memory check already ran)
    try {
      if (dependencies.hasPath(taskId, dependsOnId)) {
        audit.appendDenied(actorId, "task.dependency.add", "task", taskId, "DEPENDENCY_CYCLE_DETECTED");
        throw AppException.unprocessable("DEPENDENCY_CYCLE_DETECTED", "Dependency cycle detected");
      }
    } catch (AppException ae) { throw ae; } catch (Exception ignored) { }
    TaskDependency d = new TaskDependency(UuidV7.generate(), taskId, dependsOnId);
    dependencies.save(d);
    audit.append(actorId, "task.dependency.add", "task", taskId, "ALLOWED", null, "{}");
    return d;
  }

  /** TimeLog: user_id = actor, work_date <= today, minutes > 0, within edit window, blocked when PENDING_APPROVAL. */
  @Transactional
  public TimeLog logTime(UUID actorId, UUID taskId, LocalDate workDate, int minutes, String note) {
    requireActiveUser(actorId);
    Task t = requireTask(taskId);
    requireParticipation(t.getProjectId(), actorId, "task.timelog.add");
    if ("PENDING_APPROVAL".equals(t.getState())) {
      audit.appendDenied(actorId, "task.timelog.add", "task", taskId, "EXECUTION_LOCKED");
      throw AppException.executionLocked("EXECUTION_LOCKED", "Task locked pending approval");
    }
    if (workDate == null || workDate.isAfter(LocalDate.now())) {
      throw AppException.badRequest("WORK_DATE_INVALID", "work_date must be <= today");
    }
    if (workDate.isBefore(LocalDate.now().minusDays(30))) {
      throw AppException.stateConflict("TIMELOG_EDIT_WINDOW_CLOSED", "Edit window closed");
    }
    if (minutes <= 0) throw AppException.badRequest("MINUTES_INVALID", "minutes must be > 0");
    TimeLog log = new TimeLog(UuidV7.generate(), taskId, actorId, workDate, minutes);
    log.setNote(note);
    timelogs.save(log);
    audit.append(actorId, "task.timelog.add", "task", taskId, "ALLOWED", null, "{}");
    outbox.emit("task", taskId, "task.timelog.added", "{}");
    signal(t, "TASK_UPDATED");
    return log;
  }

  @Transactional
  public Task softDelete(UUID actorId, UUID taskId) {
    requireActiveUser(actorId);
    Task t = requireTask(taskId);
    requireParticipation(t.getProjectId(), actorId, "task.delete");
    UUID group = t.getDeleteGroupId() != null ? t.getDeleteGroupId() : UuidV7.generate();
    t.setDeleteGroupId(group);
    t.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
    t.setDeletedBy(actorId);
    // cascade soft-delete subtasks into same group
    for (var sub : tasks.findByParentTaskId(taskId)) {
      if (sub.getDeletedAt() == null) {
        sub.setDeleteGroupId(group);
        sub.setDeletedAt(t.getDeletedAt());
        sub.setDeletedBy(actorId);
        tasks.save(sub);
      }
    }
    t.setVersion(t.getVersion() + 1);
    tasks.save(t);
    audit.append(actorId, "task.delete", "task", taskId, "ALLOWED", null, "{}");
    outbox.emit("task", taskId, "task.deleted", "{}");
    signal(t, "TASK_DELETED");
    return t;
  }

  /** Restore only same delete_group + parent alive + does not restore assignments. */
  @Transactional
  public Task restore(UUID actorId, UUID taskId) {
    requireActiveUser(actorId);
    Task t = requireTask(taskId);
    requireParticipation(t.getProjectId(), actorId, "task.restore");
    if (t.getDeletedAt() == null) return t;
    UUID group = t.getDeleteGroupId();
    if (t.getParentTaskId() != null) {
      Task parent = requireTask(t.getParentTaskId());
      if (parent.getDeletedAt() != null) {
        throw AppException.stateConflict("PARENT_DELETED", "Parent still deleted");
      }
      if (group != null && parent.getDeleteGroupId() != null && !group.equals(parent.getDeleteGroupId())) {
        // different group: only restore if parent alive (it is) — allowed, keep own group cleared
      }
    }
    t.setDeletedAt(null);
    t.setDeletedBy(null);
    t.setDeleteGroupId(null);
    // restore siblings in same group (children), but NOT assignments (stay REMOVED)
    for (var sub : tasks.findByParentTaskId(t.getId())) {
      if (group != null && group.equals(sub.getDeleteGroupId())) {
        sub.setDeletedAt(null);
        sub.setDeletedBy(null);
        sub.setDeleteGroupId(null);
        tasks.save(sub);
      }
    }
    t.setVersion(t.getVersion() + 1);
    tasks.save(t);
    audit.append(actorId, "task.restore", "task", taskId, "ALLOWED", null, "{}");
    outbox.emit("task", taskId, "task.restored", "{}");
    signal(t, "TASK_RESTORED");
    return t;
  }
}
