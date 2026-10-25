package vn.smarttask.realtime;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.when;
import vn.smarttask.common.AppException;
import vn.smarttask.common.UuidV7;
import vn.smarttask.common.service.TokenHash;
import vn.smarttask.identity.entity.UserSession;
import vn.smarttask.identity.event.SessionRevokedEvent;
import vn.smarttask.identity.event.UserAccessRevokedEvent;
import vn.smarttask.identity.repo.UserSessionRepository;

/**
 * F4 realtime registry (offline unit): session scoping, revocation fan-out,
 * sweeper behaviour, and the signal-only payload contract (routing keys,
 * never private content).
 */
@ExtendWith(MockitoExtension.class)
class RealtimeRegistryTest {

  @Mock UserSessionRepository sessions;

  private RealtimeRegistry registry() {
    return new RealtimeRegistry(sessions);
  }

  private static UserSession activeSession(UUID sid, UUID user) {
    UserSession s = new UserSession(sid, user, "hash",
        OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));
    s.setActive(true);
    return s;
  }

  @Test
  void subscribeTracksConnectionsPerSessionAndUser() {
    var r = registry();
    UUID s1 = UuidV7.generate(), s2 = UuidV7.generate(), user = UUID.randomUUID();
    r.subscribe(s1, user);
    r.subscribe(s1, user);
    r.subscribe(s2, user);
    assertEquals(2, r.connectionCount(s1));
    assertEquals(1, r.connectionCount(s2));
    assertEquals(3, r.userConnectionCount(user));
    assertEquals(0, r.connectionCount(UuidV7.generate()));
  }

  @Test
  void closeBySessionOnlyClosesThatSession() {
    var r = registry();
    UUID s1 = UuidV7.generate(), s2 = UuidV7.generate(), user = UUID.randomUUID();
    r.subscribe(s1, user);
    r.subscribe(s2, user);
    r.closeBySession(s1);
    assertEquals(0, r.connectionCount(s1));
    assertEquals(1, r.connectionCount(s2));
    assertEquals(1, r.userConnectionCount(user));
  }

  @Test
  void closeByUserClosesEverySession() {
    var r = registry();
    UUID user = UUID.randomUUID(), other = UUID.randomUUID();
    r.subscribe(UuidV7.generate(), user);
    r.subscribe(UuidV7.generate(), user);
    r.subscribe(UuidV7.generate(), other);
    r.closeByUser(user);
    assertEquals(0, r.userConnectionCount(user));
    assertEquals(1, r.userConnectionCount(other));
  }

  @Test
  void sessionRevokedEventClosesSessionAndSignalsSurvivors() {
    var r = registry();
    UUID user = UUID.randomUUID();
    UUID dead = UuidV7.generate(), alive = UuidV7.generate();
    r.subscribe(dead, user);
    r.subscribe(alive, user);
    // Publish must not throw even though standalone emitters have no async
    // context (dead-letter sends are swallowed and deregistered).
    assertDoesNotThrow(
        () -> r.onSessionRevoked(new SessionRevokedEvent(dead, user, "REVOKED_LOGOUT")));
    assertEquals(0, r.connectionCount(dead));
  }

  @Test
  void userAccessRevokedClosesAllUserConnections() {
    var r = registry();
    UUID user = UUID.randomUUID();
    r.subscribe(UuidV7.generate(), user);
    r.subscribe(UuidV7.generate(), user);
    r.onUserAccessRevoked(new UserAccessRevokedEvent(user, "REJECTED"));
    assertEquals(0, r.userConnectionCount(user));
  }

  @Test
  void sweepClosesInactiveSessionsKeepsActive() {
    var r = registry();
    UUID user = UUID.randomUUID();
    UUID live = UuidV7.generate(), gone = UuidV7.generate(), stale = UuidV7.generate();
    r.subscribe(live, user);
    r.subscribe(gone, user);
    r.subscribe(stale, user);
    when(sessions.findById(live)).thenReturn(Optional.of(activeSession(live, user)));
    when(sessions.findById(gone)).thenReturn(Optional.empty());
    UserSession inactive = activeSession(stale, user);
    inactive.setActive(false);
    when(sessions.findById(stale)).thenReturn(Optional.of(inactive));

    r.sweepInactiveSessions();

    assertEquals(1, r.connectionCount(live));
    assertEquals(0, r.connectionCount(gone));
    assertEquals(0, r.connectionCount(stale));
  }

  @Test
  void sweepClosesExpiredSessions() {
    var r = registry();
    UUID user = UUID.randomUUID(), sid = UuidV7.generate();
    r.subscribe(sid, user);
    UserSession s = new UserSession(sid, user, "hash",
        OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
    s.setActive(true);
    when(sessions.findById(sid)).thenReturn(Optional.of(s));

    r.sweepInactiveSessions();

    assertEquals(0, r.connectionCount(sid));
  }

  @Test
  void publishToEmptySessionIsNoop() {
    var r = registry();
    assertDoesNotThrow(() -> r.publishToSession(UuidV7.generate(), "TASK_STATE_CHANGED",
        "PROJECT", UuidV7.generate(), UuidV7.generate()));
    assertDoesNotThrow(() -> r.publishToUser(UUID.randomUUID(), "TASK_STATE_CHANGED",
        "PROJECT", UuidV7.generate(), UuidV7.generate()));
  }

  @Test
  void signalCarriesOnlyRoutingKeys() {
    UUID scope = UuidV7.generate(), obj = UuidV7.generate();
    var signal = RealtimeSignal.of("TASK_STATE_CHANGED", "PROJECT", scope, obj, 7);
    Map<String, Object> m = signal.toMap();
    assertEquals(Set.of("kind", "scope_type", "scope_id", "object_id", "seq", "at"),
        m.keySet());
    assertEquals("TASK_STATE_CHANGED", m.get("kind"));
    assertEquals("PROJECT", m.get("scope_type"));
    assertEquals(scope.toString(), m.get("scope_id"));
    assertEquals(obj.toString(), m.get("object_id"));
    assertEquals(7L, m.get("seq"));
    assertFalse(m.containsKey("title"));
    assertFalse(m.containsKey("content"));
    assertFalse(m.containsKey("payload"));
    assertFalse(m.containsKey("body"));
  }

  @Test
  void signalRejectsBlankKindOrScope() {
    assertThrows(IllegalArgumentException.class,
        () -> RealtimeSignal.of(" ", "PROJECT", UuidV7.generate(), UuidV7.generate(), 1));
    assertThrows(IllegalArgumentException.class,
        () -> RealtimeSignal.of("X", null, UuidV7.generate(), UuidV7.generate(), 1));
  }
}
