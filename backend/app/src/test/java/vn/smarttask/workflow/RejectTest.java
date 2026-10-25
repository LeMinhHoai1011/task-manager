package vn.smarttask.workflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.smarttask.common.AppException;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.common.service.OutboxService;
import vn.smarttask.project.entity.Project;
import vn.smarttask.project.entity.ProjectMembership;
import vn.smarttask.project.repo.MilestoneRepository;
import vn.smarttask.project.repo.ProjectMembershipRepository;
import vn.smarttask.project.repo.ProjectRepository;
import vn.smarttask.task.entity.Task;
import vn.smarttask.task.entity.TaskAssignment;
import vn.smarttask.task.repo.TaskAssignmentRepository;
import vn.smarttask.task.repo.TaskRepository;
import vn.smarttask.workflow.entity.ApprovalDesignation;
import vn.smarttask.workflow.entity.ApprovalRound;
import vn.smarttask.workflow.repo.*;
import vn.smarttask.workflow.service.WorkflowService;

/** T-05 reject requires reason >= 3 chars; terminal single-decision enforced. */
class RejectTest {
  TaskRepository tasks; ProjectRepository projects; ProjectMembershipRepository members;
  TaskAssignmentRepository assigns; ApprovalRoundRepository rounds;
  ApprovalDesignationRepository designations; ApprovalDecisionRepository decisions;
  WorkflowEventRepository events; MilestoneRepository milestones;
  AuditService audit; OutboxService outbox;
  UUID pm, dev, taskId, projectId;

  Task task; ApprovalRound round;

  @BeforeEach
  void setup() {
    tasks = mock(TaskRepository.class); projects = mock(ProjectRepository.class);
    members = mock(ProjectMembershipRepository.class); assigns = mock(TaskAssignmentRepository.class);
    rounds = mock(ApprovalRoundRepository.class); designations = mock(ApprovalDesignationRepository.class);
    decisions = mock(ApprovalDecisionRepository.class); events = mock(WorkflowEventRepository.class);
    milestones = mock(MilestoneRepository.class);
    audit = mock(AuditService.class); outbox = mock(OutboxService.class);
    pm = UUID.randomUUID(); dev = UUID.randomUUID();
    projectId = UUID.randomUUID(); taskId = UUID.randomUUID();
    task = new Task(taskId, projectId, "T", dev);
    task.setState("PENDING_APPROVAL");
    round = new ApprovalRound(UUID.randomUUID(), taskId, 1, dev);
    when(tasks.save(any())).thenAnswer(i -> i.getArgument(0));
    when(rounds.save(any())).thenAnswer(i -> i.getArgument(0));
    when(decisions.save(any())).thenAnswer(i -> i.getArgument(0));
    when(events.save(any())).thenAnswer(i -> i.getArgument(0));
    when(tasks.findById(taskId)).thenReturn(Optional.of(task));
    when(tasks.findByIdForUpdate(taskId)).thenReturn(Optional.of(task));
    when(rounds.findFirstByTaskIdOrderByRoundNoDesc(taskId)).thenReturn(Optional.of(round));
    when(rounds.findById(round.getId())).thenReturn(Optional.of(round));
    when(rounds.findByIdForUpdate(round.getId())).thenReturn(Optional.of(round));
    when(decisions.findByRoundId(round.getId())).thenReturn(Optional.empty());
    when(assigns.findByTaskIdAndStatus(eq(taskId), eq("ACTIVE"))).thenReturn(List.of());
    when(members.findMembership(projectId, pm)).thenReturn(
        Optional.of(new ProjectMembership(UUID.randomUUID(), projectId, pm, "MANAGER")));
    when(designations.findByRoundIdAndUserId(round.getId(), pm))
        .thenReturn(Optional.of(new ApprovalDesignation(UUID.randomUUID(), round.getId(), pm, "DEFAULT")));
  }

  WorkflowService svc() {
    return new WorkflowService(tasks, projects, members, assigns, rounds, designations,
        decisions, events, milestones, audit, outbox);
  }

  @Test
  void rejectRequiresReason() {
    var ex1 = assertThrows(AppException.class, () -> svc().reject(pm, taskId, null, 1));
    assertEquals("REJECT_REASON_REQUIRED", ex1.getReasonCode());
    var ex2 = assertThrows(AppException.class, () -> svc().reject(pm, taskId, "no", 1));
    assertEquals("REJECT_REASON_REQUIRED", ex2.getReasonCode());
    assertEquals("PENDING_APPROVAL", task.getState());
  }

  @Test
  void rejectOkMovesToInProgressAndClosesRound() {
    Task t = svc().reject(pm, taskId, "needs rework", 1);
    assertEquals("IN_PROGRESS", t.getState());
    assertEquals("REJECTED", round.getStatus());
    verify(decisions).save(argThat(d -> "REJECTED".equals(d.getOutcome())
        && "needs rework".equals(d.getReason())));
    verify(audit, atLeastOnce()).append(eq(pm), eq("workflow.reject"), eq("task"), eq(taskId),
        eq("ALLOWED"), any(), anyString());
  }
}
