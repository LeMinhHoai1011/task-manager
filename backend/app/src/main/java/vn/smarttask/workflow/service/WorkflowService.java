package vn.smarttask.workflow.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.smarttask.common.AppException;
import vn.smarttask.common.UuidV7;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.common.service.OutboxService;
import vn.smarttask.project.entity.Project;
import vn.smarttask.project.repo.MilestoneRepository;
import vn.smarttask.project.repo.ProjectMembershipRepository;
import vn.smarttask.project.repo.ProjectRepository;
import vn.smarttask.realtime.RealtimePublisher;
import vn.smarttask.task.entity.Task;
import vn.smarttask.task.entity.TaskAssignment;
import vn.smarttask.task.repo.TaskAssignmentRepository;
import vn.smarttask.task.repo.TaskRepository;
import vn.smarttask.workflow.WorkflowGuard;
import vn.smarttask.workflow.WorkflowTransition;
import vn.smarttask.workflow.entity.*;
import vn.smarttask.workflow.repo.*;

/**
 * PH-2 WorkflowService: manual transitions T-01..T-10. No AI actors.
 * Every transition writes workflow_event + audit + outbox in the same tx, version+1, If-Match checked.
 */
@Service
public class WorkflowService {
  private final TaskRepository tasks;
  private final ProjectRepository projects;
  private final ProjectMembershipRepository projectMembers;
  private final TaskAssignmentRepository assignments;
  private final ApprovalRoundRepository rounds;
  private final ApprovalDesignationRepository designations;
  private final ApprovalDecisionRepository decisions;
  private final WorkflowEventRepository events;
  private final MilestoneRepository milestones;
  private final AuditService audit;
  private final OutboxService outbox;

  public WorkflowService(TaskRepository tasks, ProjectRepository projects,
      ProjectMembershipRepository projectMembers, TaskAssignmentRepository assignments,
      ApprovalRoundRepository rounds, ApprovalDesignationRepository designations,
      ApprovalDecisionRepository decisions, WorkflowEventRepository events,
      MilestoneRepository milestones, AuditService audit, OutboxService outbox) {
    this.tasks = tasks; this.projects = projects; this.projectMembers = projectMembers;
    this.assignments = assignments; this.rounds = rounds; this.designations = designations;
    this.decisions = decisions; this.events = events; this.milestones = milestones;
    this.audit = audit; this.outbox = outbox;
  }

  /** Optional in plain-unit tests; production injects it. Signals are advisory. */
  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private RealtimePublisher realtime;

  public record SubmitResult(ApprovalRound round, boolean noApproverWarning, Task task) {}

  private Task requireTaskForUpdate(UUID taskId) {
    try {
      return tasks.findByIdForUpdate(taskId).orElseThrow(() -> AppException.notFound("Task not found"));
    } catch (AppException ae) { throw ae; } catch (Exception e) {
      return tasks.findById(taskId).orElseThrow(() -> AppException.notFound("Task not found"));
    }
  }

  private void checkVersion(Task t, long expected, UUID actor, String action) {
    if (t.getVersion() != expected) {
      audit.appendDenied(actor, action, "task", t.getId(), "VERSION_CONFLICT");
      throw AppException.versionConflict("Stale version", t.getVersion());
    }
  }

  private void move(Task t, UUID actor, String to, String tid, String action) {
    String from = t.getState();
    boolean allowed = switch (from + "->" + to) {
      case "TODO->IN_PROGRESS", "IN_PROGRESS->TODO", "IN_PROGRESS->PENDING_APPROVAL",
          "PENDING_APPROVAL->IN_PROGRESS", "PENDING_APPROVAL->COMPLETED",
          "PENDING_APPROVAL->CANCELLED", "TODO->CANCELLED", "IN_PROGRESS->CANCELLED",
          "IN_PROGRESS->COMPLETED", "CANCELLED->TODO", "COMPLETED->IN_PROGRESS",
          "TODO->PENDING_APPROVAL" -> true;
      default -> false;
    };
    if (!allowed) {
      audit.appendDenied(actor, action, "task", t.getId(), "TRANSITION_FORBIDDEN");
      throw AppException.stateConflict("TRANSITION_FORBIDDEN", "Transition " + from + "->" + to + " forbidden");
    }
    t.setState(to);
    t.setVersion(t.getVersion() + 1);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    if ("COMPLETED".equals(to)) { t.setCompletedAt(now); t.setCompletedBy(actor); }
    if ("CANCELLED".equals(to)) { t.setCancelledAt(now); t.setCancelledBy(actor); }
    if ("IN_PROGRESS".equals(to) && ("COMPLETED".equals(from) || "CANCELLED".equals(from))) {
      // reopened_* set by callers
    }
    tasks.save(t);
    events.save(new WorkflowEvent(UuidV7.generate(), t.getId(), from, to, tid, actor));
    audit.append(actor, action, "task", t.getId(), "ALLOWED", null,
        "{\"from\":\"" + from + "\",\"to\":\"" + to + "\",\"tid\":\"" + tid + "\"}");
    outbox.emit("task", t.getId(), "task.workflow." + tid.toLowerCase().replace('-', '_'),
        "{\"from\":\"" + from + "\",\"to\":\"" + to + "\"}");
    if (realtime != null) {
      realtime.project(t.getProjectId(), "WORKFLOW_" + tid.replace('-', '_'), t.getId());
    }
  }

