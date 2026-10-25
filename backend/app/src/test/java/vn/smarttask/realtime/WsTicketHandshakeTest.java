package vn.smarttask.realtime;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
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
import vn.smarttask.common.service.AuditService;
import vn.smarttask.common.service.TokenHash;
import vn.smarttask.identity.entity.UserSession;
import vn.smarttask.identity.repo.UserSessionRepository;

/**
 * F4 ticket-only handshake (offline, Mockito): the sessionId comes back from
 * the ticket row — the query never carries access/refresh tokens. Reuse,
 * expiry, unknown tickets, and inactive sessions fail closed with 401.
 */
@ExtendWith(MockitoExtension.class)
class WsTicketHandshakeTest {

  @Mock RealtimeTicketRepository tickets;
  @Mock UserSessionRepository sessions;
  @Mock AuditService audit;

  private WsTicketService service() {
    return new WsTicketService(tickets, sessions, audit);
  }

  private static UserSession activeSession(UUID sid, UUID user) {
    UserSession s = new UserSession(sid, user, "hash",
        OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));
    s.setActive(true);
    return s;
  }

  private RealtimeTicket storedTicket(UUID tid, UUID sid, String raw) {
    return new RealtimeTicket(tid, sid, TokenHash.sha256(raw),
        OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(60));
  }

  @Test
  void handshakeReturnsSessionFromTicketRow() {
    UUID sid = UuidV7.generate(), user = UUID.randomUUID(), tid = UuidV7.generate();
    String raw = "sse-handshake-raw";
    RealtimeTicket t = storedTicket(tid, sid, raw);
    when(tickets.findByTicketHashForUpdate(TokenHash.sha256(raw))).thenReturn(Optional.of(t));
    when(sessions.findById(sid)).thenReturn(Optional.of(activeSession(sid, user)));

    var principal = service().validateAndConsumeByTicket(raw);

    assertEquals(sid, principal.sessionId());
    assertEquals(user, principal.userId());
    assertTrue(t.isUsed(), "ticket must be single-use");
  }

  @Test
  void reuseIsRejected() {
    UUID sid = UuidV7.generate(), tid = UuidV7.generate();
    String raw = "sse-handshake-raw";
    RealtimeTicket t = storedTicket(tid, sid, raw);
    t.setUsed(true);
    when(tickets.findByTicketHashForUpdate(TokenHash.sha256(raw))).thenReturn(Optional.of(t));

    AppException ex = assertThrows(AppException.class,
        () -> service().validateAndConsumeByTicket(raw));
    assertEquals(401, ex.getHttpStatus().value());
    assertEquals("INVALID_SESSION", ex.getCode());
  }

  @Test
  void unknownTicketIsRejected() {
    when(tickets.findByTicketHashForUpdate(any())).thenReturn(Optional.empty());
    assertThrows(AppException.class, () -> service().validateAndConsumeByTicket("nope"));
  }

  @Test
  void blankTicketIsRejectedWithoutLookup() {
    assertThrows(AppException.class, () -> service().validateAndConsumeByTicket("  "));
    assertThrows(AppException.class, () -> service().validateAndConsumeByTicket(null));
  }

  @Test
  void expiredTicketIsRejected() {
    UUID sid = UuidV7.generate(), tid = UuidV7.generate();
    String raw = "sse-handshake-raw";
    RealtimeTicket t = new RealtimeTicket(tid, sid, TokenHash.sha256(raw),
        OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1));
    when(tickets.findByTicketHashForUpdate(TokenHash.sha256(raw))).thenReturn(Optional.of(t));

    assertThrows(AppException.class, () -> service().validateAndConsumeByTicket(raw));
    assertFalse(t.isUsed(), "expired tickets are rejected before consume");
  }

  @Test
  void inactiveSessionIsRejectedAndBurned() {
    UUID sid = UuidV7.generate(), user = UUID.randomUUID(), tid = UuidV7.generate();
    String raw = "sse-handshake-raw";
    RealtimeTicket t = storedTicket(tid, sid, raw);
    when(tickets.findByTicketHashForUpdate(TokenHash.sha256(raw))).thenReturn(Optional.of(t));
    UserSession s = activeSession(sid, user);
    s.setActive(false);
    when(sessions.findById(sid)).thenReturn(Optional.of(s));

    AppException ex = assertThrows(AppException.class,
        () -> service().validateAndConsumeByTicket(raw));
    assertEquals(401, ex.getHttpStatus().value());
    assertTrue(t.isUsed());
  }
}
