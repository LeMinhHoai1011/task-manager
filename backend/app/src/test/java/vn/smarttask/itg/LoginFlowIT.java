package vn.smarttask.itg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * F2.2/3 LoginFlowIT: register -&gt; approve (grant-gated) -&gt; login -&gt;
 * GET /tasks (200, empty) -&gt; logout -&gt; old token 401; plus
 * locked (423) / suspended (403) login denials. All over real HTTP except
 * fixture seeding through the services.
 */
class LoginFlowIT extends ItgBase {

  @Test
  void registerApproveLoginListLogoutThenOldTokenDies() throws Exception {
    String t = tag();
    String username = "itgflow_" + t;
    String password = "Secret123!";

    // approver fixture: ACTIVE user holding ACCOUNT_APPROVE_GLOBAL
    var approver = activeUser("itgfa", "Secret123!");
    grants.grantBootstrap(approver.getId(), "ACCOUNT_APPROVE_GLOBAL");
    String approverToken = loginToken(approver.getUsername(), "Secret123!");

    // 1. register (PENDING, inactive)
    MvcResult reg = doPost("/api/v1/auth/register", null, Map.of(
        "username", username,
        "email", username + "@itg.io",
        "password", password,
        "display_name", "Flow"));
    assertEquals(200, reg.getResponse().getStatus());
    UUID userId = UUID.fromString(json(reg).path("data").path("user_id").asText());

    // 2. approve via HTTP as the grant-holding approver
    MvcResult ap = doPost("/api/v1/auth/approve", approverToken,
        Map.of("target_id", userId.toString(), "approve", true));
    assertEquals(200, ap.getResponse().getStatus());

    // 3. login via HTTP
    MvcResult login = doPost("/api/v1/auth/login", null, Map.of(
        "username", username, "password", password));
    assertEquals(200, login.getResponse().getStatus());
    String token = accessTokenOf(login);
    assertTrue(token != null && !token.isBlank());

    // 4. GET /tasks -> 200 with empty items (PageResponse nests the list under data.data)
    MvcResult list = doGet("/api/v1/tasks", token);
    assertEquals(200, list.getResponse().getStatus());
    assertTrue(json(list).path("data").path("data").isArray());
    assertEquals(0, json(list).path("data").path("data").size());

    // 5. logout via HTTP
    var logoutReq = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
        .post("/api/v1/auth/logout")
        .header("X-Forwarded-For", fwd())
        .header("Authorization", "Bearer " + token);
    MvcResult logout = mvc.perform(logoutReq).andReturn();
    assertEquals(200, logout.getResponse().getStatus());

    // 6. old token is now revoked -> 401
    MvcResult after = doGet("/api/v1/tasks", token);
    assertEquals(401, after.getResponse().getStatus());
    assertEquals("INVALID_SESSION", errorCode(after));
  }

  @Test
  void sameOriginBrowserPostWithOriginHeaderIsNotRejectedByCors() throws Exception {
    var user = activeUser("itgor", "Secret123!");
    MvcResult r = doPost("/api/v1/auth/login", null,
        Map.of("username", user.getUsername(), "password", "Secret123!"),
        Map.of("Origin", "http://localhost:8088"));
    assertEquals(200, r.getResponse().getStatus());
  }

  @Test
  void lockedUserGets423AndSuspendedGets403() throws Exception {
    // Fixture hygiene: service calls return detached instances whose @PrePersist
    // timestamps live only on Hibernate's managed copy (assigned-UUID + save()
    // takes the merge path), so re-fetch before mutating + saving.
    var lockedSeed = activeUser("itglk", "Secret123!");
    var locked = users.findById(lockedSeed.getId()).orElseThrow();
    locked.setLocked(true);
    users.save(locked);
    MvcResult r1 = doPost("/api/v1/auth/login", null, Map.of(
        "username", locked.getUsername(), "password", "Secret123!"));
    assertEquals(423, r1.getResponse().getStatus());
    assertEquals("ACCOUNT_LOCKED", errorCode(r1));

    var suspSeed = activeUser("itgsp", "Secret123!");
    var susp = users.findById(suspSeed.getId()).orElseThrow();
    susp.setStatus("SUSPENDED");
    susp.setActive(false);
    users.save(susp);
    MvcResult r2 = doPost("/api/v1/auth/login", null, Map.of(
        "username", susp.getUsername(), "password", "Secret123!"));
    assertEquals(403, r2.getResponse().getStatus());
    assertEquals("ACCOUNT_NOT_ACTIVE", errorCode(r2));
  }
}
