package vn.smarttask.identity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.context.ApplicationEventPublisher;
import vn.smarttask.common.AppException;
import vn.smarttask.common.UuidV7;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.common.service.OutboxService;
import vn.smarttask.identity.entity.AppUser;
import vn.smarttask.identity.entity.UserRefreshToken;
import vn.smarttask.identity.entity.UserSession;
import vn.smarttask.identity.event.SessionRevokedEvent;
import vn.smarttask.identity.repo.AppUserRepository;
import vn.smarttask.identity.repo.ReauthTicketRepository;
import vn.smarttask.identity.repo.RecoveryCodeRepository;
import vn.smarttask.identity.repo.UserRefreshTokenRepository;
import vn.smarttask.identity.repo.UserSessionRepository;
import vn.smarttask.identity.service.IdentityService;
import vn.smarttask.workspace.repo.WorkspaceMembershipRepository;
import vn.smarttask.workspace.repo.WorkspaceRepository;

/**
 * F4 EP-006 forced revoke (offline, Mockito): owner-or-SA rule, audit,
 * refresh-token cascade, and the revocation event for realtime/push fan-out.
 */
@ExtendWith(MockitoExtension.class)
class SessionRevokeTest {

  @Mock AppUserRepository users;
  @Mock UserSessionRepository sessions;
  @Mock UserRefreshTokenRepository refreshTokens;
  @Mock ReauthTicketRepository reauthTickets;
  @Mock RecoveryCodeRepository recoveryCodes;
  @Mock WorkspaceRepository workspaces;
  @Mock WorkspaceMembershipRepository wsMembers;
  @Mock AuditService audit;
  @Mock OutboxService outbox;
  @Mock ApplicationEventPublisher events;

  private IdentityService service() {
    return new IdentityService(users, sessions, refreshTokens, reauthTickets, recoveryCodes,
        workspaces, wsMembers, audit, outbox, true);
  }

  private IdentityService serviceWithEvents() {
    return new IdentityService(users, sessions, refreshTokens, reauthTickets, recoveryCodes,
        workspaces, wsMembers, audit, outbox, true, events, null);
  }

