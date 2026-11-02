package vn.smarttask.security;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.verify;
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
 * F0 PolicyEnforcer (offline, Mockito): missing READ -&gt; 404 + DENIED_NOT_FOUND
 * audit; visible resource without write -&gt; 403 + DENIED_FORBIDDEN audit.
 */
@ExtendWith(MockitoExtension.class)
class Policy404vs403Test {

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

  private Project project(UUID pid, UUID wsId) {
    Project p = new Project(pid, wsId, "p", UUID.randomUUID());
    p.setStatus("PLANNING");
    return p;
  }

  private Workspace workspace(UUID wsId) {
    Workspace w = new Workspace(wsId, "w", UUID.randomUUID(), "TEAM");
    w.setStatus("ACTIVE");
    return w;
  }

  @Test
  void outsiderTaskReadIs404WithDeniedNotFoundAudit() {
    UUID actor = UUID.randomUUID(), tid = UuidV7.generate();
    UUID pid = UuidV7.generate(), wsId = UuidV7.generate();
    when(tasks.findById(tid))
        .thenReturn(Optional.of(new Task(tid, pid, "t", UUID.randomUUID())));
    when(projects.findById(pid)).thenReturn(Optional.of(project(pid, wsId)));
    when(projectMembers.findMembership(pid, actor)).thenReturn(Optional.empty());
    when(assignments.findByTaskIdAndUserId(tid, actor)).thenReturn(Optional.empty());

    AppException ex = assertThrows(AppException.class,
        () -> enforcer().requireTaskRead(actor, tid));
    assertEquals(404, ex.getHttpStatus().value());
    assertEquals("RESOURCE_NOT_FOUND", ex.getCode());
    verify(audit).appendDenied(actor, "task.read", "task", tid, "DENIED_NOT_FOUND");
  }

  @Test
  void viewerTaskWriteIs403WithDeniedForbiddenAudit() {
    UUID actor = UUID.randomUUID(), tid = UuidV7.generate();
    UUID pid = UuidV7.generate(), wsId = UuidV7.generate();
    when(tasks.findById(tid))
        .thenReturn(Optional.of(new Task(tid, pid, "t", UUID.randomUUID())));
    when(projects.findById(pid)).thenReturn(Optional.of(project(pid, wsId)));
    when(workspaces.findById(wsId)).thenReturn(Optional.of(workspace(wsId)));
    when(projectMembers.findMembership(pid, actor)).thenReturn(Optional.of(
        new ProjectMembership(UuidV7.generate(), pid, actor, "VIEWER")));
    when(assignments.findByTaskIdAndUserId(tid, actor)).thenReturn(Optional.of(
        new vn.smarttask.task.entity.TaskAssignment(UuidV7.generate(), tid, actor)));

    AppException ex = assertThrows(AppException.class,
        () -> enforcer().requireTaskWrite(actor, tid));
    assertEquals(403, ex.getHttpStatus().value());
    assertEquals("FORBIDDEN", ex.getCode());
    verify(audit).appendDenied(actor, "task.write", "task", tid, "DENIED_FORBIDDEN");
  }

  @Test
  void assignedMemberReadsButCannotManageTask() {
    UUID actor = UUID.randomUUID(), tid = UuidV7.generate();
    UUID pid = UuidV7.generate(), wsId = UuidV7.generate();
    when(tasks.findById(tid))
        .thenReturn(Optional.of(new Task(tid, pid, "t", UUID.randomUUID())));
    when(projects.findById(pid)).thenReturn(Optional.of(project(pid, wsId)));
    when(workspaces.findById(wsId)).thenReturn(Optional.of(workspace(wsId)));
    when(projectMembers.findMembership(pid, actor)).thenReturn(Optional.of(
        new ProjectMembership(UuidV7.generate(), pid, actor, "MEMBER")));
    when(assignments.findByTaskIdAndUserId(tid, actor)).thenReturn(Optional.of(
        new vn.smarttask.task.entity.TaskAssignment(UuidV7.generate(), tid, actor)));

    assertDoesNotThrow(() -> enforcer().requireTaskRead(actor, tid));
    assertEquals(403, assertThrows(AppException.class,
        () -> enforcer().requireTaskWrite(actor, tid)).getHttpStatus().value());
  }

  @Test
  void outsiderWorkspaceReadIs404() {
    UUID actor = UUID.randomUUID(), wsId = UuidV7.generate();
    when(workspaces.findById(wsId)).thenReturn(Optional.of(workspace(wsId)));
    when(wsMembers.findMembership(wsId, actor)).thenReturn(Optional.empty());

    AppException ex = assertThrows(AppException.class,
        () -> enforcer().requireWorkspaceRead(actor, wsId));
    assertEquals(404, ex.getHttpStatus().value());
    verify(audit).appendDenied(actor, "workspace.read", "workspace", wsId, "DENIED_NOT_FOUND");
  }

  @Test
  void workspaceViewerWriteIs403() {
    UUID actor = UUID.randomUUID(), wsId = UuidV7.generate();
    when(workspaces.findById(wsId)).thenReturn(Optional.of(workspace(wsId)));
    when(wsMembers.findMembership(wsId, actor)).thenReturn(Optional.of(
        new WorkspaceMembership(UuidV7.generate(), wsId, actor, "VIEWER")));

    AppException ex = assertThrows(AppException.class,
        () -> enforcer().requireWorkspaceWrite(actor, wsId));
    assertEquals(403, ex.getHttpStatus().value());
    verify(audit).appendDenied(actor, "workspace.write", "workspace", wsId, "DENIED_FORBIDDEN");
  }

  @Test
  void missingResourceIs404() {
    UUID actor = UUID.randomUUID(), pid = UuidV7.generate();
    when(projects.findById(pid)).thenReturn(Optional.empty());
    AppException ex = assertThrows(AppException.class,
        () -> enforcer().requireProjectRead(actor, pid));
    assertEquals(404, ex.getHttpStatus().value());
    assertEquals("RESOURCE_NOT_FOUND", ex.getCode());
    verify(audit, org.mockito.Mockito.never()).appendDenied(any(), any(), any(), any(), any());
  }
}
