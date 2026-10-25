package vn.smarttask.realtime;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vn.smarttask.common.RedactingLogger;
import vn.smarttask.identity.event.SessionRevokedEvent;
import vn.smarttask.identity.event.UserAccessRevokedEvent;
import vn.smarttask.identity.repo.UserSessionRepository;

/**
 * F4 realtime connection registry (SSE transport).
 *
 * <p>Transport note: the project pins Spring Boot 3.2.5 / Spring Framework
 * 6.1.x, while the only {@code spring-websocket} artifact in the offline
 * repository is 6.2.7 (built against Spring 6.2.x) and the only
 * {@code spring-boot-starter-websocket} is 3.5.0 (Boot 3.5.x line). Mixing
 * those with the 6.1.x core risks {@code NoSuchMethodError} / linkage
 * failures at runtime and they cannot be resolved offline, so the WebSocket
 * endpoint is NOT introduced here. Realtime is served over Server-Sent
 * Events via {@link SseEmitter} (already on the classpath through
 * {@code spring-boot-starter-web}): {@code GET /api/v1/realtime/stream}.
 *
 * <p>Auth: the handshake consumes a single-use ticket from
 * {@code POST /api/v1/realtime/ticket} ({@code ?ticket=} query). The
 * sessionId is read back from the ticket row (atomic
 * validate-and-consume), so callers never send access/refresh tokens in the
 * query, frame, or subprotocol. Ticket values are never logged
 * (see {@link RedactingLogger}).
 *
 * <p>Server-&gt;client messages are {@link RealtimeSignal} routing keys only.
 * A client receiving a signal re-fetches the object through the REST API.
 */
@Component
public class RealtimeRegistry {

  private static final Logger log = LoggerFactory.getLogger(RealtimeRegistry.class);

  /** SSE stream lifetime: clients reconnect with a fresh ticket afterwards. */
  static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1000L;

  private final UserSessionRepository sessions;

  private record Connection(SseEmitter emitter, UUID sessionId, UUID userId) {}

  private final ConcurrentHashMap<UUID, Set<Connection>> bySession = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<UUID, Set<UUID>> sessionsByUser = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<UUID, AtomicLong> seqBySession = new ConcurrentHashMap<>();

  public RealtimeRegistry(UserSessionRepository sessions) {
    this.sessions = sessions;
  }

  /**
   * Opens a stream for an already ticket-authenticated session. The caller
   * must have consumed a valid ticket first; this method performs no auth.
   */
  public SseEmitter subscribe(UUID sessionId, UUID userId) {
    SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
    Connection conn = new Connection(emitter, sessionId, userId);
    bySession.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(conn);
    sessionsByUser.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
    seqBySession.computeIfAbsent(sessionId, k -> new AtomicLong(0));
    emitter.onCompletion(() -> remove(conn));
    emitter.onTimeout(() -> remove(conn));
    emitter.onError(e -> remove(conn));
    // Flush the response headers / establish the stream immediately. Without
    // this, an idle stream can sit silent until the 30s heartbeat, which some
    // proxies and smoke checks interpret as a failed handshake.
    try {
      emitter.send(SseEmitter.event().comment("connected"));
    } catch (IOException | IllegalStateException e) {
      remove(conn);
    }
    RedactingLogger.info(log, null, "realtime.subscribe", "session_count",
        String.valueOf(bySession.size()));
    return emitter;
  }

  private void remove(Connection conn) {
    Set<Connection> set = bySession.get(conn.sessionId());
    if (set != null) {
      set.remove(conn);
      if (set.isEmpty()) {
        bySession.remove(conn.sessionId(), set);
        seqBySession.remove(conn.sessionId());
      }
    }
    Set<UUID> ss = sessionsByUser.get(conn.userId());
    if (ss != null && !bySession.containsKey(conn.sessionId())) {
      ss.remove(conn.sessionId());
      if (ss.isEmpty()) sessionsByUser.remove(conn.userId(), ss);
    }
  }

