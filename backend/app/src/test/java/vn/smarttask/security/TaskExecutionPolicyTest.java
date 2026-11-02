package vn.smarttask.security;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import vn.smarttask.common.AppException;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.project.entity.Project;
import vn.smarttask.project.entity.ProjectMembership;
import vn.smarttask.project.repo.*;
import vn.smarttask.task.entity.*;
import vn.smarttask.task.repo.*;
import vn.smarttask.workspace.repo.*;

class TaskExecutionPolicyTest {
  @Test
  void assignedMemberExecutesButCannotManageAndGuestCannotExecute() {
    var tasks = mock(TaskRepository.class);
    var projects = mock(ProjectRepository.class);
    var members = mock(ProjectMembershipRepository.class);
    var assignments = mock(TaskAssignmentRepository.class);
    var workspaces = mock(WorkspaceRepository.class);
    var policy = new PolicyEnforcer(new AuthorizationPolicy(), workspaces,
        mock(WorkspaceMembershipRepository.class), projects, members, tasks,
        assignments, mock(AuditService.class));
    UUID actor = UUID.randomUUID(), pid = UUID.randomUUID(), tid = UUID.randomUUID();
    var project = new Project(pid, UUID.randomUUID(), "P", actor);
    project.setStatus("ACTIVE");
    var member = new ProjectMembership(UUID.randomUUID(), pid, actor, "MEMBER");
    var assignment = new TaskAssignment(UUID.randomUUID(), tid, actor);
    when(tasks.findById(tid)).thenReturn(Optional.of(new Task(tid, pid, "T", actor)));
    when(projects.findById(pid)).thenReturn(Optional.of(project));
    when(members.findMembership(pid, actor)).thenReturn(Optional.of(member));
    when(assignments.findByTaskIdAndUserId(tid, actor)).thenReturn(Optional.of(assignment));
    assertDoesNotThrow(() -> policy.requireTaskExecution(actor, tid, "workflow.start"));
    assertDoesNotThrow(() -> policy.requireTaskExecution(actor, tid, "workflow.submit"));
    assertEquals(403, assertThrows(AppException.class,
        () -> policy.requireTaskWrite(actor, tid)).getHttpStatus().value());
    member.setRole("GUEST");
    assertEquals(403, assertThrows(AppException.class,
        () -> policy.requireTaskExecution(actor, tid, "workflow.start")).getHttpStatus().value());
    member.setRole("MEMBER");
    assignment.setStatus("REMOVED");
    assertEquals(404, assertThrows(AppException.class,
        () -> policy.requireTaskExecution(actor, tid, "workflow.start")).getHttpStatus().value());
    assignment.setStatus("ACTIVE");
    member.setStatus("REMOVED");
    assertEquals(404, assertThrows(AppException.class,
        () -> policy.requireTaskExecution(actor, tid, "workflow.start")).getHttpStatus().value());
  }
}
