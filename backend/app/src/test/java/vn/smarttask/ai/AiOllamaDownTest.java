package vn.smarttask.ai;

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

/** Ollama outage -> FAILED_OLLAMA (retryable) while core Task create/update/approve still work. */
class AiOllamaDownTest {

  @Test
  void ollamaDownFailsJobButCoreTasksStillWork() {
    AiFixture f = new AiFixture(MockOllamaClient.Mode.DOWN);
    var job = f.requestOk();
    assertEquals("FAILED_OLLAMA", job.getStatus());

    // Retryable while attempt < maxAttempts: flip back to VALID, retry succeeds.
    f.ollama.setMode(MockOllamaClient.Mode.VALID);
    var retried = f.service.retry(f.user, job.getId());
    assertEquals("COMPLETED", retried.getStatus());
    assertEquals(2, retried.getAttempt());
    assertEquals(2, f.ollama.calls());

    // Core TaskService (real instance, mocked repos) unaffected by the earlier outage.
    TaskRepository tasks = mock(TaskRepository.class);
    ProjectRepository projects = mock(ProjectRepository.class);
    ProjectMembershipRepository members = mock(ProjectMembershipRepository.class);
    WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
    TaskAssignmentRepository assigns = mock(TaskAssignmentRepository.class);
    TaskDependencyRepository deps = mock(TaskDependencyRepository.class);
    ChecklistItemRepository checks = mock(ChecklistItemRepository.class);
    TimeLogRepository logs = mock(TimeLogRepository.class);
    AppUserRepository users = mock(AppUserRepository.class);
    AuditService audit = mock(AuditService.class);
    OutboxService outbox = mock(OutboxService.class);
    ApprovalRoundRepository rounds = mock(ApprovalRoundRepository.class);
    ApprovalDesignationRepository designations = mock(ApprovalDesignationRepository.class);
    ApprovalDecisionRepository decisions = mock(ApprovalDecisionRepository.class);
    WorkflowEventRepository events = mock(WorkflowEventRepository.class);
    MilestoneRepository milestones = mock(MilestoneRepository.class);

    UUID pm = UUID.randomUUID(), member = UUID.randomUUID();
    UUID wsId = UUID.randomUUID(), projectId = UUID.randomUUID();
    when(tasks.save(any())).thenAnswer(i -> i.getArgument(0));
    when(rounds.save(any())).thenAnswer(i -> i.getArgument(0));
    when(designations.save(any())).thenAnswer(i -> i.getArgument(0));
    when(decisions.save(any())).thenAnswer(i -> i.getArgument(0));
    when(events.save(any())).thenAnswer(i -> i.getArgument(0));
    when(assigns.save(any())).thenAnswer(i -> i.getArgument(0));
    when(users.findById(member)).thenReturn(Optional.of(new AppUser(member, "m", "m@x.io", "h", "M")));
    when(users.findById(pm)).thenReturn(Optional.of(new AppUser(pm, "pm", "pm@x.io", "h", "PM")));
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

    TaskService ts = new TaskService(tasks, projects, members, workspaces, assigns, deps,
        checks, logs, users, audit, outbox);
    Task t = ts.create(member, projectId, "Core still works", "desc", "HIGH", null);
    assertEquals("TODO", t.getState());
    when(tasks.findById(t.getId())).thenReturn(Optional.of(t));
    when(tasks.findByIdForUpdate(t.getId())).thenReturn(Optional.of(t));
    when(tasks.findByParentTaskId(t.getId())).thenReturn(List.of());
    when(assigns.findByTaskIdAndStatus(eq(t.getId()), eq("ACTIVE"))).thenReturn(List.of());
    when(rounds.findFirstByTaskIdOrderByRoundNoDesc(t.getId())).thenReturn(Optional.empty());

    Task updated = ts.updateContent(member, t.getId(), "Core still works v2", null, t.getVersion());
    assertNotNull(updated);

    WorkflowService ws = new WorkflowService(tasks, projects, members, assigns, rounds,
        designations, decisions, events, milestones, audit, outbox);
    assertEquals("IN_PROGRESS", ws.start(member, t.getId(), updated.getVersion()).getState());
  }

  @Test
  void timeoutFailsWithTimeoutStatus() {
    AiFixture f = new AiFixture(MockOllamaClient.Mode.TIMEOUT);
    var job = f.requestOk();
    assertEquals("FAILED_TIMEOUT", job.getStatus());
    // Retry allowed (attempt 1 < 3) but still timing out.
    var again = f.service.retry(f.user, job.getId());
    assertEquals("FAILED_TIMEOUT", again.getStatus());
    assertEquals(2, again.getAttempt());
  }

  @Test
  void exhaustedAttemptsCannotRetry() {
    AiFixture f = new AiFixture(MockOllamaClient.Mode.DOWN);
    var job = f.requestOk();
    f.service.retry(f.user, job.getId()); // attempt 2
    var third = f.service.retry(f.user, job.getId()); // attempt 3
    assertEquals("FAILED_OLLAMA", third.getStatus());
    assertEquals(3, third.getAttempt());
    AppException denied =
        assertThrows(AppException.class, () -> f.service.retry(f.user, job.getId()));
    assertEquals(409, denied.getHttpStatus().value());
    assertEquals(3, f.ollama.calls());
  }
}
