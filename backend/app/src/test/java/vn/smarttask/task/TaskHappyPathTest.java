package vn.smarttask.task;

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
import vn.smarttask.identity.entity.AppUser;
import vn.smarttask.identity.repo.AppUserRepository;
import vn.smarttask.project.entity.Project;
import vn.smarttask.project.entity.ProjectMembership;
import vn.smarttask.project.repo.MilestoneRepository;
import vn.smarttask.project.repo.ProjectMembershipRepository;
import vn.smarttask.project.repo.ProjectRepository;
import vn.smarttask.task.entity.Task;
import vn.smarttask.task.repo.*;
import vn.smarttask.task.service.TaskService;
import vn.smarttask.workflow.repo.*;
import vn.smarttask.workflow.service.WorkflowService;
import vn.smarttask.workspace.entity.Workspace;
import vn.smarttask.workspace.repo.WorkspaceRepository;

/** Happy path: TK-01 create -> T-01 start -> T-02 submit (approval ON) -> T-04 approve. */
class TaskHappyPathTest {
  TaskRepository tasks; ProjectRepository projects; ProjectMembershipRepository members;
  WorkspaceRepository workspaces; TaskAssignmentRepository assigns; TaskDependencyRepository deps;
  ChecklistItemRepository checks; TimeLogRepository logs; AppUserRepository users;
  ApprovalRoundRepository rounds; ApprovalDesignationRepository designations;
  ApprovalDecisionRepository decisions; WorkflowEventRepository events;
  MilestoneRepository milestones; AuditService audit; OutboxService outbox;

  UUID pm, member, wsId, projectId;

  @BeforeEach
  void setup() {
    tasks = mock(TaskRepository.class); projects = mock(ProjectRepository.class);
    members = mock(ProjectMembershipRepository.class); workspaces = mock(WorkspaceRepository.class);
    assigns = mock(TaskAssignmentRepository.class); deps = mock(TaskDependencyRepository.class);
    checks = mock(ChecklistItemRepository.class); logs = mock(TimeLogRepository.class);
    users = mock(AppUserRepository.class); rounds = mock(ApprovalRoundRepository.class);
    designations = mock(ApprovalDesignationRepository.class); decisions = mock(ApprovalDecisionRepository.class);
    events = mock(WorkflowEventRepository.class); milestones = mock(MilestoneRepository.class);
    audit = mock(AuditService.class); outbox = mock(OutboxService.class);
    pm = UUID.randomUUID(); member = UUID.randomUUID();
    wsId = UUID.randomUUID(); projectId = UUID.randomUUID();
    when(tasks.save(any())).thenAnswer(i -> i.getArgument(0));
    when(rounds.save(any())).thenAnswer(i -> i.getArgument(0));
    when(designations.save(any())).thenAnswer(i -> i.getArgument(0));
    when(decisions.save(any())).thenAnswer(i -> i.getArgument(0));
    when(events.save(any())).thenAnswer(i -> i.getArgument(0));
    when(assigns.save(any())).thenAnswer(i -> i.getArgument(0));

    AppUser u = new AppUser(member, "m", "m@x.io", "h", "M");
    when(users.findById(member)).thenReturn(Optional.of(u));
    AppUser p = new AppUser(pm, "pm", "pm@x.io", "h", "PM");
    when(users.findById(pm)).thenReturn(Optional.of(p));

    Project pr = new Project(projectId, wsId, "P", pm);
    pr.setStatus("ACTIVE");
    pr.setApprovalEnabled(true);
    when(projects.findById(projectId)).thenReturn(Optional.of(pr));
    when(workspaces.findById(wsId)).thenReturn(Optional.of(new Workspace(wsId, "W", pm, "TEAM")));
    when(members.findMembership(projectId, member))
        .thenReturn(Optional.of(new ProjectMembership(UUID.randomUUID(), projectId, member, "MANAGER")));
    when(members.findMembership(projectId, pm))
        .thenReturn(Optional.of(new ProjectMembership(UUID.randomUUID(), projectId, pm, "MANAGER")));
    when(members.findByProjectId(projectId)).thenReturn(List.of(
        new ProjectMembership(UUID.randomUUID(), projectId, pm, "MANAGER")));
  }

  @Test
  void happyPathCreateStartSubmitApprove() {
    TaskService ts = new TaskService(tasks, projects, members, workspaces, assigns, deps,
        checks, logs, users, audit, outbox);
    Task t = ts.create(member, projectId, "Build login", "desc", "HIGH", null);
    assertEquals("TODO", t.getState());
    assertEquals(projectId, t.getProjectId());

    // store task in repo stubs
    when(tasks.findById(t.getId())).thenReturn(Optional.of(t));
    when(tasks.findByIdForUpdate(t.getId())).thenReturn(Optional.of(t));
    when(tasks.findByParentTaskId(t.getId())).thenReturn(List.of());
    when(assigns.findByTaskIdAndStatus(eq(t.getId()), eq("ACTIVE"))).thenReturn(List.of());
    when(rounds.findFirstByTaskIdOrderByRoundNoDesc(t.getId())).thenReturn(Optional.empty());

    WorkflowService ws = new WorkflowService(tasks, projects, members, assigns, rounds,
        designations, decisions, events, milestones, audit, outbox);
    Task started = ws.start(member, t.getId(), t.getVersion());
    assertEquals("IN_PROGRESS", started.getState());

    var sub = ws.submit(member, t.getId(), started.getVersion());
    assertEquals("PENDING_APPROVAL", sub.task().getState());
    assertNotNull(sub.round());
    assertEquals(1, sub.round().getRoundNo());
    assertFalse(sub.noApproverWarning());

    // approve by PM: wire round lookups to the created round
    var round = sub.round();
    when(rounds.findFirstByTaskIdOrderByRoundNoDesc(t.getId())).thenReturn(Optional.of(round));
    when(rounds.findById(round.getId())).thenReturn(Optional.of(round));
    when(rounds.findByIdForUpdate(round.getId())).thenReturn(Optional.of(round));
    when(decisions.findByRoundId(round.getId())).thenReturn(Optional.empty());
    var des = new vn.smarttask.workflow.entity.ApprovalDesignation(
        UUID.randomUUID(), round.getId(), pm, "DEFAULT");
    when(designations.findByRoundIdAndUserId(round.getId(), pm)).thenReturn(Optional.of(des));
    when(assigns.findByTaskIdAndStatus(eq(t.getId()), eq("ACTIVE"))).thenReturn(List.of());

    Task done = ws.approve(pm, t.getId(), sub.task().getVersion());
    assertEquals("COMPLETED", done.getState());
    assertNotNull(done.getCompletedAt());

    verify(audit, atLeast(4)).append(any(), anyString(), anyString(), any(), anyString(), any(), anyString());
    verify(outbox, atLeast(4)).emit(anyString(), any(), anyString(), anyString());
    verify(events, atLeast(3)).save(any());
  }
}
