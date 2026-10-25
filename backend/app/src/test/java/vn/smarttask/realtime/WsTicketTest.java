package vn.smarttask.realtime;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import vn.smarttask.common.AppException;
import vn.smarttask.common.UuidV7;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.common.service.TokenHash;
import vn.smarttask.identity.entity.UserSession;
import vn.smarttask.identity.repo.UserSessionRepository;

/**
 * F0 WS ticket (offline, Mockito): reuse / expiry / wrong-session /
 * inactive-session all fail closed with 401 INVALID_SESSION.
 */
@ExtendWith(MockitoExtension.class)
class WsTicketTest {

  @Mock RealtimeTicketRepository tickets;
  @Mock UserSessionRepository sessions;
  @Mock AuditService audit;

  private WsTicketService service() {
    return new WsTicketService(tickets, sessions, audit);
  }

  private UserSession activeSession(UUID sid, UUID user) {
    UserSession s = new UserSession(sid, user, "hash",
        OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));
    s.setActive(true);
    return s;
  }

  @Test
  void issueBindsToActiveSession() {
    UUID sid = UuidV7.generate(), user = UUID.randomUUID();
    when(sessions.findById(sid)).thenReturn(Optional.of(activeSession(sid, user)));

    var issued = service().issue(sid);
    assertNotNull(issued.ticket());
    assertFalse(issued.ticket().isBlank());
    ArgumentCaptor<RealtimeTicket> cap = ArgumentCaptor.forClass(RealtimeTicket.class);
    verify(tickets).save(cap.capture());
    assertEquals(sid, cap.getValue().getSessionId());
    assertEquals(TokenHash.sha256(issued.ticket()), cap.getValue().getTicketHash());
  }

  @Test
  void issueOnRevokedSessionIs401() {
    UUID sid = UuidV7.generate(), user = UUID.randomUUID();
    UserSession s = activeSession(sid, user);
    s.setActive(false);
    when(sessions.findById(sid)).thenReturn(Optional.of(s));

    AppException ex = assertThrows(AppException.class, () -> service().issue(sid));
    assertEquals(401, ex.getHttpStatus().value());
    assertEquals("INVALID_SESSION", ex.getCode());
  }

  private RealtimeTicket storedTicket(UUID tid, UUID sid, String raw) {
    return new RealtimeTicket(tid, sid, TokenHash.sha256(raw),
        OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(60));
  }

  @Test
  void validateHappyPathConsumesOnce() {
    UUID sid = UuidV7.generate(), user = UUID.randomUUID(), tid = UuidV7.generate();
    String raw = "ws-ticket-raw";
    RealtimeTicket t = storedTicket(tid, sid, raw);
    when(tickets.findByTicketHashForUpdate(TokenHash.sha256(raw))).thenReturn(Optional.of(t));
    when(sessions.findById(sid)).thenReturn(Optional.of(activeSession(sid, user)));

    assertEquals(user, service().validateAndConsume(raw, sid));
    assertTrue(t.isUsed());
    verify(tickets).save(t);
  }

  @Test
  void reuseIsRejected() {
    UUID sid = UuidV7.generate(), tid = UuidV7.generate();
    String raw = "ws-ticket-raw";
    RealtimeTicket t = storedTicket(tid, sid, raw);
    t.setUsed(true);
    when(tickets.findByTicketHashForUpdate(TokenHash.sha256(raw))).thenReturn(Optional.of(t));

    AppException ex = assertThrows(AppException.class,
        () -> service().validateAndConsume(raw, sid));
    assertEquals(401, ex.getHttpStatus().value());
  }

  @Test
  void expiredIsRejected() {
    UUID sid = UuidV7.generate(), tid = UuidV7.generate();
    String raw = "ws-ticket-raw";
    RealtimeTicket t = new RealtimeTicket(tid, sid, TokenHash.sha256(raw),
        OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1));
    when(tickets.findByTicketHashForUpdate(TokenHash.sha256(raw))).thenReturn(Optional.of(t));

    assertThrows(AppException.class, () -> service().validateAndConsume(raw, sid));
  }

  @Test
  void unknownTicketIsRejected() {
    when(tickets.findByTicketHashForUpdate(any())).thenReturn(Optional.empty());
    assertThrows(AppException.class,
        () -> service().validateAndConsume("nope", UuidV7.generate()));
  }

  @Test
  void wrongSessionIsRejectedAndBurned() {
    UUID sid = UuidV7.generate(), other = UuidV7.generate(), tid = UuidV7.generate();
    String raw = "ws-ticket-raw";
    RealtimeTicket t = storedTicket(tid, sid, raw);
    when(tickets.findByTicketHashForUpdate(TokenHash.sha256(raw))).thenReturn(Optional.of(t));

    AppException ex = assertThrows(AppException.class,
        () -> service().validateAndConsume(raw, other));
    assertEquals(401, ex.getHttpStatus().value());
    assertTrue(t.isUsed(), "mismatched ticket must be burned");
  }

  @Test
  void revokedSessionAtHandshakeIsRejected() {
    UUID sid = UuidV7.generate(), user = UUID.randomUUID(), tid = UuidV7.generate();
    String raw = "ws-ticket-raw";
    RealtimeTicket t = storedTicket(tid, sid, raw);
    when(tickets.findByTicketHashForUpdate(TokenHash.sha256(raw))).thenReturn(Optional.of(t));
    UserSession s = activeSession(sid, user);
    s.setActive(false);
    when(sessions.findById(sid)).thenReturn(Optional.of(s));

    assertThrows(AppException.class, () -> service().validateAndConsume(raw, sid));
    assertTrue(t.isUsed());
  }
}
