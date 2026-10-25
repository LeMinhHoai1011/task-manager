package vn.smarttask.project;

import java.util.List;
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
import vn.smarttask.workspace.entity.WorkspaceMembership;
import vn.smarttask.workspace.repo.WorkspaceMembershipRepository;
import vn.smarttask.workspace.repo.WorkspaceRepository;

@ExtendWith(MockitoExtension.class)
public class LastManagerGuardTest {
  @Mock ProjectRepository projects;
  @Mock ProjectMembershipRepository members;
  @Mock MilestoneRepository milestones;
  @Mock WorkspaceRepository workspaces;
  @Mock WorkspaceMembershipRepository wsMembers;
  @Mock AppUserRepository users;
  @Mock AuditService audit;
  @Mock OutboxService outbox;

  private ProjectService svc() {
    return new ProjectService(projects, members, milestones, workspaces, wsMembers, users, audit, outbox);
  }

  @Test
  void removeLastManagerReturns409AndAuditDenied() {
    UUID actor = UUID.randomUUID(), target = UUID.randomUUID(), pid = UUID.randomUUID();
    AppUser a = new AppUser(actor, "a", "a@x.io", "h", "A");
    a.setStatus("ACTIVE"); a.setActive(true);
    when(users.findById(actor)).thenReturn(Optional.of(a));
    ProjectMembership am = new ProjectMembership(UUID.randomUUID(), pid, actor, "MANAGER");
    when(members.findMembership(pid, actor)).thenReturn(Optional.of(am));
    ProjectMembership tm = new ProjectMembership(UUID.randomUUID(), pid, target, "MANAGER");
    when(members.findByProjectIdAndUserId(pid, target)).thenReturn(Optional.of(tm));
    when(members.countActiveManager(pid)).thenReturn(1L);

    AppException ex = assertThrows(AppException.class, () -> svc().removeMember(actor, pid, target));
    assertEquals(409, ex.getHttpStatus().value());
    assertEquals("LAST_PROJECT_MANAGER", ex.getCode());
    verify(audit).appendDenied(eq(actor), eq("project.member.remove"), eq("project"),
        eq(pid), eq("LAST_PROJECT_MANAGER"));
  }

  @Test
  void demoteLastManagerReturns409() {
    UUID actor = UUID.randomUUID(), target = UUID.randomUUID(), pid = UUID.randomUUID();
    AppUser a = new AppUser(actor, "a", "a@x.io", "h", "A");
    a.setStatus("ACTIVE"); a.setActive(true);
    when(users.findById(actor)).thenReturn(Optional.of(a));
    when(members.findMembership(pid, actor))
        .thenReturn(Optional.of(new ProjectMembership(UUID.randomUUID(), pid, actor, "MANAGER")));
    when(members.findByProjectIdAndUserId(pid, target))
        .thenReturn(Optional.of(new ProjectMembership(UUID.randomUUID(), pid, target, "MANAGER")));
    when(members.countActiveManager(pid)).thenReturn(1L);
    AppException ex = assertThrows(AppException.class,
        () -> svc().roleChange(actor, pid, target, "MEMBER"));
    assertEquals(409, ex.getHttpStatus().value());
    assertEquals("LAST_PROJECT_MANAGER", ex.getCode());
  }
}
