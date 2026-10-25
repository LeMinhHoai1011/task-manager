package vn.smarttask.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.smarttask.common.AppException;
import vn.smarttask.common.CorrelationIdFilter;
import vn.smarttask.common.ErrorEnvelope;

/**
 * F4 SSE handshake gate: validates and consumes the single-use ticket BEFORE
 * Spring MVC starts the async stream.
 *
 * <p>Why a filter: {@code EventSource} always sends
 * {@code Accept: text/event-stream}, and a JSON {@link ErrorEnvelope} cannot be
 * negotiated for that Accept (it would surface as 500 instead of 401). Doing
 * the handshake here means the controller always receives a validated
 * principal and can return a plain {@code SseEmitter}, while invalid tickets
 * fail closed with 401 - a body for normal API clients, status only for
 * EventSource.
 *
 * <p>The raw ticket is never logged; only audit metadata (hashed lookup) is
 * recorded by {@link WsTicketService}.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class RealtimeHandshakeFilter extends OncePerRequestFilter {

  public static final String PRINCIPAL_ATTR = "smarttask.realtime.principal";
  static final String PATH = "/api/v1/realtime/stream";

  private final WsTicketService tickets;
  private final ObjectMapper objectMapper;

  public RealtimeHandshakeFilter(WsTicketService tickets, ObjectMapper objectMapper) {
    this.tickets = tickets;
    this.objectMapper = objectMapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !PATH.equals(request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {
    try {
      WsTicketService.TicketPrincipal principal =
          tickets.validateAndConsumeByTicket(request.getParameter("ticket"));
      request.setAttribute(PRINCIPAL_ATTR, principal);
      chain.doFilter(request, response);
    } catch (AppException ex) {
      response.setStatus(ex.getHttpStatus().value());
      String accept = request.getHeader("Accept");
      boolean sse = accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE);
      if (!sse) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId == null || correlationId.isBlank()) {
          correlationId = request.getHeader(CorrelationIdFilter.HEADER);
        }
        if (correlationId == null || correlationId.isBlank()) {
          correlationId = UUID.randomUUID().toString();
        }
        response.setHeader(CorrelationIdFilter.HEADER, correlationId);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
            ErrorEnvelope.of(ex, correlationId));
      }
      response.flushBuffer();
    }
  }
}
