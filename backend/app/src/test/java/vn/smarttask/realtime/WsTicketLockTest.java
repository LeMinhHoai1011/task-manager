package vn.smarttask.realtime;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.common.service.TokenHash;
import vn.smarttask.identity.entity.UserSession;
import vn.smarttask.identity.repo.UserSessionRepository;

/**
 * GATE WS-01 (offline unit): handshake validate-and-consume serializes on the
 * locked ticket row. True concurrent double-handshake needs a real DB
 * (see StreamTicketConcurrencyIT, NOT_TESTED without Docker).
 */
@ExtendWith(MockitoExtension.class)
class WsTicketLockTest {

  @Mock RealtimeTicketRepository tickets;
  @Mock UserSessionRepository sessions;
  @Mock AuditService audit;

  private static final String RAW = "raw-ws-ticket";

  @Test
  void validateUsesLockedRowRead() {
    UUID sessionId = UUID.randomUUID();
    RealtimeTicket t = new RealtimeTicket(UUID.randomUUID(), sessionId,
        TokenHash.sha256(RAW), OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(60));
    when(tickets.findByTicketHashForUpdate(TokenHash.sha256(RAW)))
        .thenReturn(Optional.of(t));
    UserSession s = new UserSession(sessionId, UUID.randomUUID(), "h",
        OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));
    s.setActive(true);
    s.setStatus("ACTIVE");
    when(sessions.findById(sessionId)).thenReturn(Optional.of(s));

    new WsTicketService(tickets, sessions, audit).validateAndConsume(RAW, sessionId);

    verify(tickets).findByTicketHashForUpdate(TokenHash.sha256(RAW));
    assertTrue(t.isUsed(), "handshake ticket must be single-use");
  }

  @Test
  void secondConsumeWithSameTicketFails() {
    UUID sessionId = UUID.randomUUID();
    RealtimeTicket t = new RealtimeTicket(UUID.randomUUID(), sessionId,
        TokenHash.sha256(RAW), OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(60));
    t.setUsed(true);
    when(tickets.findByTicketHashForUpdate(TokenHash.sha256(RAW)))
        .thenReturn(Optional.of(t));

    assertThrows(vn.smarttask.common.AppException.class, () -> new WsTicketService(
        tickets, sessions, audit).validateAndConsume(RAW, sessionId));
    verify(tickets, org.mockito.Mockito.never()).save(any());
  }
}
