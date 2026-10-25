package vn.smarttask.realtime;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.smarttask.common.AppException;
import vn.smarttask.common.UuidV7;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.common.service.TokenHash;
import vn.smarttask.identity.repo.UserSessionRepository;

/**
 * F0 WebSocket ticket service: mints short-lived (60s) single-use tickets
 * bound to a session; validates ticket hash + expiry + single-use + session
 * ACTIVE at handshake time. No real WS server is required for the unit tests.
 */
@Service
public class WsTicketService {

  static final long TTL_SECONDS = 60L;

  private final RealtimeTicketRepository tickets;
  private final UserSessionRepository sessions;
  private final AuditService audit;

  public WsTicketService(RealtimeTicketRepository tickets, UserSessionRepository sessions,
      AuditService audit) {
    this.tickets = tickets;
    this.sessions = sessions;
    this.audit = audit;
  }

  public record IssuedTicket(String ticket, OffsetDateTime expiresAt) {}

  /**
   * F4 SSE-handshake principal: the session the ticket was issued for, plus
   * the owning user. The handshake query carries ONLY the ticket; the
   * sessionId is read back from the ticket row (atomic validate-and-consume),
   * so access/refresh tokens never travel in the query/frame/subprotocol.
   */
  public record TicketPrincipal(UUID sessionId, UUID userId) {}

  /** Mint a ticket for an ACTIVE session. Returns the raw (unhashed) ticket. */
  @Transactional
  public IssuedTicket issue(UUID sessionId) {
    var session = sessions.findById(sessionId)
        .orElseThrow(() -> AppException.invalidSession("Invalid session"));
    if (!session.isActive()
        || OffsetDateTime.now(ZoneOffset.UTC).isAfter(session.getExpiresAt())) {
      audit.appendDenied(session.getUserId(), "realtime.ticket.issue", "realtime_ticket", null,
          "SESSION_INACTIVE");
      throw AppException.invalidSession("Invalid session");
    }
    String raw = UuidV7.generate().toString().replace("-", "");
    OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(TTL_SECONDS);
    tickets.save(new RealtimeTicket(UuidV7.generate(), sessionId, TokenHash.sha256(raw),
        expiresAt));
    audit.append(session.getUserId(), "realtime.ticket.issue", "realtime_ticket", null,
        "ALLOWED", null, "{}");
    return new IssuedTicket(raw, expiresAt);
  }

  /**
   * Validate + consume a ticket for the expected session.
   * Reuse / expiry / unknown ticket / session mismatch / inactive session
   * all fail closed with 401 INVALID_SESSION.
   */
  @Transactional
  public UUID validateAndConsume(String rawTicket, UUID expectedSessionId) {
    if (rawTicket == null || rawTicket.isBlank() || expectedSessionId == null) {
      throw AppException.invalidSession("Invalid realtime ticket");
    }
    var t = tickets.findByTicketHashForUpdate(TokenHash.sha256(rawTicket.trim()));
    if (t.isEmpty() || t.get().isUsed()
        || OffsetDateTime.now(ZoneOffset.UTC).isAfter(t.get().getExpiresAt())) {
      audit.appendDenied(null, "realtime.handshake", "realtime_ticket", null,
          "TICKET_INVALID_OR_EXPIRED");
      throw AppException.invalidSession("Invalid realtime ticket");
    }
    var ticket = t.get();
    if (!expectedSessionId.equals(ticket.getSessionId())) {
      ticket.setUsed(true); // burn on mismatch: fail closed, no oracle
      tickets.save(ticket);
      audit.appendDenied(null, "realtime.handshake", "realtime_ticket", ticket.getId(),
          "SESSION_MISMATCH");
      throw AppException.invalidSession("Invalid realtime ticket");
    }
    var session = sessions.findById(ticket.getSessionId());
    if (session.isEmpty() || !session.get().isActive()
        || OffsetDateTime.now(ZoneOffset.UTC).isAfter(session.get().getExpiresAt())) {
      ticket.setUsed(true);
      tickets.save(ticket);
      audit.appendDenied(null, "realtime.handshake", "realtime_ticket", ticket.getId(),
          "SESSION_INACTIVE");
      throw AppException.invalidSession("Invalid realtime ticket");
    }
    ticket.setUsed(true); // single-use
    tickets.save(ticket);
    audit.append(session.get().getUserId(), "realtime.handshake", "realtime_ticket",
        ticket.getId(), "ALLOWED", null, "{}");
    return session.get().getUserId();
  }

  /**
   * F4 SSE handshake: validate + consume by ticket alone. The bound sessionId
   * is read back from the ticket row (same pessimistic lock as
   * {@link #validateAndConsume}), so the handshake query carries ONLY the
   * ticket — never an access/refresh token. Reuse / expiry / unknown ticket /
   * inactive session all fail closed with 401 INVALID_SESSION, and the raw
   * ticket value is never logged.
   */
  @Transactional
  public TicketPrincipal validateAndConsumeByTicket(String rawTicket) {
    if (rawTicket == null || rawTicket.isBlank()) {
      audit.appendDenied(null, "realtime.handshake", "realtime_ticket", null,
          "TICKET_INVALID_OR_EXPIRED");
      throw AppException.invalidSession("Invalid realtime ticket");
    }
    var t = tickets.findByTicketHashForUpdate(TokenHash.sha256(rawTicket.trim()));
    if (t.isEmpty() || t.get().isUsed()
        || OffsetDateTime.now(ZoneOffset.UTC).isAfter(t.get().getExpiresAt())) {
      audit.appendDenied(null, "realtime.handshake", "realtime_ticket", null,
          "TICKET_INVALID_OR_EXPIRED");
      throw AppException.invalidSession("Invalid realtime ticket");
    }
    var ticket = t.get();
    var session = sessions.findById(ticket.getSessionId());
    if (session.isEmpty() || !session.get().isActive()
        || OffsetDateTime.now(ZoneOffset.UTC).isAfter(session.get().getExpiresAt())) {
      ticket.setUsed(true);
      tickets.save(ticket);
      audit.appendDenied(null, "realtime.handshake", "realtime_ticket", ticket.getId(),
          "SESSION_INACTIVE");
      throw AppException.invalidSession("Invalid realtime ticket");
    }
    ticket.setUsed(true); // single-use: reconnects must mint a fresh ticket
    tickets.save(ticket);
    audit.append(session.get().getUserId(), "realtime.handshake", "realtime_ticket",
        ticket.getId(), "ALLOWED", null, "{}");
    return new TicketPrincipal(session.get().getId(), session.get().getUserId());
  }
}
