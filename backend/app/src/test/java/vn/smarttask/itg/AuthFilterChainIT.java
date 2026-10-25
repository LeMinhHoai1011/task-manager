package vn.smarttask.itg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import vn.smarttask.common.service.TokenHash;

/**
 * F2.2/2 AuthFilterChainIT: real HTTP through the real
 * {@code SessionAuthenticationFilter} (no mocks). Asserts the narrow public
 * allowlist: only register/login/refresh/logout skip the session filter;
 * reauth/approve/users and every other endpoint demand a live session.
 */
class AuthFilterChainIT extends ItgBase {

  private String seedToken() {
    var u = activeUser("itgf", "Secret123!");
    return loginToken(u.getUsername(), "Secret123!");
  }

  @Test
  void missingTokenIs401() throws Exception {
    MvcResult r = doGet("/api/v1/tasks", null);
    assertEquals(401, r.getResponse().getStatus());
    assertEquals("INVALID_SESSION", errorCode(r));
  }

  @Test
  void wrongTokenIs401() throws Exception {
    MvcResult r = doGet("/api/v1/tasks", "deadbeef-" + UUID.randomUUID());
    assertEquals(401, r.getResponse().getStatus());
    assertEquals("INVALID_SESSION", errorCode(r));
  }

  @Test
  void expiredTokenIs401() throws Exception {
    var u = activeUser("itgx", "Secret123!");
    String token = loginToken(u.getUsername(), "Secret123!");
    var s = sessions.findBySessionTokenHash(TokenHash.sha256(token)).orElseThrow();
    s.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
    sessions.save(s);

    MvcResult r = doGet("/api/v1/tasks", token);
    assertEquals(401, r.getResponse().getStatus());
    assertEquals("INVALID_SESSION", errorCode(r));
  }

  @Test
  void validTokenPasses() throws Exception {
    MvcResult r = doGet("/api/v1/tasks", seedToken());
    assertEquals(200, r.getResponse().getStatus());
  }

  @Test
  void sensitiveAuthEndpointsRequireToken() throws Exception {
    MvcResult reauth = doPost("/api/v1/auth/reauth", null, Map.of("action", "sensitive"));
    assertEquals(401, reauth.getResponse().getStatus());
    assertEquals("INVALID_SESSION", errorCode(reauth));

    MvcResult approve = doPost("/api/v1/auth/approve", null,
        Map.of("target_id", UUID.randomUUID().toString(), "approve", true));
    assertEquals(401, approve.getResponse().getStatus());

    MvcResult users = doPost("/api/v1/auth/users", null, Map.of(
        "username", "itg_nobody_" + tag(),
        "email", "nobody_" + tag() + "@itg.io",
        "password", "Secret123!",
        "display_name", "Nobody"));
    assertEquals(401, users.getResponse().getStatus());
  }

  @Test
  void publicAuthEndpointsDoNot401ForAuth() throws Exception {
    String t = tag();
    // register without token -> business 200
    MvcResult reg = doPost("/api/v1/auth/register", null, Map.of(
        "username", "itgreg_" + t,
        "email", "itgreg_" + t + "@itg.io",
        "password", "Secret123!",
        "display_name", "Reg"));
    assertEquals(200, reg.getResponse().getStatus());
    UUID regId = UUID.fromString(json(reg).path("data").path("user_id").asText());
    // the fresh registration is PENDING: approve it (service-level) before login
    identity.approve(null, regId, true, true);

    // login without token -> business 200
    MvcResult login = doPost("/api/v1/auth/login", null, Map.of(
        "username", "itgreg_" + t, "password", "Secret123!"));
    assertEquals(200, login.getResponse().getStatus());
    String refresh = refreshTokenOf(login);
    assertTrue(refresh != null && !refresh.isBlank());

    // NOTE: a bad-password login also yields 401 INVALID_SESSION/SESSION_INVALID
    // from the service itself (same envelope as the filter), so it cannot prove
    // filter passage and is intentionally not asserted here. The 200s above do.

    // refresh without token -> business 200 (rotation)
    MvcResult ref = doPost("/api/v1/auth/refresh", null,
        Map.of("refresh_token", refresh));
    assertEquals(200, ref.getResponse().getStatus());

    // re-login while a session is live -> 200 (old session revoked, single-session kept)
    MvcResult login2 = doPost("/api/v1/auth/login", null, Map.of(
        "username", "itgreg_" + t, "password", "Secret123!"));
    assertEquals(200, login2.getResponse().getStatus());

    // logout without Authorization header -> 400 (missing header), never filter-401
    MvcResult out = doPost("/api/v1/auth/logout", null, Map.of());
    assertNotEquals(401, out.getResponse().getStatus(),
        "logout must not be stopped by the session filter; got " + out.getResponse().getStatus());
  }
}
