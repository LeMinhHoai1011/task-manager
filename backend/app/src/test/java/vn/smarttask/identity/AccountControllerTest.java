package vn.smarttask.identity;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.smarttask.common.AppException;
import vn.smarttask.common.UuidV7;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.identity.entity.AppUser;
import vn.smarttask.identity.entity.UserRefreshToken;
import vn.smarttask.identity.repo.AppUserRepository;
import vn.smarttask.identity.repo.UserRefreshTokenRepository;
import vn.smarttask.identity.repo.UserSessionRepository;
import vn.smarttask.identity.service.IdentityService;
import vn.smarttask.security.CurrentActor;
import vn.smarttask.security.GrantService;

/**
 * Unit tests for AccountController lock/unlock/reset-password (offline, Mockito).
 */
@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

  @Mock IdentityService identity;
  @Mock GrantService grants;
  @Mock AppUserRepository users;
  @Mock UserSessionRepository sessions;
  @Mock UserRefreshTokenRepository refreshTokens;
  @Mock AuditService audit;

  private AccountController controller;

  @BeforeEach
  void setUp() {
    controller = new AccountController(identity, grants, users, audit);
    // Set authenticated actor
    UUID actor = UUID.randomUUID();
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(
            new vn.smarttask.security.Principal(actor, UuidV7.generate()), null, java.util.List.of()));
  }

  @Test
  void lockAccountSucceeds() {
    UUID id = UUID.randomUUID();
    AppUser u = new AppUser(id, "alice", "alice@example.com", "hash", "Alice");
    u.setActive(true);
    u.setStatus("ACTIVE");
    doNothing().when(grants).requireGrant(any(), any(), any(), any(), any());
    doNothing().when(identity).revokeAllSessions(any());
    when(users.findById(id)).thenReturn(Optional.of(u));
    when(users.save(any())).thenAnswer(i -> i.getArgument(0));

    var res = controller.lock(id, null);

    assertTrue(u.isLocked());
    verify(identity).revokeAllSessions(id);
    verify(audit).append(eq(actorId()), eq("account.lock"), eq("app_user"), eq(id),
        eq("ALLOWED"), isNull(), any());
    assertEquals(200, res.getStatusCode().value());
  }

  @Test
  void unlockAccountSucceeds() {
    UUID id = UUID.randomUUID();
    AppUser u = new AppUser(id, "bob", "bob@example.com", "hash", "Bob");
    u.setLocked(true);
    u.setActive(true);
    u.setStatus("ACTIVE");
    doNothing().when(grants).requireGrant(any(), any(), any(), any(), any());
    when(users.findById(id)).thenReturn(Optional.of(u));
    when(users.save(any())).thenAnswer(i -> i.getArgument(0));

    var res = controller.unlock(id);

    assertFalse(u.isLocked());
    assertNull(u.getLockedUntil());
    verify(audit).append(eq(actorId()), eq("account.unlock"), eq("app_user"), eq(id),
        eq("ALLOWED"), isNull(), any());
    assertEquals(200, res.getStatusCode().value());
  }

  @Test
  void resetPasswordSucceeds() {
    UUID id = UUID.randomUUID();
    doNothing().when(grants).requireGrant(any(), any(), any(), any(), any());
    when(identity.resetPasswordTemp(any(), eq(id))).thenReturn("temp123");

    var res = controller.resetPassword(id);

    verify(identity).resetPasswordTemp(eq(actorId()), eq(id));
    verify(audit).append(eq(actorId()), eq("account.reset_password"), eq("app_user"), eq(id),
        eq("ALLOWED"), isNull(), any());
    assertEquals(200, res.getStatusCode().value());
  }

  @Test
  void lockNotFound() {
    when(users.findById(any())).thenReturn(Optional.empty());
    AppException ex = assertThrows(AppException.class, () -> controller.lock(UUID.randomUUID(), null));
    assertEquals(404, ex.getHttpStatus().value());
  }

  private UUID actorId() {
    return ((vn.smarttask.security.Principal) SecurityContextHolder.getContext()
        .getAuthentication().getPrincipal()).userId();
  }
}