  private long nextSeq(UUID sessionId) {
    return seqBySession.computeIfAbsent(sessionId, k -> new AtomicLong(0)).incrementAndGet();
  }

  /** Signal-only publish to every open stream of one session. */
  public void publishToSession(UUID sessionId, String kind, String scopeType, UUID scopeId,
      UUID objectId) {
    Set<Connection> set = bySession.get(sessionId);
    if (set == null || set.isEmpty()) return;
    RealtimeSignal signal = RealtimeSignal.of(kind, scopeType, scopeId, objectId,
        nextSeq(sessionId));
    for (Connection c : new HashSet<>(set)) {
      try {
        c.emitter().send(SseEmitter.event().name("signal").data(signal.toMap()));
      } catch (IOException | IllegalStateException e) {
        remove(c);
      }
    }
  }

  /** Signal-only publish to every open stream of a user (each session keeps its own seq). */
  public void publishToUser(UUID userId, String kind, String scopeType, UUID scopeId,
      UUID objectId) {
    Set<UUID> ss = sessionsByUser.get(userId);
    if (ss == null || ss.isEmpty()) return;
    for (UUID sessionId : new HashSet<>(ss)) {
      publishToSession(sessionId, kind, scopeType, scopeId, objectId);
    }
  }

  /** Closes every stream bound to a session (revocation path). */
  public void closeBySession(UUID sessionId) {
    Set<Connection> set = bySession.get(sessionId);
    if (set == null || set.isEmpty()) return;
    for (Connection c : new HashSet<>(set)) {
      remove(c);
      try {
        c.emitter().complete();
      } catch (IllegalStateException e) {
        // already completed: registry entry already removed above
      }
    }
  }

  /** Closes every stream of a user (account-level revocation path). */
  public void closeByUser(UUID userId) {
    Set<UUID> ss = sessionsByUser.get(userId);
    if (ss == null || ss.isEmpty()) return;
    for (UUID sessionId : new HashSet<>(ss)) {
      closeBySession(sessionId);
    }
  }

  /** Immediate revocation: close the dead session, then signal the user's survivors. */
  @EventListener
  public void onSessionRevoked(SessionRevokedEvent e) {
    closeBySession(e.sessionId());
    publishToUser(e.userId(), "SESSION_REVOKED", "SESSION", e.sessionId(), e.sessionId());
  }

  /** Immediate revocation: close every connection of the user. */
  @EventListener
  public void onUserAccessRevoked(UserAccessRevokedEvent e) {
    closeByUser(e.userId());
  }

  /**
   * Periodic sweeper: closes streams whose session is gone, inactive, or
   * expired (covers paths that bypass the event bus, e.g. direct DB expiry).
   */
  @Scheduled(fixedDelay = 60000)
  public void sweepInactiveSessions() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    for (UUID sessionId : new HashSet<>(bySession.keySet())) {
      var s = sessions.findById(sessionId);
      if (s.isEmpty() || !s.get().isActive() || now.isAfter(s.get().getExpiresAt())) {
        closeBySession(sessionId);
      }
    }
  }

  /** Keep-alive comment so idle streams survive intermediary timeouts. */
  @Scheduled(fixedDelay = 30000)
  public void heartbeat() {
    for (Set<Connection> set : bySession.values()) {
      for (Connection c : new HashSet<>(set)) {
        try {
          c.emitter().send(SseEmitter.event().comment("hb"));
        } catch (IOException | IllegalStateException e) {
          remove(c);
        }
      }
    }
  }

  // ---------------------------------------------------------------- test hooks

  /** Open-stream count for one session. */
  public int connectionCount(UUID sessionId) {
    Set<Connection> set = bySession.get(sessionId);
    return set == null ? 0 : set.size();
  }

  /** Open-stream count across all sessions of a user. */
  public int userConnectionCount(UUID userId) {
    Set<UUID> ss = sessionsByUser.get(userId);
    if (ss == null) return 0;
    int n = 0;
    for (UUID sid : ss) n += connectionCount(sid);
    return n;
  }
}
