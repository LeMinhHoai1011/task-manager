package vn.smarttask.workflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.common.service.OutboxService;
import vn.smarttask.project.entity.Project;
import vn.smarttask.project.entity.ProjectMembership;
import vn.smarttask.project.repo.MilestoneRepository;
import vn.smarttask.project.repo.ProjectMembershipRepository;
import vn.smarttask.project.repo.ProjectRepository;
import vn.smarttask.task.entity.Task;
import vn.smarttask.task.repo.TaskAssignmentRepository;
import vn.smarttask.task.repo.TaskRepository;
import vn.smarttask.workflow.repo.*;
import vn.smarttask.workflow.service.WorkflowService;

/** No approver: task stays PENDING + APPROVAL_NO_APPROVER warning; SLA is flag-only (no auto-approve). */
class NoApproverTest {
  TaskRepository tasks; ProjectRepository projects; ProjectMembershipRepository members;
  TaskAssignmentRepository assigns; ApprovalRoundRepository rounds;
  ApprovalDesignationRepository designations; ApprovalDecisionRepository decisions;
  WorkflowEventRepository events; MilestoneRepository milestones;
  AuditService audit; OutboxService outbox;
  UUID dev, projectId, taskId;

  @BeforeEach
  void setup() {
    tasks = mock(TaskRepository.class); projects = mock(ProjectRepository.class);
    members = mock(ProjectMembershipRepository.class); assigns = mock(TaskAssignmentRepository.class);
    rounds = mock(ApprovalRoundRepository.class); designations = mock(ApprovalDesignationRepository.class);
    decisions = mock(ApprovalDecisionRepository.class); events = mock(WorkflowEventRepository.class);
    milestones = mock(MilestoneRepository.class);
    audit = mock(AuditService.class); outbox = mock(OutboxService.class);
    dev = UUID.randomUUID(); projectId = UUID.randomUUID(); taskId = UUID.randomUUID();
    when(tasks.save(any())).thenAnswer(i -> i.getArgument(0));
    when(rounds.save(any())).thenAnswer(i -> i.getArgument(0));
    when(events.save(any())).thenAnswer(i -> i.getArgument(0));
  }

  @Test
  void pendingWithWarningWhenNoApprover() {
    Project p = new Project(projectId, UUID.randomUUID(), "P", dev);
    p.setStatus("ACTIVE");
    p.setApprovalEnabled(true);
    when(projects.findById(projectId)).thenReturn(Optional.of(p));
    Task t = new Task(taskId, projectId, "T", dev);
    t.setState("IN_PROGRESS");
    when(tasks.findById(taskId)).thenReturn(Optional.of(t));
    when(tasks.findByIdForUpdate(taskId)).thenReturn(Optional.of(t));
    when(tasks.findByParentTaskId(taskId)).thenReturn(List.of());
    when(members.findMembership(projectId, dev)).thenReturn(
        Optional.of(new ProjectMembership(UUID.randomUUID(), projectId, dev, "MANAGER")));
    // only the submitter is PM -> nobody eligible
    when(members.findByProjectId(projectId)).thenReturn(List.of(
        new ProjectMembership(UUID.randomUUID(), projectId, dev, "MANAGER")));
    when(assigns.findByTaskIdAndStatus(eq(taskId), eq("ACTIVE"))).thenReturn(List.of());
    when(rounds.findFirstByTaskIdOrderByRoundNoDesc(taskId)).thenReturn(Optional.empty());
    when(rounds.findById(any())).thenAnswer(i -> Optional.of(
        new vn.smarttask.workflow.entity.ApprovalRound((UUID) i.getArgument(0), taskId, 1, dev)));

    WorkflowService svc = new WorkflowService(tasks, projects, members, assigns, rounds,
        designations, decisions, events, milestones, audit, outbox);
    var r = svc.submit(dev, taskId, 1);
    assertEquals("PENDING_APPROVAL", r.task().getState());
    assertTrue(r.noApproverWarning());
    verify(audit).append(eq(dev), eq("workflow.submit"), eq("task"), eq(taskId),
        eq("ALLOWED"), eq("APPROVAL_NO_APPROVER"), anyString());

    // SLA flag must not auto-approve: task stays PENDING
    svc.markSlaBreached(r.round().getId() == null ? null : r.round().getId(), dev);
    assertEquals("PENDING_APPROVAL", t.getState());
  }
}
