package vn.smarttask.security;

import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.when;
import vn.smarttask.common.AppException;
import vn.smarttask.common.UuidV7;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.project.entity.Project;
import vn.smarttask.project.entity.ProjectMembership;
import vn.smarttask.project.repo.ProjectMembershipRepository;
import vn.smarttask.project.repo.ProjectRepository;
import vn.smarttask.task.entity.Task;
import vn.smarttask.task.repo.TaskAssignmentRepository;
import vn.smarttask.task.repo.TaskRepository;
import vn.smarttask.workspace.entity.Workspace;
import vn.smarttask.workspace.entity.WorkspaceMembership;
import vn.smarttask.workspace.repo.WorkspaceMembershipRepository;
import vn.smarttask.workspace.repo.WorkspaceRepository;

/**
 * GATE POLICY-01 (offline unit): DELETED scope hides existence (404 +
 * DENIED_NOT_FOUND) for readers AND writers at controller entry.
 */
@ExtendWith(MockitoExtension.class)
class PolicyDeletedScopeTest {

  @Mock WorkspaceRepository workspaces;
  @Mock WorkspaceMembershipRepository wsMembers;
  @Mock ProjectRepository projects;
  @Mock ProjectMembershipRepository projectMembers;
  @Mock TaskRepository tasks;
  @Mock TaskAssignmentRepository assignments;
  @Mock AuditService audit;

  private PolicyEnforcer enforcer() {
    return new PolicyEnforcer(new AuthorizationPolicy(), workspaces, wsMembers, projects,
        projectMembers, tasks, assignments, audit);
  }

  private Workspace workspace(UUID id, String status) {
    Workspace w = new Workspace(id, "w", UUID.randomUUID(), "TEAM");
    w.setStatus(status);
    return w;
  }

  private Project project(UUID pid, UUID wsId, String status) {
    Project p = new Project(pid, wsId, "p", UUID.randomUUID());
    p.setStatus(status);
    return p;
  }

  @Test
  void deletedWorkspaceReadIs404EvenForMember() {
    UUID actor = UUID.randomUUID(), wsId = UuidV7.generate();
    when(workspaces.findById(wsId)).thenReturn(Optional.of(workspace(wsId, "DELETED")));
    AppException ex = assertThrows(AppException.class,
        () -> enforcer().requireWorkspaceRead(actor, wsId));
    assertEquals(404, ex.getHttpStatus().value());
    assertEquals("RESOURCE_NOT_FOUND", ex.getCode());
  }

  @Test
  void deletedWorkspaceWriteIs404Not403() {
    UUID actor = UUID.randomUUID(), wsId = UuidV7.generate();
    when(workspaces.findById(wsId)).thenReturn(Optional.of(workspace(wsId, "DELETED")));
    AppException ex = assertThrows(AppException.class,
        () -> enforcer().requireWorkspaceWrite(actor, wsId));
    assertEquals(404, ex.getHttpStatus().value());
  }

  @Test
  void deletedProjectReadIs404EvenForManager() {
    UUID actor = UUID.randomUUID(), pid = UuidV7.generate(), wsId = UuidV7.generate();
    when(projects.findById(pid)).thenReturn(Optional.of(project(pid, wsId, "DELETED")));
    AppException ex = assertThrows(AppException.class,
        () -> enforcer().requireProjectRead(actor, pid));
    assertEquals(404, ex.getHttpStatus().value());
  }

  @Test
  void taskInDeletedProjectIs404() {
    UUID actor = UUID.randomUUID(), tid = UuidV7.generate();
    UUID pid = UuidV7.generate(), wsId = UuidV7.generate();
    when(tasks.findById(tid))
        .thenReturn(Optional.of(new Task(tid, pid, "t", UUID.randomUUID())));
    when(projects.findById(pid)).thenReturn(Optional.of(project(pid, wsId, "DELETED")));
    AppException ex = assertThrows(AppException.class,
        () -> enforcer().requireTaskRead(actor, tid));
    assertEquals(404, ex.getHttpStatus().value());
  }

  @Test
  void archivedProjectStillReadable() {
    UUID actor = UUID.randomUUID(), pid = UuidV7.generate(), wsId = UuidV7.generate();
    when(projects.findById(pid)).thenReturn(Optional.of(project(pid, wsId, "ARCHIVED")));
    when(workspaces.findById(wsId)).thenReturn(Optional.of(workspace(wsId, "ACTIVE")));
    when(projectMembers.findMembership(pid, actor)).thenReturn(
        Optional.of(new ProjectMembership(UuidV7.generate(), pid, actor, "MEMBER")));
    assertDoesNotThrow(() -> enforcer().requireProjectRead(actor, pid));
  }

  @Test
  void workspaceOwnerWithoutProjectMembershipGets404() {
    // POLICY-01: WO has no implicit project-content read.
    UUID wo = UUID.randomUUID(), pid = UuidV7.generate(), wsId = UuidV7.generate();
    when(projects.findById(pid)).thenReturn(Optional.of(project(pid, wsId, "ACTIVE")));
    when(projectMembers.findMembership(pid, wo)).thenReturn(Optional.empty());
    AppException ex = assertThrows(AppException.class,
        () -> enforcer().requireProjectRead(wo, pid));
    assertEquals(404, ex.getHttpStatus().value());
  }

  @Test
  void projectGuestWriteIs403() {
    // POLICY-01: Guest is read-only.
    UUID g = UUID.randomUUID(), pid = UuidV7.generate(), wsId = UuidV7.generate();
    when(projects.findById(pid)).thenReturn(Optional.of(project(pid, wsId, "ACTIVE")));
    when(workspaces.findById(wsId)).thenReturn(Optional.of(workspace(wsId, "ACTIVE")));
    when(projectMembers.findMembership(pid, g)).thenReturn(
        Optional.of(new ProjectMembership(UuidV7.generate(), pid, g, "GUEST")));
    AppException ex = assertThrows(AppException.class,
        () -> enforcer().requireProjectWrite(g, pid));
    assertEquals(403, ex.getHttpStatus().value());
  }
}