  private boolean isManager(UUID projectId, UUID userId) {
    return projectMembers.findMembership(projectId, userId)
        .map(m -> "MANAGER".equals(m.getRole()) && "ACTIVE".equals(m.getStatus()))
        .orElse(false);
  }

  private void requireManager(UUID projectId, UUID actor, String action) {
    if (!isManager(projectId, actor)) {
      audit.appendDenied(actor, action, "task", null, "MANAGER_REQUIRED");
      throw AppException.forbidden("Project manager required");
    }
  }

  private List<UUID> activeAssignees(UUID taskId) {
    return assignments.findByTaskIdAndStatus(taskId, "ACTIVE").stream()
        .map(TaskAssignment::getUserId).collect(Collectors.toList());
  }

  // T-01 start TODO->IN_PROGRESS (PLANNING project blocks)
  @Transactional
  public Task start(UUID actor, UUID taskId, long expectedVersion) {
    Task t = requireTaskForUpdate(taskId);
    checkVersion(t, expectedVersion, actor, "workflow.start");
    projectMembers.findMembership(t.getProjectId(), actor)
        .orElseThrow(() -> AppException.notFound("Project not found"));
    Project p = projects.findById(t.getProjectId()).orElseThrow(() -> AppException.notFound("Project not found"));
    if ("PLANNING".equals(p.getStatus())) {
      audit.appendDenied(actor, "workflow.start", "task", taskId, "PROJECT_PLANNING");
      throw AppException.stateConflict("PROJECT_PLANNING", "Cannot start in PLANNING project");
    }
    if (!"TODO".equals(t.getState())) {
      throw AppException.stateConflict("TRANSITION_FORBIDDEN", "Start requires TODO");
    }
    move(t, actor, "IN_PROGRESS", "T-01", "workflow.start");
    return t;
  }

  // T-02 submit IN_PROGRESS->PENDING_APPROVAL (approval ON) with subtask guard + auto-designate;
  // T-03 direct IN_PROGRESS->COMPLETED when approval OFF.
  @Transactional
  public SubmitResult submit(UUID actor, UUID taskId, long expectedVersion) {
    Task t = requireTaskForUpdate(taskId);
    checkVersion(t, expectedVersion, actor, "workflow.submit");
    projectMembers.findMembership(t.getProjectId(), actor)
        .orElseThrow(() -> AppException.notFound("Project not found"));
    if (!"IN_PROGRESS".equals(t.getState())) {
      throw AppException.stateConflict("TRANSITION_FORBIDDEN", "Submit requires IN_PROGRESS");
    }
    Project p = projects.findById(t.getProjectId()).orElseThrow(() -> AppException.notFound("Project not found"));
    if (!p.isApprovalEnabled()) {
      // T-03: straight to COMPLETED
      move(t, actor, "COMPLETED", "T-03", "workflow.submit");
      return new SubmitResult(null, false, t);
    }
    // approval ON: all subtasks must be COMPLETED/CANCELLED
    for (var sub : tasks.findByParentTaskId(taskId)) {
      if (!"COMPLETED".equals(sub.getState()) && !"CANCELLED".equals(sub.getState())) {
        audit.appendDenied(actor, "workflow.submit", "task", taskId, "SUBTASKS_INCOMPLETE");
        throw AppException.stateConflict("SUBTASKS_INCOMPLETE", "Subtasks incomplete");
      }
    }
    move(t, actor, "PENDING_APPROVAL", "T-02", "workflow.submit");
    int nextNo = rounds.findFirstByTaskIdOrderByRoundNoDesc(taskId).map(r -> r.getRoundNo() + 1).orElse(1);
    ApprovalRound round = new ApprovalRound(UuidV7.generate(), taskId, nextNo, actor);
    rounds.save(round);
    // auto-designate eligible PMs: ACTIVE membership, role MANAGER/MEMBER, not assignee, not submitter
    List<UUID> assignees = activeAssignees(taskId);
    List<UUID> eligible = projectMembers.findByProjectId(t.getProjectId()).stream()
        .filter(m -> "ACTIVE".equals(m.getStatus()))
        .filter(m -> "MANAGER".equals(m.getRole()))
        .map(vn.smarttask.project.entity.ProjectMembership::getUserId)
        .filter(uid -> !uid.equals(actor) && !assignees.contains(uid))
        .collect(Collectors.toList());
    for (UUID uid : eligible) {
      designations.save(new ApprovalDesignation(UuidV7.generate(), round.getId(), uid, "DEFAULT"));
    }
    boolean warning = eligible.isEmpty();
    if (warning) {
      audit.append(actor, "workflow.submit", "task", taskId, "ALLOWED", "APPROVAL_NO_APPROVER", "{}");
    }
    outbox.emit("task", taskId, "task.approval.submitted", "{\"round\":" + nextNo + "}");
    return new SubmitResult(round, warning, t);
  }

