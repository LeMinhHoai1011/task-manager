package vn.smarttask.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import vn.smarttask.identity.repo.AppUserRepository;
import vn.smarttask.identity.repo.UserSessionRepository;

/**
 * GATE AUTH-01 (offline unit): exact public auth allowlist. Sensitive auth
 * endpoints (/reauth, /approve, /users) MUST pass the session filter.
 */
@ExtendWith(MockitoExtension.class)
class AuthAllowlistTest {

  @Mock UserSessionRepository sessions;
  @Mock AppUserRepository users;

  private SessionAuthenticationFilter filter() {
    return new SessionAuthenticationFilter(sessions, users,
        new ObjectMapper().findAndRegisterModules());
  }

  private boolean skipped(String uri) {
    MockHttpServletRequest r = new MockHttpServletRequest("POST", uri);
    r.setRequestURI(uri);
    return filter().shouldNotFilter(r);
  }

  @Test
  void publicAuthEndpointsSkipFilter() {
    assertTrue(skipped("/api/v1/auth/register"));
    assertTrue(skipped("/api/v1/auth/login"));
    assertTrue(skipped("/api/v1/auth/refresh"));
    assertTrue(skipped("/api/v1/auth/logout"));
  }

  @Test
  void sensitiveAuthEndpointsRequireSession() {
    assertFalse(skipped("/api/v1/auth/reauth"));
    assertFalse(skipped("/api/v1/auth/approve"));
    assertFalse(skipped("/api/v1/auth/users"));
  }

  @Test
  void nonAuthEndpointsRequireSession() {
    assertFalse(skipped("/api/v1/tasks"));
    assertFalse(skipped("/api/v1/realtime/ticket"));
    assertFalse(skipped("/api/v1/admin/audit"));
  }

  @Test
  void healthAndStreamTicketStayPublic() {
    assertTrue(skipped("/actuator/health"));
    assertTrue(skipped("/api/v1/health/readiness"));
    assertTrue(skipped("/error"));
    assertTrue(skipped("/api/v1/files/stream/abc123"));
  }
}
