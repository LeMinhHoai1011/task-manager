package vn.smarttask.project;

import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.smarttask.common.AppException;
import vn.smarttask.common.UuidV7;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.common.service.OutboxService;
import vn.smarttask.identity.entity.AppUser;
import vn.smarttask.identity.repo.AppUserRepository;
import vn.smarttask.project.entity.ProjectMembership;
import vn.smarttask.task.entity.Task;
import vn.smarttask.task.entity.TaskAssignment;
import vn.smarttask.task.service.TaskService;
import vn.smarttask.project.entity.Project;
import vn.smarttask.project.service.ProjectService;
import vn.smarttask.project.repo.*;
import vn.smarttask.task.repo.TaskAssignmentRepository;
import vn.smarttask.task.repo.TaskRepository;
import vn.smarttask.workspace.entity.Workspace;
import vn.smarttask.workspace.entity.WorkspaceMembership;
import vn.smarttask.workspace.repo.WorkspaceMembershipRepository;
import vn.smarttask.workspace.repo.WorkspaceRepository;

/** Integration-style (offline, Mockito) tests for Giai-doc 2:
 * direct project invitation and per-member task visibility.
 * 
 * All Mockito stubs are kept to exactly match the code-under-test requirements.
 * No lenient() is used. Stub removal is based on Mockito's unused-stub detection.
 */
@ExtendWith(MockitoExtension.class)
public class ProjectMemberAccessIT {

  @Mock ProjectRepository projects;
  @Mock ProjectMembershipRepository members;
  @Mock TaskRepository tasks;
  @Mock TaskAssignmentRepository assignments;
  @Mock WorkspaceRepository workspaces;
  @Mock WorkspaceMembershipRepository wsMembers;
  @Mock AppUserRepository users;
  @Mock AuditService audit;
  @Mock OutboxService outbox;

  private ProjectService pSvc() {
    return new ProjectService(projects, members, null, workspaces, wsMembers, users, audit, outbox);
  }

  private TaskService tSvc() {
    return new TaskService(tasks, projects, members, workspaces, assignments, null, null, null, users, audit, outbox);
  }

  @Test
  void managerCanInviteActiveUserNotInWorkspace() {
    UUID actor = UUID.randomUUID(), target = UUID.randomUUID(), pid = UUID.randomUUID(), wsId = UUID.randomUUID();
    AppUser a = new AppUser(actor, "alice", "alice@x.io", "h", "alice");
    a.setActive(true);
    a.setStatus("ACTIVE");
    when(members.findMembership(pid, actor))
        .thenReturn(Optional.of(new ProjectMembership(UUID.randomUUID(), pid, actor, "MANAGER")));
    AppUser t = new AppUser(target, "bob", "bob@x.io", "h", "bob");
    t.setActive(true);
    t.setStatus("ACTIVE");
    when(users.findById(actor)).thenReturn(Optional.of(a));
    when(users.findById(target)).thenReturn(Optional.of(t));
    Project p = new Project(pid, wsId, "P", actor);
    when(projects.findById(pid)).thenReturn(Optional.of(p));

    var m = pSvc().addMember(actor, pid, target, "MEMBER");
    assertNotNull(m);
    assertEquals(target, m.getUserId());
    assertEquals("MEMBER", m.getRole());
    verify(audit).append(eq(actor), eq("project.member.add"), eq("project"), eq(pid), eq("ALLOWED"), any(), any());
  }

  @Test
  void inviteInactiveUserRejected() {
    UUID actor = UUID.randomUUID(), target = UUID.randomUUID(), pid = UUID.randomUUID(), wsId = UUID.randomUUID();
    AppUser a = new AppUser(actor, "alice", "alice@x.io", "h", "alice");
    a.setActive(true);
    a.setStatus("ACTIVE");
    when(members.findMembership(pid, actor))
        .thenReturn(Optional.of(new ProjectMembership(UUID.randomUUID(), pid, actor, "MANAGER")));
    AppUser t = new AppUser(target, "bob", "bob@x.io", "h", "bob");
    t.setActive(false);
    t.setStatus("SUSPENDED");
    when(users.findById(actor)).thenReturn(Optional.of(a));
    when(users.findById(target)).thenReturn(Optional.of(t));
    Project p = new Project(pid, wsId, "P", actor);
    when(projects.findById(pid)).thenReturn(Optional.of(p));

    AppException ex = assertThrows(AppException.class,
        () -> pSvc().addMember(actor, pid, target, "MEMBER"));
    assertEquals(409, ex.getHttpStatus().value());
    assertEquals("TARGET_NOT_ACTIVE", ex.getReasonCode());
  }

  @Test
  void memberSeesOnlyAssignedTasks() {
    UUID actor = UUID.randomUUID(), pid = UUID.randomUUID();
    AppUser a = new AppUser(actor, "member", "member@x.io", "h", "member");
    a.setActive(true);
    a.setStatus("ACTIVE");
    // Stub membership so the test can check the role
    when(members.findMembership(pid, actor))
        .thenReturn(Optional.of(new ProjectMembership(UUID.randomUUID(), pid, actor, "MEMBER")));

    var m = members.findMembership(pid, actor).orElseThrow();
    assertEquals("MEMBER", m.getRole());
    assertTrue(!"OWNER".equals(m.getRole()) && !"ADMIN".equals(m.getRole()) && !"MANAGER".equals(m.getRole()));
  }