  /** Eligibility: ACTIVE membership (MANAGER/MEMBER) + ACTIVE designation + not ASG + not SUB + round OPEN. */
  public boolean isEligibleApprover(UUID roundId, UUID userId) {
    var round = rounds.findById(roundId).orElse(null);
    if (round == null || !"OPEN".equals(round.getStatus())) return false;
    var task = tasks.findById(round.getTaskId()).orElse(null);
    if (task == null) return false;
    var mem = projectMembers.findMembership(task.getProjectId(), userId).orElse(null);
    if (mem == null || !"ACTIVE".equals(mem.getStatus())) return false;
    if (!"MANAGER".equals(mem.getRole()) && !"MEMBER".equals(mem.getRole())) return false;
    var des = designations.findByRoundIdAndUserId(roundId, userId).orElse(null);
    if (des == null || !des.isActive()) return false;
    List<UUID> assignees = activeAssignees(task.getId());
    if (assignees.contains(userId)) return false; // ASG
    if (WorkflowGuard.isSelfApprovalBlocked(userId, round.getSubmittedBy(), assignees)) return false; // SUB
    if (userId.equals(round.getSubmittedBy()) && assignees.contains(userId)) return false;
    return true;
  }

  // T-04 approve PENDING_APPROVAL->COMPLETED
  @Transactional
  public Task approve(UUID actor, UUID taskId, long expectedVersion) {
    Task t = requireTaskForUpdate(taskId);
    checkVersion(t, expectedVersion, actor, "workflow.approve");
    if (!"PENDING_APPROVAL".equals(t.getState())) {
      throw AppException.stateConflict("TRANSITION_FORBIDDEN", "Approve requires PENDING_APPROVAL");
    }
    var round = rounds.findFirstByTaskIdOrderByRoundNoDesc(taskId)
        .orElseThrow(() -> AppException.stateConflict("NO_OPEN_ROUND", "No approval round"));
    var locked = rounds.findByIdForUpdate(round.getId()).orElse(round);
    if (!"OPEN".equals(locked.getStatus())) {
      throw AppException.stateConflict("ROUND_NOT_OPEN", "Round not open");
    }
    if (decisions.findByRoundId(locked.getId()).isPresent()) {
      var win = decisions.findByRoundId(locked.getId()).get();
      audit.appendDenied(actor, "workflow.approve", "task", taskId, "APPROVAL_ALREADY_DECIDED");
      throw AppException.stateConflict("APPROVAL_ALREADY_DECIDED",
          "Already decided: " + win.getOutcome() + " by " + win.getDecidedBy())
          .withWinningDecision(win.getOutcome()).withResourceState(t.getState());
    }
    List<UUID> assignees = activeAssignees(taskId);
    if (assignees.contains(actor)) {
      audit.appendDenied(actor, "workflow.approve", "task", taskId, "SELF_APPROVAL");
      throw AppException.forbidden("Assignee cannot approve (no self-approval)");
    }
    if (WorkflowGuard.isSelfApprovalBlocked(actor, locked.getSubmittedBy(), assignees)) {
      audit.appendDenied(actor, "workflow.approve", "task", taskId, "SELF_APPROVAL");
      throw AppException.forbidden("Self-approval forbidden");
    }
    if (!isEligibleApprover(locked.getId(), actor)) {
      audit.appendDenied(actor, "workflow.approve", "task", taskId, "NOT_ELIGIBLE_APPROVER");
      throw AppException.forbidden("Not eligible approver");
    }
    decisions.save(new ApprovalDecision(UuidV7.generate(), locked.getId(), "APPROVED", actor, null));
    locked.setStatus("APPROVED");
    locked.setRoundOutcome("APPROVED");
    locked.setDecidedAt(OffsetDateTime.now(ZoneOffset.UTC));
    rounds.save(locked);
    move(t, actor, "COMPLETED", "T-04", "workflow.approve");
    return t;
  }

