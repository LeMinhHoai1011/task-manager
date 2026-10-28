package vn.smarttask.identity;

import java.time.OffsetDateTime;
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
import vn.smarttask.identity.service.IdentityService;
import vn.smarttask.workspace.entity.Workspace;
import vn.smarttask.workspace.entity.WorkspaceMembership;
import vn.smarttask.workspace.repo.WorkspaceMembershipRepository;
import vn.smarttask.workspace.repo.WorkspaceRepository;

/**
 * PH-1: self-registration creates an ACTIVE account with exactly one Personal Workspace.
 * Offline (Mockito) — no Spring context, no database.
 */
@ExtendWith(MockitoExtension.class)
class IdentityServiceRegisterTest {

  @Mock AppUserRepository users;
  @Mock WorkspaceRepository workspaces;
  @Mock WorkspaceMembershipRepository wsMembers;
  @Mock AuditService audit;
  @Mock OutboxService outbox;

  private IdentityService svc() {
    return new IdentityService(users, null, null, null, null,
        workspaces, wsMembers, audit, outbox, true, null, null);
  }

  @Test
  void registerCreatesActiveAccountWithPersonalWorkspace() {
    String username = "alice", email = "alice@example.com", password = "Passw0rd!", displayName = "Alice";
    when(users.findByUsernameIgnoreCase(username)).thenReturn(Optional.empty());
    when(users.findByEmailIgnoreCase(email)).thenReturn(Optional.empty());
    when(workspaces.countByOwnerUserIdAndTypeAndStatusNot(any(), eq("PERSONAL"), eq("DELETED")))
        .thenReturn(0L);
    when(workspaces.save(any())).thenAnswer(i -> i.getArgument(0));
    when(wsMembers.save(any())).thenAnswer(i -> i.getArgument(0));

    AppUser u = svc().register(username, email, password, displayName);

    assertEquals("ACTIVE", u.getStatus());
    assertTrue(u.isActive());
    assertEquals(username, u.getUsername());
    assertEquals(email, u.getEmail());
    verify(workspaces).countByOwnerUserIdAndTypeAndStatusNot(u.getId(), "PERSONAL", "DELETED");
    verify(workspaces).save(any(Workspace.class));
    verify(wsMembers).save(any(WorkspaceMembership.class));
    verify(audit).append(eq(u.getId()), eq("auth.register"), eq("app_user"), eq(u.getId()),
        eq("ALLOWED"), isNull(), any());
  }

  @Test
  void duplicateUsernameIsRejected() {
    when(users.findByUsernameIgnoreCase("bob")).thenReturn(Optional.of(new AppUser(UUID.randomUUID(),
        "bob", "bob@example.com", "hash", "Bob")));

    AppException ex = assertThrows(AppException.class,
        () -> svc().register("bob", "bob2@example.com", "Passw0rd!", "Bob2"));
    assertEquals(409, ex.getHttpStatus().value());
    assertEquals("STATE_CONFLICT", ex.getCode());
    assertEquals("DUPLICATE_USER", ex.getReasonCode());
  }

  @Test
  void duplicateEmailIsRejected() {
    when(users.findByUsernameIgnoreCase("carol")).thenReturn(Optional.empty());
    when(users.findByEmailIgnoreCase("carol@example.com")).thenReturn(Optional.of(new AppUser(
        UUID.randomUUID(), "carol2", "carol@example.com", "hash", "Carol2")));

    AppException ex = assertThrows(AppException.class,
        () -> svc().register("carol", "carol@example.com", "Passw0rd!", "Carol"));
    assertEquals(409, ex.getHttpStatus().value());
    assertEquals("STATE_CONFLICT", ex.getCode());
    assertEquals("DUPLICATE_USER", ex.getReasonCode());
  }

  @Test
  void selfRegistrationDisabledIs403() {
    IdentityService svc = new IdentityService(users, null, null, null, null,
        workspaces, wsMembers, audit, outbox, false, null, null);

    AppException ex = assertThrows(AppException.class,
        () -> svc.register("dave", "dave@example.com", "Passw0rd!", "Dave"));
    assertEquals(403, ex.getHttpStatus().value());
    assertEquals("FORBIDDEN", ex.getCode());
  }
}
