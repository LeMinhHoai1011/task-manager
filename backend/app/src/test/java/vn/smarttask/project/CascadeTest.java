package vn.smarttask.project;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.smarttask.common.repo.AuditEventRepository;
import vn.smarttask.common.repo.OutboxEventRepository;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.common.service.OutboxService;
import vn.smarttask.identity.entity.AppUser;
import vn.smarttask.identity.repo.AppUserRepository;
import vn.smarttask.project.entity.Milestone;
import vn.smarttask.project.entity.Project;
import vn.smarttask.project.entity.ProjectMembership;
import vn.smarttask.project.repo.*;
import vn.smarttask.project.service.ProjectService;
import vn.smarttask.task.entity.Task;
import vn.smarttask.task.repo.TaskRepository;
import vn.smarttask.workflow.entity.ApprovalRound;
import vn.smarttask.workflow.repo.ApprovalRoundRepository;
import vn.smarttask.workflow.repo.WorkflowEventRepository;
import vn.smarttask.workspace.repo.WorkspaceMembershipRepository;
import vn.smarttask.workspace.repo.WorkspaceRepository;

/** PJ-05 cascade: COMPLETED kept; TODO/IN_PROGRESS/PENDING -> CANCELLED w/ PROJECT_COMPLETED_CASCADE; OPEN rounds + OPEN milestones closed. */
class CascadeTest {
  ProjectRepository projects; ProjectMembershipRepository members; MilestoneRepository milestones;
  WorkspaceRepository workspaces; WorkspaceMembershipRepository wsMembers; AppUserRepository users;
  TaskRepository taskRepo; ApprovalRoundRepository roundRepo; WorkflowEventRepository eventRepo;
  AuditService audit; OutboxService outbox;
  UUID pm, projectId;

  @BeforeEach
  void setup() {
    projects = mock(ProjectRepository.class); members = mock(ProjectMembershipRepository.class);
    milestones = mock(MilestoneRepository.class); workspaces = mock(WorkspaceRepository.class);
    wsMembers = mock(WorkspaceMembershipRepository.class); users = mock(AppUserRepository.class);
    taskRepo = mock(TaskRepository.class); roundRepo = mock(ApprovalRoundRepository.class);
    eventRepo = mock(WorkflowEventRepository.class);
    audit = mock(AuditService.class); outbox = mock(OutboxService.class);
    pm = UUID.randomUUID(); projectId = UUID.randomUUID();
    when(users.findById(pm)).thenReturn(Optional.of(new AppUser(pm, "pm", "pm@x.io", "h", "PM")));
    when(projects.save(any())).thenAnswer(i -> i.getArgument(0));
    when(taskRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    when(roundRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    when(eventRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    when(milestones.save(any())).thenAnswer(i -> i.getArgument(0));
  }

  @Test
  void cascadeKeepsCompletedCancelsRest() {
    Project p = new Project(projectId, UUID.randomUUID(), "P", pm);
    p.setStatus("ACTIVE");
    when(projects.findByIdForUpdate(projectId)).thenReturn(Optional.of(p));
    when(members.findMembership(projectId, pm)).thenReturn(
        Optional.of(new ProjectMembership(UUID.randomUUID(), projectId, pm, "MANAGER")));

    Task done = new Task(UUID.randomUUID(), projectId, "done", pm);
    done.setState("COMPLETED");
    Task todo = new Task(UUID.randomUUID(), projectId, "todo", pm);
    todo.setState("TODO");
    Task prog = new Task(UUID.randomUUID(), projectId, "prog", pm);
    prog.setState("IN_PROGRESS");
    Task pend = new Task(UUID.randomUUID(), projectId, "pend", pm);
    pend.setState("PENDING_APPROVAL");
    Task sub = new Task(UUID.randomUUID(), projectId, "sub", pm);
    sub.setState("IN_PROGRESS");
    sub.setParentTaskId(prog.getId());
    sub.setDepth(1);
    when(taskRepo.findByProjectId(projectId)).thenReturn(List.of(done, todo, prog, pend, sub));
    ApprovalRound open = new ApprovalRound(UUID.randomUUID(), pend.getId(), 1, pm);
    when(roundRepo.findByTaskIdAndStatus(eq(pend.getId()), eq("OPEN"))).thenReturn(List.of(open));
    when(roundRepo.findByTaskIdAndStatus(eq(todo.getId()), eq("OPEN"))).thenReturn(List.of());
    when(roundRepo.findByTaskIdAndStatus(eq(prog.getId()), eq("OPEN"))).thenReturn(List.of());
    when(roundRepo.findByTaskIdAndStatus(eq(done.getId()), eq("OPEN"))).thenReturn(List.of());
    when(roundRepo.findByTaskIdAndStatus(eq(sub.getId()), eq("OPEN"))).thenReturn(List.of());
    Milestone mOpen = new Milestone(UUID.randomUUID(), projectId, "M1", null);
    Milestone mDone = new Milestone(UUID.randomUUID(), projectId, "M2", null);
    mDone.setStatus("DONE");
    when(milestones.findByProjectId(projectId)).thenReturn(List.of(mOpen, mDone));

    ProjectService svc = new ProjectService(projects, members, milestones, workspaces,
        wsMembers, users, audit, outbox);
    svc.setTaskCascade(taskRepo, roundRepo, eventRepo);
    Project out = svc.complete(pm, projectId, p.getVersion());

    assertEquals("COMPLETED", out.getStatus());
    assertEquals("COMPLETED", done.getState()); // kept
    assertEquals("CANCELLED", todo.getState());
    assertEquals("CANCELLED", prog.getState());
    assertEquals("CANCELLED", pend.getState());
    assertEquals("CANCELLED", sub.getState());
    assertEquals("PROJECT_COMPLETED_CASCADE", todo.getCancelledReason());
    assertEquals("CANCELLED", open.getStatus());
    assertEquals("CANCELLED", mOpen.getStatus());
    assertEquals("DONE", mDone.getStatus());
    verify(audit, atLeastOnce()).append(eq(pm), eq("project.complete"), eq("project"),
        eq(projectId), eq("ALLOWED"), any(), anyString());
    verify(outbox, atLeastOnce()).emit(eq("project"), eq(projectId), eq("project.completed"), anyString());
  }

  @Test
  void reactivateDoesNotReopenTasks() {
    Project p = new Project(projectId, UUID.randomUUID(), "P", pm);
    p.setStatus("COMPLETED");
    when(projects.findByIdForUpdate(projectId)).thenReturn(Optional.of(p));
    when(members.findMembership(projectId, pm)).thenReturn(
        Optional.of(new ProjectMembership(UUID.randomUUID(), projectId, pm, "MANAGER")));
    ProjectService svc = new ProjectService(projects, members, milestones, workspaces,
        wsMembers, users, audit, outbox);
    svc.setTaskCascade(taskRepo, roundRepo, eventRepo);
    Project out = svc.reactivate(pm, projectId, p.getVersion());
    assertEquals("ACTIVE", out.getStatus());
    verify(taskRepo, never()).save(any());
  }
}