  // T-05 reject PENDING_APPROVAL->IN_PROGRESS (reason >= 3 chars)
  @Transactional
  public Task reject(UUID actor, UUID taskId, String reason, long expectedVersion) {
    if (reason == null || reason.trim().length() < 3) {
      audit.appendDenied(actor, "workflow.reject", "task", taskId, "REJECT_REASON_REQUIRED");
      throw AppException.badRequest("REJECT_REASON_REQUIRED", "Reject reason >= 3 chars required");
    }
    Task t = requireTaskForUpdate(taskId);
    checkVersion(t, expectedVersion, actor, "workflow.reject");
    if (!"PENDING_APPROVAL".equals(t.getState())) {
      throw AppException.stateConflict("TRANSITION_FORBIDDEN", "Reject requires PENDING_APPROVAL");
    }
    var round = rounds.findFirstByTaskIdOrderByRoundNoDesc(taskId)
        .orElseThrow(() -> AppException.stateConflict("NO_OPEN_ROUND", "No approval round"));
    var locked = rounds.findByIdForUpdate(round.getId()).orElse(round);
    if (!"OPEN".equals(locked.getStatus())) {
      throw AppException.stateConflict("ROUND_NOT_OPEN", "Round not open");
    }
    if (decisions.findByRoundId(locked.getId()).isPresent()) {
      audit.appendDenied(actor, "workflow.reject", "task", taskId, "APPROVAL_ALREADY_DECIDED");
      throw AppException.stateConflict("APPROVAL_ALREADY_DECIDED", "Round already decided")
          .withWinningDecision(decisions.findByRoundId(locked.getId()).get().getOutcome())
          .withResourceState(t.getState());
    }
    List<UUID> assignees = activeAssignees(taskId);
    if (assignees.contains(actor)) {
      audit.appendDenied(actor, "workflow.reject", "task", taskId, "SELF_APPROVAL");
      throw AppException.forbidden("Assignee cannot decide (no self-approval)");
    }
    if (!isEligibleApprover(locked.getId(), actor)) {
      audit.appendDenied(actor, "workflow.reject", "task", taskId, "NOT_ELIGIBLE_APPROVER");
      throw AppException.forbidden("Not eligible approver");
    }
    decisions.save(new ApprovalDecision(UuidV7.generate(), locked.getId(), "REJECTED", actor, reason.trim()));
    locked.setStatus("REJECTED");
    locked.setRoundOutcome("REJECTED");
    locked.setDecidedAt(OffsetDateTime.now(ZoneOffset.UTC));
    rounds.save(locked);
    move(t, actor, "IN_PROGRESS", "T-05", "workflow.reject");
    return t;
  }

  // T-06/T-07 cancel: closes OPEN round as CANCELLED (no fake approve/reject decision row)
  @Transactional
  public Task cancel(UUID actor, UUID taskId, String reason, long expectedVersion) {
    Task t = requireTaskForUpdate(taskId);
    checkVersion(t, expectedVersion, actor, "workflow.cancel");
    if ("COMPLETED".equals(t.getState()) || "CANCELLED".equals(t.getState())) {
      throw AppException.stateConflict("TRANSITION_FORBIDDEN", "Cannot cancel terminal task");
    }
    String tid = "TODO".equals(t.getState()) ? "T-07" : "IN_PROGRESS".equals(t.getState()) ? "T-08" : "T-06";
    for (var r : rounds.findByTaskIdAndStatus(taskId, "OPEN")) {
      r.setStatus("CANCELLED");
      r.setRoundOutcome("CANCELLED");
      r.setDecidedAt(OffsetDateTime.now(ZoneOffset.UTC));
      rounds.save(r);
      audit.append(actor, "workflow.round.cancel", "task", taskId, "ALLOWED", "TASK_CANCELLED", "{}");
    }
    t.setCancelledReason(reason);
    move(t, actor, "CANCELLED", tid, "workflow.cancel");
    return t;
  }

