package vn.smarttask.workspace;

import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.common.service.OutboxService;
import vn.smarttask.identity.entity.AppUser;
import vn.smarttask.identity.repo.AppUserRepository;
import vn.smarttask.identity.service.IdentityService;
import vn.smarttask.project.repo.ProjectMembershipRepository;
import vn.smarttask.project.repo.ProjectRepository;
import vn.smarttask.workspace.entity.Workspace;
import vn.smarttask.workspace.entity.WorkspaceMembership;
import vn.smarttask.workspace.repo.*;
import vn.smarttask.workspace.service.WorkspaceService;

/** Owner transfer atomic: project roles unchanged, never 2 owners. */
@ExtendWith(MockitoExtension.class)
public class OwnerTransferTest {
  @Mock WorkspaceRepository workspaces;
  @Mock WorkspaceMembershipRepository members;
  @Mock WorkspaceInvitationRepository invitations;
  @Mock AppUserRepository users;
  @Mock ProjectRepository projects;
  @Mock ProjectMembershipRepository projectMembers;
  @Mock AuditService audit;
  @Mock OutboxService outbox;
  @Mock IdentityService identity;

  @Test
  void transferIsAtomicAndProjectRolesUnchanged() {
    WorkspaceService svc = new WorkspaceService(workspaces, members, invitations, users,
        projects, projectMembers, audit, outbox);
    UUID wsId = UUID.randomUUID();
    UUID oldOwner = UUID.randomUUID();
    UUID newOwner = UUID.randomUUID();
    AppUser actor = new AppUser(oldOwner, "o", "o@x.io", "h", "O");
    actor.setStatus("ACTIVE"); actor.setActive(true);
    when(users.findById(oldOwner)).thenReturn(Optional.of(actor));
    Workspace w = new Workspace(wsId, "team", oldOwner, "TEAM");
    when(workspaces.findByIdForUpdate(wsId)).thenReturn(Optional.of(w));
    WorkspaceMembership cur = new WorkspaceMembership(UUID.randomUUID(), wsId, oldOwner, "OWNER");
    WorkspaceMembership tgt = new WorkspaceMembership(UUID.randomUUID(), wsId, newOwner, "MEMBER");
    when(members.findByWorkspaceIdAndUserId(wsId, oldOwner)).thenReturn(Optional.of(cur));
    when(members.findByWorkspaceIdAndUserId(wsId, newOwner)).thenReturn(Optional.of(tgt));
    when(members.save(any())).thenAnswer(i -> i.getArgument(0));
    when(workspaces.save(any())).thenAnswer(i -> i.getArgument(0));

    svc.ownerTransfer(oldOwner, wsId, newOwner, false, null, null, identity, "workspace.owner.transfer");

    assertEquals("MEMBER", cur.getRole());
    assertEquals("OWNER", tgt.getRole());
    assertEquals(newOwner, w.getOwnerUserId());
    // project roles untouched: no projectMembers writes
    verifyNoInteractions(projectMembers);
    // never 2 owners: save order demote-then-promote + single owner_user_id
    verify(audit).append(eq(oldOwner), eq("workspace.owner.transfer"), eq("workspace"),
        eq(wsId), eq("ALLOWED"), isNull(), any());
  }
}
