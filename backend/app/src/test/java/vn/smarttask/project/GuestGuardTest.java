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
import vn.smarttask.workspace.entity.WorkspaceMembership;
import vn.smarttask.workspace.repo.WorkspaceMembershipRepository;
import vn.smarttask.workspace.repo.WorkspaceRepository;

/** WG (workspace guest) lam PM -> 403. */
@ExtendWith(MockitoExtension.class)
public class GuestGuardTest {
  @Mock ProjectRepository projects;
  @Mock ProjectMembershipRepository members;
  @Mock MilestoneRepository milestones;
  @Mock WorkspaceRepository workspaces;
  @Mock WorkspaceMembershipRepository wsMembers;
  @Mock AppUserRepository users;
  @Mock AuditService audit;
  @Mock OutboxService outbox;

  @Test
  void workspaceGuestCannotBeProjectManager() {
    ProjectService svc = new ProjectService(projects, members, milestones, workspaces,
        wsMembers, users, audit, outbox);
    UUID actor = UUID.randomUUID(), target = UUID.randomUUID(),
        pid = UUID.randomUUID(), wsId = UUID.randomUUID();
    AppUser a = new AppUser(actor, "a", "a@x.io", "h", "A");
    a.setStatus("ACTIVE"); a.setActive(true);
    when(users.findById(actor)).thenReturn(Optional.of(a));
    Project p = new Project(pid, wsId, "P", actor);
    when(projects.findById(pid)).thenReturn(Optional.of(p));
    when(members.findMembership(pid, actor))
        .thenReturn(Optional.of(new ProjectMembership(UUID.randomUUID(), pid, actor, "MANAGER")));
    // target is workspace GUEST
    when(wsMembers.findMembership(wsId, target))
        .thenReturn(Optional.of(new WorkspaceMembership(UUID.randomUUID(), wsId, target, "GUEST")));

    AppException ex = assertThrows(AppException.class,
        () -> svc.addMember(actor, pid, target, "MANAGER"));
    assertEquals(403, ex.getHttpStatus().value());
    assertEquals("FORBIDDEN", ex.getCode());
    verify(audit).appendDenied(eq(actor), eq("project.member.add"), eq("project"),
        eq(pid), eq("GUEST_GUARD"));
  }
}
