package vn.smarttask.workflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
import vn.smarttask.task.repo.TaskAssignmentRepository;
import vn.smarttask.task.repo.TaskRepository;
import vn.smarttask.workflow.entity.ApprovalRound;
import vn.smarttask.workflow.repo.*;
import vn.smarttask.workflow.service.WorkflowService;

/** Forbidden transitions: TODO->COMPLETED denied (409); TODO->CANCELLED ok; version conflict 409. */
class TransitionForbiddenTest {
  WorkflowService svc(TaskRepository tasks, ProjectRepository projects,
      ProjectMembershipRepository members, TaskAssignmentRepository assigns,
      ApprovalRoundRepository rounds, ApprovalDesignationRepository designations,
      ApprovalDecisionRepository decisions, WorkflowEventRepository events,
      MilestoneRepository milestones, AuditService audit, OutboxService outbox) {
    return new WorkflowService(tasks, projects, members, assigns, rounds, designations,
        decisions, events, milestones, audit, outbox);
  }

  @Test
  void todoToCompletedForbidden() {
    assertFalse(WorkflowTransition.isAllowed(TaskState.TODO, TaskState.COMPLETED));
  }

  @Test
  void submitFromTodoForbidden409() {
    TaskRepository tasks = mock(TaskRepository.class);
    ProjectRepository projects = mock(ProjectRepository.class);
    ProjectMembershipRepository members = mock(ProjectMembershipRepository.class);
    TaskAssignmentRepository assigns = mock(TaskAssignmentRepository.class);
    ApprovalRoundRepository rounds = mock(ApprovalRoundRepository.class);
    ApprovalDesignationRepository designations = mock(ApprovalDesignationRepository.class);
    ApprovalDecisionRepository decisions = mock(ApprovalDecisionRepository.class);
    WorkflowEventRepository events = mock(WorkflowEventRepository.class);
    MilestoneRepository milestones = mock(MilestoneRepository.class);
    AuditService audit = mock(AuditService.class);
    OutboxService outbox = mock(OutboxService.class);
    UUID projectId = UUID.randomUUID(), taskId = UUID.randomUUID(), actor = UUID.randomUUID();
    Task t = new Task(taskId, projectId, "T", actor);
    t.setState("TODO");
    when(tasks.findById(taskId)).thenReturn(Optional.of(t));
    when(tasks.findByIdForUpdate(taskId)).thenReturn(Optional.of(t));
    when(members.findMembership(projectId, actor)).thenReturn(
        Optional.of(new ProjectMembership(UUID.randomUUID(), projectId, actor, "MEMBER")));
    Project p = new Project(projectId, UUID.randomUUID(), "P", actor);
    p.setStatus("ACTIVE");
    when(projects.findById(projectId)).thenReturn(Optional.of(p));

    var ex = assertThrows(AppException.class,
        () -> svc(tasks, projects, members, assigns, rounds, designations, decisions,
            events, milestones, audit, outbox).submit(actor, taskId, 1));
    assertEquals(409, ex.getHttpStatus().value());
    assertEquals("TODO", t.getState());
  }

  @Test
  void versionConflict409() {
    TaskRepository tasks = mock(TaskRepository.class);
    ProjectRepository projects = mock(ProjectRepository.class);
    ProjectMembershipRepository members = mock(ProjectMembershipRepository.class);
    TaskAssignmentRepository assigns = mock(TaskAssignmentRepository.class);
    ApprovalRoundRepository rounds = mock(ApprovalRoundRepository.class);
    ApprovalDesignationRepository designations = mock(ApprovalDesignationRepository.class);
    ApprovalDecisionRepository decisions = mock(ApprovalDecisionRepository.class);
    WorkflowEventRepository events = mock(WorkflowEventRepository.class);
    MilestoneRepository milestones = mock(MilestoneRepository.class);
    AuditService audit = mock(AuditService.class);
    OutboxService outbox = mock(OutboxService.class);
    UUID projectId = UUID.randomUUID(), taskId = UUID.randomUUID(), actor = UUID.randomUUID();
    Task t = new Task(taskId, projectId, "T", actor);
    t.setState("TODO");
    t.setVersion(3);
    when(tasks.findById(taskId)).thenReturn(Optional.of(t));
    when(tasks.findByIdForUpdate(taskId)).thenReturn(Optional.of(t));
    when(members.findMembership(projectId, actor)).thenReturn(
        Optional.of(new ProjectMembership(UUID.randomUUID(), projectId, actor, "MEMBER")));
    Project p = new Project(projectId, UUID.randomUUID(), "P", actor);
    p.setStatus("ACTIVE");
    when(projects.findById(projectId)).thenReturn(Optional.of(p));

    var ex = assertThrows(AppException.class,
        () -> svc(tasks, projects, members, assigns, rounds, designations, decisions,
            events, milestones, audit, outbox).start(actor, taskId, 1));
    assertEquals("VERSION_CONFLICT", ex.getReasonCode());
  }

  @Test
  void secondTerminalDecisionRejected() {
    UUID taskId = UUID.randomUUID(), roundId = UUID.randomUUID();
    TaskRepository tasks = mock(TaskRepository.class);
    ProjectMembershipRepository members = mock(ProjectMembershipRepository.class);
    TaskAssignmentRepository assigns = mock(TaskAssignmentRepository.class);
    ApprovalRoundRepository rounds = mock(ApprovalRoundRepository.class);
    ApprovalDesignationRepository designations = mock(ApprovalDesignationRepository.class);
    ApprovalDecisionRepository decisions = mock(ApprovalDecisionRepository.class);
    WorkflowEventRepository events = mock(WorkflowEventRepository.class);
    MilestoneRepository milestones = mock(MilestoneRepository.class);
    ProjectRepository projects = mock(ProjectRepository.class);
    AuditService audit = mock(AuditService.class);
    OutboxService outbox = mock(OutboxService.class);
    UUID projectId = UUID.randomUUID(), pm = UUID.randomUUID(), pm2 = UUID.randomUUID();
    Task t = new Task(taskId, projectId, "T", pm);
    t.setState("PENDING_APPROVAL");
    ApprovalRound r = new ApprovalRound(roundId, taskId, 1, pm);
    when(tasks.findById(taskId)).thenReturn(Optional.of(t));
    when(tasks.findByIdForUpdate(taskId)).thenReturn(Optional.of(t));
    when(rounds.findFirstByTaskIdOrderByRoundNoDesc(taskId)).thenReturn(Optional.of(r));
    when(rounds.findById(roundId)).thenReturn(Optional.of(r));
    when(rounds.findByIdForUpdate(roundId)).thenReturn(Optional.of(r));
    var win = new vn.smarttask.workflow.entity.ApprovalDecision(
        UUID.randomUUID(), roundId, "APPROVED", pm, null);
    when(decisions.findByRoundId(roundId)).thenReturn(Optional.of(win));
    when(assigns.findByTaskIdAndStatus(eq(taskId), eq("ACTIVE"))).thenReturn(List.of());

    var ex = assertThrows(AppException.class,
        () -> svc(tasks, projects, members, assigns, rounds, designations, decisions,
            events, milestones, audit, outbox).approve(pm2, taskId, 1));
    assertEquals("APPROVAL_ALREADY_DECIDED", ex.getReasonCode());
  }
}
