package vn.smarttask.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.smarttask.common.CorrelationIdFilter;
import vn.smarttask.common.ErrorEnvelope;
import vn.smarttask.common.service.TokenHash;
import vn.smarttask.identity.repo.AppUserRepository;
import vn.smarttask.identity.repo.UserSessionRepository;

/**
 * F0 session authentication: resolves the actor from the opaque Bearer token.
 *
 * <p>Lookup: {@code Authorization: Bearer <token>} -&gt; SHA-256 -&gt;
 * {@code user_session} (must be active + unexpired) -&gt; {@code app_user}
 * (must be ACTIVE, not locked). On success the SecurityContext holds an
 * authenticated {@link Principal}; on failure the chain stops with an
 * {@link ErrorEnvelope}:
 * <ul>
 *   <li>missing/unknown/revoked/expired session -&gt; 401 INVALID_SESSION</li>
 *   <li>user not ACTIVE (incl. SUSPENDED/PENDING/REJECTED) -&gt; 403 ACCOUNT_NOT_ACTIVE</li>
 *   <li>locked flag or {@code locked_until} in future -&gt; 423 ACCOUNT_LOCKED</li>
 * </ul>
 *
 * <p>Public paths (no token required): {@code /api/v1/auth/register},
 * {@code /api/v1/auth/login}, {@code /api/v1/auth/refresh},
 * {@code /api/v1/auth/logout}, {@code /actuator/health},
 * {@code /api/v1/health/**}, {@code /error},
 * ticket-bearer {@code /api/v1/files/stream/**} (single-use stream
 * tickets carry their own auth + per-request recheck in FileController),
 * and ticket-bearer {@code /api/v1/realtime/stream} (single-use realtime
 * ticket consumed in RealtimeController; no Bearer is accepted there).
 */
@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {

  private final UserSessionRepository sessions;
  private final AppUserRepository users;
  private final ObjectMapper objectMapper;

  public SessionAuthenticationFilter(UserSessionRepository sessions, AppUserRepository users,
      ObjectMapper objectMapper) {
    this.sessions = sessions;
    this.users = users;
    this.objectMapper = objectMapper;
  }

  /**
   * Exact public auth endpoints. Sensitive auth endpoints ({@code /reauth},
   * {@code /approve}, {@code /users}) require a session principal and MUST
   * NOT be allowlisted: a blanket {@code /auth/**} bypass would make them
   * unreachable (always 401) or, worse, unauthenticated.
   */
  private static final java.util.Set<String> PUBLIC_AUTH_POSTS = java.util.Set.of(
      "/api/v1/auth/register",
      "/api/v1/auth/login",
      "/api/v1/auth/refresh",
      "/api/v1/auth/logout");

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    if (PUBLIC_AUTH_POSTS.contains(path)) {
      return true;
    }
    return path.equals("/actuator/health")
        || path.startsWith("/api/v1/health/")
        || path.equals("/error")
        || path.startsWith("/api/v1/files/stream/")
        || path.equals("/api/v1/realtime/stream");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {
    String raw = bearer(request);
    if (raw == null) {
      deny(response, request, 401, "INVALID_SESSION", "auth.session.invalid", "SESSION_INVALID");
      return;
    }
    var session = sessions.findBySessionTokenHash(TokenHash.sha256(raw));
    if (session.isEmpty() || !session.get().isActive()
        || OffsetDateTime.now(ZoneOffset.UTC).isAfter(session.get().getExpiresAt())) {
      deny(response, request, 401, "INVALID_SESSION", "auth.session.invalid", "SESSION_INVALID");
      return;
    }
    var user = users.findById(session.get().getUserId());
    if (user.isEmpty() || !user.get().isActive() || !"ACTIVE".equals(user.get().getStatus())) {
      deny(response, request, 403, "ACCOUNT_NOT_ACTIVE", "auth.account.not_active",
          "ACCOUNT_NOT_ACTIVE");
      return;
    }
    OffsetDateTime lockedUntil = user.get().getLockedUntil();
    if (user.get().isLocked()
        || (lockedUntil != null && lockedUntil.isAfter(OffsetDateTime.now(ZoneOffset.UTC)))) {
      deny(response, request, 423, "ACCOUNT_LOCKED", "auth.account.locked", "ACCOUNT_LOCKED");
      return;
    }
    var principal = new Principal(user.get().getId(), session.get().getId());
    var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
    SecurityContextHolder.getContext().setAuthentication(auth);
    chain.doFilter(request, response);
  }

  private static String bearer(HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    if (header == null || !header.startsWith("Bearer ")) {
      return null;
    }
    String raw = header.substring("Bearer ".length()).trim();
    return raw.isEmpty() ? null : raw;
  }

  private void deny(HttpServletResponse response, HttpServletRequest request, int status,
      String code, String messageKey, String reason) throws IOException {
    String correlationId = request.getHeader(CorrelationIdFilter.HEADER);
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getWriter(),
        ErrorEnvelope.of(code, messageKey, reason, null, correlationId));
  }
}