  private static UserSession activeSession(UUID sid, UUID user) {
    UserSession s = new UserSession(sid, user, "hash",
        OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));
    s.setActive(true);
    s.setStatus("ACTIVE");
    return s;
  }

  @Test
  void ownerRevokesOwnSession() {
    UUID user = UUID.randomUUID(), sid = UuidV7.generate();
    UserSession s = activeSession(sid, user);
    when(sessions.findById(sid)).thenReturn(Optional.of(s));
    when(refreshTokens.findBySessionId(sid)).thenReturn(List.of());
    when(sessions.save(any())).thenAnswer(i -> i.getArgument(0));

    var out = serviceWithEvents().revokeSession(user, sid, false, null);

    assertFalse(out.isActive());
    assertEquals("REVOKED_ADMIN", out.getStatus());
    verify(audit).append(eq(user), eq("session.revoke"), eq("user_session"), eq(sid),
        eq("ALLOWED"), eq("REVOKED_ADMIN"), any());
    verify(events).publishEvent(any(SessionRevokedEvent.class));
  }

  @Test
  void systemAdminRevokesForeignSession() {
    UUID sa = UUID.randomUUID(), victim = UUID.randomUUID(), sid = UuidV7.generate();
    UserSession s = activeSession(sid, victim);
    when(sessions.findById(sid)).thenReturn(Optional.of(s));
    when(refreshTokens.findBySessionId(sid)).thenReturn(List.of());
    when(sessions.save(any())).thenAnswer(i -> i.getArgument(0));

    serviceWithEvents().revokeSession(sa, sid, true, "abuse");

    assertEquals("REVOKED_ADMIN", s.getStatus());
    verify(audit).append(eq(sa), eq("session.revoke"), eq("user_session"), eq(sid),
        eq("ALLOWED"), eq("abuse"), any());
  }

  @Test
  void strangerReads404() {
    UUID stranger = UUID.randomUUID(), victim = UUID.randomUUID(), sid = UuidV7.generate();
    when(sessions.findById(sid)).thenReturn(Optional.of(activeSession(sid, victim)));

    AppException ex = assertThrows(AppException.class,
        () -> service().revokeSession(stranger, sid, false, null));
    assertEquals(404, ex.getHttpStatus().value());
    verify(events, never()).publishEvent(any());
  }

  @Test
  void unknownSessionReads404() {
    when(sessions.findById(any())).thenReturn(Optional.empty());
    AppException ex = assertThrows(AppException.class,
        () -> service().revokeSession(UUID.randomUUID(), UuidV7.generate(), true, null));
    assertEquals(404, ex.getHttpStatus().value());
  }

  @Test
  void inactiveSessionIs409() {
    UUID user = UUID.randomUUID(), sid = UuidV7.generate();
    UserSession s = activeSession(sid, user);
    s.setActive(false);
    s.setStatus("REVOKED_LOGOUT");
    when(sessions.findById(sid)).thenReturn(Optional.of(s));

    AppException ex = assertThrows(AppException.class,
        () -> service().revokeSession(user, sid, false, null));
    assertEquals(409, ex.getHttpStatus().value());
    assertEquals("STATE_CONFLICT", ex.getCode());
    assertEquals("SESSION_NOT_ACTIVE", ex.getReasonCode());
  }

  @Test
  void boundRefreshTokensAreRevoked() {
    UUID user = UUID.randomUUID(), sid = UuidV7.generate();
    UserSession s = activeSession(sid, user);
    when(sessions.findById(sid)).thenReturn(Optional.of(s));
    UserRefreshToken rt = new UserRefreshToken(UuidV7.generate(), user, sid, "hash",
        OffsetDateTime.now(ZoneOffset.UTC).plusDays(7));
    when(refreshTokens.findBySessionId(sid)).thenReturn(List.of(rt));
    when(sessions.save(any())).thenAnswer(i -> i.getArgument(0));
    when(refreshTokens.save(any())).thenAnswer(i -> i.getArgument(0));

    service().revokeSession(user, sid, false, null);

    assertTrue(rt.isRevoked());
  }

  @Test
  void logoutRevokesBoundRefreshTokens() {
    UUID user = UUID.randomUUID(), sid = UuidV7.generate();
    UserSession s = activeSession(sid, user);
    when(sessions.findBySessionTokenHash(any())).thenReturn(Optional.of(s));
    UserRefreshToken rt = new UserRefreshToken(UuidV7.generate(), user, sid, "hash",
        OffsetDateTime.now(ZoneOffset.UTC).plusDays(7));
    when(refreshTokens.findBySessionId(sid)).thenReturn(List.of(rt));
    when(sessions.save(any())).thenAnswer(i -> i.getArgument(0));
    when(refreshTokens.save(any())).thenAnswer(i -> i.getArgument(0));

    serviceWithEvents().logout("access-token");

    assertFalse(s.isActive());
    assertEquals("REVOKED_LOGOUT", s.getStatus());
    assertTrue(rt.isRevoked());
    verify(events).publishEvent(any(SessionRevokedEvent.class));
  }

  @Test
  void refreshRejectsSessionRevokedByLogout() {
    UUID user = UUID.randomUUID(), sid = UuidV7.generate();
    AppUser u = new AppUser(user, "alice", "a@x.io", "hash", "Alice");
    u.setStatus("ACTIVE");
    u.setActive(true);
    when(users.findById(user)).thenReturn(Optional.of(u));
    UserRefreshToken rt = new UserRefreshToken(UuidV7.generate(), user, sid, "hash",
        OffsetDateTime.now(ZoneOffset.UTC).plusDays(7));
    when(refreshTokens.findByTokenHash(any())).thenReturn(Optional.of(rt));
    UserSession revoked = activeSession(sid, user);
    revoked.setActive(false);
    revoked.setStatus("REVOKED_LOGOUT");
    when(sessions.findById(sid)).thenReturn(Optional.of(revoked));

    AppException ex = assertThrows(AppException.class, () -> service().refresh("refresh-token"));

    assertEquals(401, ex.getHttpStatus().value());
    assertEquals("INVALID_SESSION", ex.getCode());
    assertFalse(rt.isRevoked(), "refresh token must not be consumed when the session is gone");
  }

  @Test
  void legacyConstructorSkipsEventsWithoutNpe() {
    UUID user = UUID.randomUUID(), sid = UuidV7.generate();
    UserSession s = activeSession(sid, user);
    when(sessions.findById(sid)).thenReturn(Optional.of(s));
    when(refreshTokens.findBySessionId(sid)).thenReturn(List.of());
    when(sessions.save(any())).thenAnswer(i -> i.getArgument(0));

    assertDoesNotThrow(() -> service().revokeSession(user, sid, false, null));
  }
}