  // T-08 reopen COMPLETED->IN_PROGRESS (PM only + reason, history preserved)
  @Transactional
  public Task reopenCompleted(UUID actor, UUID taskId, String reason, long expectedVersion) {
    if (reason == null || reason.trim().length() < 3) {
      throw AppException.badRequest("REOPEN_REASON_REQUIRED", "Reopen reason required");
    }
    Task t = requireTaskForUpdate(taskId);
    checkVersion(t, expectedVersion, actor, "workflow.reopen");
    if (!"COMPLETED".equals(t.getState())) {
      throw AppException.stateConflict("TRANSITION_FORBIDDEN", "Reopen-completed requires COMPLETED");
    }
    requireManager(t.getProjectId(), actor, "workflow.reopen");
    t.setReopenedAt(OffsetDateTime.now(ZoneOffset.UTC));
    t.setReopenedBy(actor);
    t.setReopenedReason(reason.trim());
    move(t, actor, "IN_PROGRESS", "T-08", "workflow.reopen");
    return t;
  }

  // T-09 reopen CANCELLED->TODO (PM only, does not auto-reopen subtasks)
  @Transactional
  public Task reopenCancelled(UUID actor, UUID taskId, String reason, long expectedVersion) {
    if (reason == null || reason.trim().length() < 3) {
      throw AppException.badRequest("REOPEN_REASON_REQUIRED", "Reopen reason required");
    }
    Task t = requireTaskForUpdate(taskId);
    checkVersion(t, expectedVersion, actor, "workflow.reopen");
    if (!"CANCELLED".equals(t.getState())) {
      throw AppException.stateConflict("TRANSITION_FORBIDDEN", "Reopen-cancelled requires CANCELLED");
    }
    requireManager(t.getProjectId(), actor, "workflow.reopen");
    t.setReopenedAt(OffsetDateTime.now(ZoneOffset.UTC));
    t.setReopenedBy(actor);
    t.setReopenedReason(reason.trim());
    move(t, actor, "TODO", "T-09", "workflow.reopen");
    return t;
  }

  /** T-10 cascade when project COMPLETED: same-tx sync. COMPLETED tasks kept; rest CANCELLED; OPEN rounds + OPEN milestones CANCELLED. */
  @Transactional
  public void cascadeOnProjectCompleted(UUID projectId, UUID actor, String reason) {
    String r = reason != null ? reason : "PROJECT_COMPLETED_CASCADE";
    for (var t : tasks.findByProjectId(projectId)) {
      if ("COMPLETED".equals(t.getState())) continue;
      if ("CANCELLED".equals(t.getState())) continue;
      for (var open : rounds.findByTaskIdAndStatus(t.getId(), "OPEN")) {
        open.setStatus("CANCELLED");
        open.setRoundOutcome("CANCELLED");
        open.setDecidedAt(OffsetDateTime.now(ZoneOffset.UTC));
        rounds.save(open);
        audit.append(actor, "workflow.round.cancel", "task", t.getId(), "ALLOWED", r, "{}");
      }
      String from = t.getState();
      t.setState("CANCELLED");
      t.setCancelledAt(OffsetDateTime.now(ZoneOffset.UTC));
      t.setCancelledBy(actor);
      t.setCancelledReason(r);
      t.setVersion(t.getVersion() + 1);
      tasks.save(t);
      events.save(new WorkflowEvent(UuidV7.generate(), t.getId(), from, "CANCELLED", "T-10", actor));
      audit.append(actor, "workflow.cascade", "task", t.getId(), "ALLOWED", r, "{\"from\":\"" + from + "\"}");
      outbox.emit("task", t.getId(), "task.workflow.t_10", "{\"reason\":\"" + r + "\"}");
    }
    for (var m : milestones.findByProjectId(projectId)) {
      if ("OPEN".equals(m.getStatus())) {
        m.setStatus("CANCELLED");
        m.setCancelledReason(r);
        m.setCancelledAt(OffsetDateTime.now(ZoneOffset.UTC));
        milestones.save(m);
        audit.append(actor, "project.milestone.cancel", "project", projectId, "ALLOWED", r, "{}");
      }
    }
  }

  /** SLA is flag-only: marks breach, never auto-approves. */
  @Transactional
  public void markSlaBreached(UUID roundId, UUID actor) {
    var round = rounds.findById(roundId).orElseThrow(() -> AppException.notFound("Round not found"));
    round.setSlaBreached(true);
    rounds.save(round);
    audit.append(actor, "workflow.sla.breach", "task", round.getTaskId(), "ALLOWED", "SLA_BREACHED", "{}");
  }
}
