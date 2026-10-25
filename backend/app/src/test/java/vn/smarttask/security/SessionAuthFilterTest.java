package vn.smarttask.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.when;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.smarttask.common.service.TokenHash;
import vn.smarttask.identity.entity.AppUser;
import vn.smarttask.identity.entity.UserSession;
import vn.smarttask.identity.repo.AppUserRepository;
import vn.smarttask.identity.repo.UserSessionRepository;

/** F0 filter unit tests (offline, Mockito): token/session/account outcomes. */
@ExtendWith(MockitoExtension.class)
class SessionAuthFilterTest {

  @Mock UserSessionRepository sessions;
  @Mock AppUserRepository users;

  private SessionAuthenticationFilter filter;
  private final ObjectMapper om = new ObjectMapper().findAndRegisterModules();

  private final UUID userId = UUID.randomUUID();
  private final UUID sessionId = UUID.randomUUID();
  private static final String RAW = "opaque-token-hex";

  @BeforeEach
  void setUp() {
    filter = new SessionAuthenticationFilter(sessions, users, om);
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private MockHttpServletRequest req(String token) {
    MockHttpServletRequest r = new MockHttpServletRequest("GET", "/api/v1/tasks");
    r.setRequestURI("/api/v1/tasks");
    if (token != null) {
      r.addHeader("Authorization", "Bearer " + token);
    }
    return r;
  }

  private UserSession activeSession() {
    UserSession s = new UserSession(sessionId, userId, TokenHash.sha256(RAW),
        OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));
    s.setActive(true);
    s.setStatus("ACTIVE");
    return s;
  }

  private AppUser activeUser() {
    AppUser u = new AppUser(userId, "u", "u@x.io", "hash", "U");
    u.setActive(true);
    u.setStatus("ACTIVE");
    return u;
  }

  private JsonNode body(MockHttpServletResponse res) throws Exception {
    return om.readTree(res.getContentAsString());
  }

  @Test
  void missingTokenIs401InvalidSession() throws Exception {
    MockHttpServletResponse res = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();
    filter.doFilter(req(null), res, chain);
    assertEquals(401, res.getStatus());
    assertEquals("INVALID_SESSION", body(res).path("error").path("code").asText());
    assertNull(chain.getRequest(), "denied request must not reach the chain");
  }

  @Test
  void unknownTokenIs401() throws Exception {
    when(sessions.findBySessionTokenHash(TokenHash.sha256(RAW))).thenReturn(Optional.empty());
    MockHttpServletResponse res = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();
    filter.doFilter(req(RAW), res, chain);
    assertEquals(401, res.getStatus());
    assertEquals("INVALID_SESSION", body(res).path("error").path("code").asText());
    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }

  @Test
  void revokedSessionIs401() throws Exception {
    UserSession s = activeSession();
    s.setActive(false);
    s.setStatus("REVOKED_LOGOUT");
    when(sessions.findBySessionTokenHash(TokenHash.sha256(RAW))).thenReturn(Optional.of(s));
    MockHttpServletResponse res = new MockHttpServletResponse();
    filter.doFilter(req(RAW), res, new MockFilterChain());
    assertEquals(401, res.getStatus());
    assertEquals("INVALID_SESSION", body(res).path("error").path("code").asText());
  }

  @Test
  void expiredSessionIs401() throws Exception {
    UserSession s = activeSession();
    s.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
    when(sessions.findBySessionTokenHash(TokenHash.sha256(RAW))).thenReturn(Optional.of(s));
    MockHttpServletResponse res = new MockHttpServletResponse();
    filter.doFilter(req(RAW), res, new MockFilterChain());
    assertEquals(401, res.getStatus());
    assertEquals("INVALID_SESSION", body(res).path("error").path("code").asText());
  }

  @Test
  void suspendedUserIs403() throws Exception {
    when(sessions.findBySessionTokenHash(TokenHash.sha256(RAW)))
        .thenReturn(Optional.of(activeSession()));
    AppUser u = activeUser();
    u.setStatus("SUSPENDED");
    when(users.findById(userId)).thenReturn(Optional.of(u));
    MockHttpServletResponse res = new MockHttpServletResponse();
    filter.doFilter(req(RAW), res, new MockFilterChain());
    assertEquals(403, res.getStatus());
    assertEquals("ACCOUNT_NOT_ACTIVE", body(res).path("error").path("code").asText());
  }

  @Test
  void inactiveUserIs403() throws Exception {
    when(sessions.findBySessionTokenHash(TokenHash.sha256(RAW)))
        .thenReturn(Optional.of(activeSession()));
    AppUser u = activeUser();
    u.setActive(false);
    when(users.findById(userId)).thenReturn(Optional.of(u));
    MockHttpServletResponse res = new MockHttpServletResponse();
    filter.doFilter(req(RAW), res, new MockFilterChain());
    assertEquals(403, res.getStatus());
    assertEquals("ACCOUNT_NOT_ACTIVE", body(res).path("error").path("code").asText());
  }

  @Test
  void lockedUserIs423() throws Exception {
    when(sessions.findBySessionTokenHash(TokenHash.sha256(RAW)))
        .thenReturn(Optional.of(activeSession()));
    AppUser u = activeUser();
    u.setLocked(true);
    when(users.findById(userId)).thenReturn(Optional.of(u));
    MockHttpServletResponse res = new MockHttpServletResponse();
    filter.doFilter(req(RAW), res, new MockFilterChain());
    assertEquals(423, res.getStatus());
    assertEquals("ACCOUNT_LOCKED", body(res).path("error").path("code").asText());
  }

  @Test
  void lockedUntilFutureIs423() throws Exception {
    when(sessions.findBySessionTokenHash(TokenHash.sha256(RAW)))
        .thenReturn(Optional.of(activeSession()));
    AppUser u = activeUser();
    u.setLockedUntil(OffsetDateTime.now(ZoneOffset.UTC).plusHours(1));
    when(users.findById(userId)).thenReturn(Optional.of(u));
    MockHttpServletResponse res = new MockHttpServletResponse();
    filter.doFilter(req(RAW), res, new MockFilterChain());
    assertEquals(423, res.getStatus());
    assertEquals("ACCOUNT_LOCKED", body(res).path("error").path("code").asText());
  }

  @Test
  void validSessionSetsPrincipal() throws Exception {
    when(sessions.findBySessionTokenHash(TokenHash.sha256(RAW)))
        .thenReturn(Optional.of(activeSession()));
    when(users.findById(userId)).thenReturn(Optional.of(activeUser()));
    MockHttpServletResponse res = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();
    filter.doFilter(req(RAW), res, chain);
    assertEquals(200, res.getStatus());
    var auth = SecurityContextHolder.getContext().getAuthentication();
    assertNotNull(auth);
    assertTrue(auth.isAuthenticated());
    assertEquals(new Principal(userId, sessionId), auth.getPrincipal());
  }

  @Test
  void publicPathsSkipFilter() throws Exception {
    MockHttpServletRequest r = new MockHttpServletRequest("POST", "/api/v1/auth/login");
    r.setRequestURI("/api/v1/auth/login");
    MockHttpServletResponse res = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();
    filter.doFilter(r, res, chain);
    assertEquals(200, res.getStatus());
    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }
}
