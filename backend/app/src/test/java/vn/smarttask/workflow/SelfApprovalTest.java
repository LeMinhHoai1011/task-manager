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

/** Self-approval: assignee+submitter blocked; creator-not-SUB allowed. */
class SelfApprovalTest {
  TaskRepository tasks; ProjectMembershipRepository members; TaskAssignmentRepository assigns;
  ApprovalRoundRepository rounds; ApprovalDesignationRepository designations;
  ApprovalDecisionRepository decisions; WorkflowEventRepository events;
  MilestoneRepository milestones; ProjectRepository projects;
  AuditService audit; OutboxService outbox;
  UUID actor, creator, projectId, taskId;
  Task task; ApprovalRound round;

  @BeforeEach
  void setup() {
    tasks = mock(TaskRepository.class); members = mock(ProjectMembershipRepository.class);
    assigns = mock(TaskAssignmentRepository.class); rounds = mock(ApprovalRoundRepository.class);
    designations = mock(ApprovalDesignationRepository.class); decisions = mock(ApprovalDecisionRepository.class);
    events = mock(WorkflowEventRepository.class); milestones = mock(MilestoneRepository.class);
    projects = mock(ProjectRepository.class);
    audit = mock(AuditService.class); outbox = mock(OutboxService.class);
    actor = UUID.randomUUID(); creator = UUID.randomUUID();
    projectId = UUID.randomUUID(); taskId = UUID.randomUUID();
    task = new Task(taskId, projectId, "T", creator);
    task.setState("PENDING_APPROVAL");
    round = new ApprovalRound(UUID.randomUUID(), taskId, 1, actor); // actor is submitter
    when(tasks.save(any())).thenAnswer(i -> i.getArgument(0));
    when(tasks.findById(taskId)).thenReturn(Optional.of(task));
    when(tasks.findByIdForUpdate(taskId)).thenReturn(Optional.of(task));
    when(rounds.findFirstByTaskIdOrderByRoundNoDesc(taskId)).thenReturn(Optional.of(round));
    when(rounds.findById(round.getId())).thenReturn(Optional.of(round));
    when(rounds.findByIdForUpdate(round.getId())).thenReturn(Optional.of(round));
    when(decisions.findByRoundId(round.getId())).thenReturn(Optional.empty());
    when(members.findMembership(projectId, actor)).thenReturn(
        Optional.of(new ProjectMembership(UUID.randomUUID(), projectId, actor, "MANAGER")));
    when(members.findMembership(projectId, creator)).thenReturn(
        Optional.of(new ProjectMembership(UUID.randomUUID(), projectId, creator, "MANAGER")));
  }

  WorkflowService svc() {
    return new WorkflowService(tasks, projects, members, assigns, rounds, designations,
        decisions, events, milestones, audit, outbox);
  }

  @Test
  void assigneeSubmitterBlocked() {
    // actor is BOTH submitter and active assignee -> SUB -> blocked
    var asg = new TaskAssignment(UUID.randomUUID(), taskId, actor);
    when(assigns.findByTaskIdAndStatus(eq(taskId), eq("ACTIVE"))).thenReturn(List.of(asg));
    when(designations.findByRoundIdAndUserId(round.getId(), actor))
        .thenReturn(Optional.of(new ApprovalDesignation(UUID.randomUUID(), round.getId(), actor, "DEFAULT")));
    var ex = assertThrows(AppException.class, () -> svc().approve(actor, taskId, 1));
    assertEquals(403, ex.getHttpStatus().value());
    assertEquals("PENDING_APPROVAL", task.getState());
    verify(audit).appendDenied(eq(actor), eq("workflow.approve"), eq("task"), eq(taskId), anyString());
  }

  @Test
  void creatorNotSubAllowed() {
    // creator is not submitter and not assignee, with designation -> eligible
    var other = new ApprovalRound(UUID.randomUUID(), taskId, 1, actor);
    when(rounds.findFirstByTaskIdOrderByRoundNoDesc(taskId)).thenReturn(Optional.of(other));
    when(rounds.findById(other.getId())).thenReturn(Optional.of(other));
    when(rounds.findByIdForUpdate(other.getId())).thenReturn(Optional.of(other));
    when(decisions.findByRoundId(other.getId())).thenReturn(Optional.empty());
    when(assigns.findByTaskIdAndStatus(eq(taskId), eq("ACTIVE"))).thenReturn(List.of());
    when(designations.findByRoundIdAndUserId(other.getId(), creator))
        .thenReturn(Optional.of(new ApprovalDesignation(UUID.randomUUID(), other.getId(), creator, "DEFAULT")));
    assertTrue(svc().isEligibleApprover(other.getId(), creator));
  }
}
