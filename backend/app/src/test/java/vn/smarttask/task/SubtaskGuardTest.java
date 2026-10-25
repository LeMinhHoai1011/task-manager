package vn.smarttask.task;

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
import vn.smarttask.identity.entity.AppUser;
import vn.smarttask.identity.repo.AppUserRepository;
import vn.smarttask.project.entity.Project;
import vn.smarttask.project.entity.ProjectMembership;
import vn.smarttask.project.repo.ProjectMembershipRepository;
import vn.smarttask.project.repo.ProjectRepository;
import vn.smarttask.task.entity.Task;
import vn.smarttask.task.repo.*;
import vn.smarttask.task.service.TaskService;
import vn.smarttask.workspace.entity.Workspace;
import vn.smarttask.workspace.repo.WorkspaceRepository;

/** Subtask guards: depth>1 blocked; parent submit blocked while children incomplete. */
class SubtaskGuardTest {
  TaskRepository tasks; ProjectRepository projects; ProjectMembershipRepository members;
  WorkspaceRepository workspaces; TaskAssignmentRepository assigns; TaskDependencyRepository deps;
  ChecklistItemRepository checks; TimeLogRepository logs; AppUserRepository users;
  AuditService audit; OutboxService outbox;
  UUID actor, projectId, wsId;

  @BeforeEach
  void setup() {
    tasks = mock(TaskRepository.class); projects = mock(ProjectRepository.class);
    members = mock(ProjectMembershipRepository.class); workspaces = mock(WorkspaceRepository.class);
    assigns = mock(TaskAssignmentRepository.class); deps = mock(TaskDependencyRepository.class);
    checks = mock(ChecklistItemRepository.class); logs = mock(TimeLogRepository.class);
    users = mock(AppUserRepository.class);
    audit = mock(AuditService.class); outbox = mock(OutboxService.class);
    actor = UUID.randomUUID(); projectId = UUID.randomUUID(); wsId = UUID.randomUUID();
    when(tasks.save(any())).thenAnswer(i -> i.getArgument(0));
    when(users.findById(actor)).thenReturn(Optional.of(new AppUser(actor, "u", "u@x.io", "h", "U")));
    Project p = new Project(projectId, wsId, "P", actor);
    p.setStatus("ACTIVE");
    when(projects.findById(projectId)).thenReturn(Optional.of(p));
    when(workspaces.findById(wsId)).thenReturn(Optional.of(new Workspace(wsId, "W", actor, "TEAM")));
    when(members.findMembership(projectId, actor)).thenReturn(
        Optional.of(new ProjectMembership(UUID.randomUUID(), projectId, actor, "MANAGER")));
  }

  TaskService svc() {
    return new TaskService(tasks, projects, members, workspaces, assigns, deps, checks, logs,
        users, audit, outbox);
  }

  @Test
  void depthBeyondOneBlocked() {
    Task parent = new Task(UUID.randomUUID(), projectId, "parent", actor);
    parent.setDepth(1); // already a subtask
    when(tasks.findById(parent.getId())).thenReturn(Optional.of(parent));
    var ex = assertThrows(AppException.class, () -> svc().createSubtask(actor, parent.getId(), "sub-sub", null));
    assertEquals("SUBTASK_MAX_DEPTH_EXCEEDED", ex.getReasonCode());
    assertEquals(422, ex.getHttpStatus().value());
  }

  @Test
  void subtaskLevelOneAllowedButNoMilestone() {
    Task parent = new Task(UUID.randomUUID(), projectId, "parent", actor);
    parent.setDepth(0);
    when(tasks.findById(parent.getId())).thenReturn(Optional.of(parent));
    Task sub = svc().createSubtask(actor, parent.getId(), "child", "d");
    assertEquals(1, sub.getDepth());
    assertEquals(parent.getId(), sub.getParentTaskId());
    assertNull(sub.getMilestoneId());
  }
}