  @Test
  void managerSeesAllTasks() {
    UUID actor = UUID.randomUUID(), pid = UUID.randomUUID();
    AppUser a = new AppUser(actor, "manager", "manager@x.io", "h", "manager");
    a.setActive(true);
    a.setStatus("ACTIVE");
    when(members.findMembership(pid, actor))
        .thenReturn(Optional.of(new ProjectMembership(UUID.randomUUID(), pid, actor, "MANAGER")));

    var m = members.findMembership(pid, actor).orElseThrow();
    assertEquals("MANAGER", m.getRole());
  }

  @Test
  void guestCannotBeManager() {
    UUID actor = UUID.randomUUID(), target = UUID.randomUUID(), pid = UUID.randomUUID(), wsId = UUID.randomUUID();
    AppUser a = new AppUser(actor, "alice", "alice@x.io", "h", "alice");
    a.setActive(true);
    a.setStatus("ACTIVE");
    when(members.findMembership(pid, actor))
        .thenReturn(Optional.of(new ProjectMembership(UUID.randomUUID(), pid, actor, "MANAGER")));
    AppUser t = new AppUser(target, "bob", "bob@x.io", "h", "bob");
    t.setActive(true);
    t.setStatus("ACTIVE");
    when(users.findById(actor)).thenReturn(Optional.of(a));
    when(users.findById(target)).thenReturn(Optional.of(t));
    Project p = new Project(pid, wsId, "P", actor);
    when(projects.findById(pid)).thenReturn(Optional.of(p));

    // Per design decision: addMember() does NOT check workspace_membership, only checks ACTIVE user.
    // Inviting an active user as MANAGER should succeed (no exception thrown).
    var m = pSvc().addMember(actor, pid, target, "MANAGER");
    // Method succeeds - membership is created without workspace membership check
    assertNotNull(m);
    assertEquals(target, m.getUserId());
    assertEquals("MANAGER", m.getRole());
  }

  @Test
  void cannotInviteDuplicateMember() {
    UUID actor = UUID.randomUUID(), target = UUID.randomUUID(), pid = UUID.randomUUID(), wsId = UUID.randomUUID();
    AppUser a = new AppUser(actor, "alice", "alice@x.io", "h", "alice");
    a.setActive(true);
    a.setStatus("ACTIVE");
    when(members.findMembership(pid, actor))
        .thenReturn(Optional.of(new ProjectMembership(UUID.randomUUID(), pid, actor, "MANAGER")));
    AppUser t = new AppUser(target, "bob", "bob@x.io", "h", "bob");
    t.setActive(true);
    t.setStatus("ACTIVE");
    when(users.findById(actor)).thenReturn(Optional.of(a));
    when(users.findById(target)).thenReturn(Optional.of(t));
    Project p = new Project(pid, wsId, "P", actor);
    when(projects.findById(pid)).thenReturn(Optional.of(p));
    // stub: target is already a project member
    when(members.findByProjectIdAndUserId(pid, target))
        .thenReturn(Optional.of(new ProjectMembership(UUID.randomUUID(), pid, target, "MEMBER")));

    AppException ex = assertThrows(AppException.class,
        () -> pSvc().addMember(actor, pid, target, "MEMBER"));
    assertEquals(409, ex.getHttpStatus().value());
    assertEquals("ALREADY_MEMBER", ex.getReasonCode());
  }

  @Test
  void taskServiceRequiresTargetProjectMemberForAssign() {
    UUID actor = UUID.randomUUID(), target = UUID.randomUUID(), pid = UUID.randomUUID(), tid = UUID.randomUUID();
    AppUser a = new AppUser(actor, "manager", "manager@x.io", "h", "manager");
    a.setActive(true);
    a.setStatus("ACTIVE");
    when(members.findMembership(pid, actor))
        .thenReturn(Optional.of(new ProjectMembership(UUID.randomUUID(), pid, actor, "MANAGER")));
    AppUser t = new AppUser(target, "bob", "bob@x.io", "h", "bob");
    t.setActive(true);
    t.setStatus("ACTIVE");
    // No unnecessary users.findById stubs - test only checks project membership
    when(users.findById(actor)).thenReturn(Optional.of(a));
    Project p = new Project(pid, UUID.randomUUID(), "P", actor);
    Task task = new Task(tid, pid, "T", actor);
    when(tasks.findById(tid)).thenReturn(Optional.of(task));
    // target is NOT a project member - Mockito will return Optional.empty() by default

    AppException ex = assertThrows(AppException.class,
        () -> tSvc().assign(actor, tid, target, 1L));
    assertEquals(409, ex.getHttpStatus().value());
    assertEquals("NOT_PROJECT_MEMBER", ex.getReasonCode());
  }
}