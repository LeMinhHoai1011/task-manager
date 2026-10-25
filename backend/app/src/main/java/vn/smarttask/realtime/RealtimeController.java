package vn.smarttask.realtime;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vn.smarttask.common.ApiResult;
import vn.smarttask.security.CurrentActor;

/**
 * F0 realtime ticket issuance + F4 SSE transport.
 *
 * <p>F0: authenticated (session) ticket issuance; the ticket — not the access
 * token — authenticates the realtime handshake.
 *
 * <p>F4 transport: {@code GET /api/v1/realtime/stream?ticket=...} opens a
 * Server-Sent Events stream after atomically validating + consuming the
 * ticket (sessionId is read back from the ticket row). The endpoint is
 * intentionally public at the session filter (ticket is the credential —
 * {@code EventSource} cannot set headers) and fails closed with 401
 * INVALID_SESSION on missing/unknown/used/expired tickets or inactive
 * sessions. Ticket values are never logged.
 *
 * <p>Server-&gt;client frames are {@code signal} events carrying
 * {@link RealtimeSignal} routing keys only (no private content); the client
 * re-fetches through the REST API on each signal. A consumed ticket is
 * single-use: reconnects must mint a fresh ticket first.
 */
@RestController
@RequestMapping("/api/v1/realtime")
public class RealtimeController {

  private final WsTicketService tickets;
  private final RealtimeRegistry registry;

  public RealtimeController(WsTicketService tickets, RealtimeRegistry registry) {
    this.tickets = tickets;
    this.registry = registry;
  }

  public record TicketDto(@JsonProperty("ticket") String ticket,
      @JsonProperty("expires_at") OffsetDateTime expiresAt) {}

  @PostMapping("/ticket")
  public ResponseEntity<ApiResult<TicketDto>> issue() {
    var principal = CurrentActor.require();
    var issued = tickets.issue(principal.sessionId());
    return ResponseEntity.ok(ApiResult.ok(
        new TicketDto(issued.ticket(), issued.expiresAt()), "realtime.ticket.success"));
  }

  @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream(
      @RequestAttribute(name = RealtimeHandshakeFilter.PRINCIPAL_ATTR)
      WsTicketService.TicketPrincipal principal) {
    // The ticket was already validated + consumed atomically by
    // RealtimeHandshakeFilter, so this method performs no auth of its own.
    return registry.subscribe(principal.sessionId(), principal.userId());
  }
}
