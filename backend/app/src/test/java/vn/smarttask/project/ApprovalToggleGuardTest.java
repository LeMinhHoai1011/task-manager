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
import vn.smarttask.common.service.AuditService;
import vn.smarttask.common.service.OutboxService;
import vn.smarttask.identity.entity.AppUser;
import vn.smarttask.identity.repo.AppUserRepository;
import vn.smarttask.project.entity.Project;
import vn.smarttask.project.entity.ProjectMembership;
import vn.smarttask.project.repo.*;
import vn.smarttask.project.service.ProjectService;
import vn.smarttask.workspace.repo.WorkspaceMembershipRepository;
import vn.smarttask.workspace.repo.WorkspaceRepository;

@ExtendWith(MockitoExtension.class)
public class ApprovalToggleGuardTest {
  @Mock ProjectRepository projects;
  @Mock ProjectMembershipRepository members;
  @Mock MilestoneRepository milestones;
  @Mock WorkspaceRepository workspaces;
  @Mock WorkspaceMembershipRepository wsMembers;
  @Mock AppUserRepository users;
  @Mock AuditService audit;
  @Mock OutboxService outbox;

  @Test
  void disableWithPendingReturns409() {
    ProjectService svc = new ProjectService(projects, members, milestones, workspaces,
        wsMembers, users, audit, outbox);
    svc.setPendingCounter(pid -> 2L);
    UUID actor = UUID.randomUUID(), pid = UUID.randomUUID();
    AppUser a = new AppUser(actor, "a", "a@x.io", "h", "A");
    a.setStatus("ACTIVE"); a.setActive(true);
    when(users.findById(actor)).thenReturn(Optional.of(a));
    Project p = new Project(pid, UUID.randomUUID(), "P", actor);
    when(projects.findByIdForUpdate(pid)).thenReturn(Optional.of(p));
    when(members.findMembership(pid, actor))
        .thenReturn(Optional.of(new ProjectMembership(UUID.randomUUID(), pid, actor, "MANAGER")));

    AppException ex = assertThrows(AppException.class,
        () -> svc.approvalToggle(actor, pid, false, p.getVersion()));
    assertEquals(409, ex.getHttpStatus().value());
    assertEquals("STATE_CONFLICT", ex.getCode());
    assertEquals("PENDING_APPROVALS_EXIST", ex.getReasonCode());
    verify(audit).appendDenied(eq(actor), eq("project.approval.toggle"), eq("project"),
        eq(pid), eq("PENDING_APPROVALS_EXIST"));
  }
}
