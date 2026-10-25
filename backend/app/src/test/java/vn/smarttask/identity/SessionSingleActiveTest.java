package vn.smarttask.identity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import vn.smarttask.common.AppException;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.common.service.OutboxService;
import vn.smarttask.identity.entity.AppUser;
import vn.smarttask.identity.entity.UserSession;
import vn.smarttask.identity.repo.*;
import vn.smarttask.identity.service.IdentityService;
import vn.smarttask.workspace.repo.WorkspaceMembershipRepository;
import vn.smarttask.workspace.repo.WorkspaceRepository;

/** PH-1: login moi revoke cu (single-session). Mock repo lock. */
@ExtendWith(MockitoExtension.class)
public class SessionSingleActiveTest {
  @Mock AppUserRepository users;
  @Mock UserSessionRepository sessions;
  @Mock UserRefreshTokenRepository refreshTokens;
  @Mock ReauthTicketRepository reauthTickets;
  @Mock RecoveryCodeRepository recoveryCodes;
  @Mock WorkspaceRepository workspaces;
  @Mock WorkspaceMembershipRepository wsMembers;
  @Mock AuditService audit;
  @Mock OutboxService outbox;

  @Test
  void newLoginRevokesOldSession() {
    IdentityService svc = new IdentityService(users, sessions, refreshTokens, reauthTickets,
        recoveryCodes, workspaces, wsMembers, audit, outbox, true);
    UUID uid = UUID.randomUUID();
    AppUser u = new AppUser(uid, "alice", "a@x.io",
        new BCryptPasswordEncoder().encode("pw123456"), "Alice");
    u.setStatus("ACTIVE"); u.setActive(true);
    when(users.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(u));
    UserSession old = new UserSession(UUID.randomUUID(), uid, "oldhash",
        OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));
    when(sessions.findActiveSessionByUserForUpdate(uid)).thenReturn(Optional.of(old));
    when(sessions.save(any())).thenAnswer(i -> i.getArgument(0));
    when(refreshTokens.save(any())).thenAnswer(i -> i.getArgument(0));

    var r = svc.login("alice", "pw123456", "laptop", "web");

    assertNotNull(r.accessToken());
    assertEquals("REVOKED_LOGIN_ELSEWHERE", old.getStatus());
    assertFalse(old.isActive());
    // http/code assert via no-throw + audit outcome
    verify(audit).append(eq(uid), eq("auth.login"), eq("user_session"), any(), eq("ALLOWED"), isNull(), any());
    verify(audit).append(eq(uid), eq("auth.session.revoked"), eq("user_session"),
        eq(old.getId()), eq("ALLOWED"), eq("REVOKED_LOGIN_ELSEWHERE"), any());
    assertEquals(32, r.accessToken().length()); // UUIDv7 hex opaque
  }

  @Test
  void lockedAccountReturns423() {
    IdentityService svc = new IdentityService(users, sessions, refreshTokens, reauthTickets,
        recoveryCodes, workspaces, wsMembers, audit, outbox, true);
    AppUser u = new AppUser(UUID.randomUUID(), "bob", "b@x.io", "h", "Bob");
    u.setStatus("ACTIVE"); u.setActive(true); u.setLocked(true);
    when(users.findByUsernameIgnoreCase("bob")).thenReturn(Optional.of(u));
    AppException ex = assertThrows(AppException.class, () -> svc.login("bob", "x", null, null));
    assertEquals(423, ex.getHttpStatus().value());
    assertEquals("ACCOUNT_LOCKED", ex.getCode());
    verify(audit).appendDenied(any(), eq("auth.login"), any(), any(), eq("ACCOUNT_LOCKED"));
  }
}
