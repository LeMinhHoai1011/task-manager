package vn.smarttask.itg;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * F3 §7 contract IT (real PG, HTTP): PageResponse items+totals, reauth
 * challenge/verify (+ legacy alias), accounts alias (+ legacy kept),
 * unified sort allowlist, If-Match 428, enriched error fields.
 */
@TestPropertySource(properties = {"smarttask.workers.enabled=false"})
class F3ContractIT extends ItgBase {

  private String[] adminWithGrant(String prefix) {
    var admin = activeUser(prefix + "adm", "Secret123!");
    grants.grantBootstrap(admin.getId(), "ACCOUNT_APPROVE_GLOBAL");
    return new String[]{admin.getUsername(), "Secret123!"};
  }

  @Test
  void accountsAliasAndLegacyBothWork() throws Exception {
    String[] adm = adminWithGrant("f3c1");
    String adminToken = loginToken(adm[0], adm[1]);

    // pending registration (self-registration on in ITG)
    MvcResult reg = doPost("/api/v1/auth/register", null, Map.of(
        "username", "f3c1pending_" + tag(), "email", "f3c1p_" + tag() + "@itg.io",
        "password", "Secret123!", "display_name", "Pending One"));
    assertEquals(200, reg.getResponse().getStatus());
    String pendingId = json(reg).path("data").path("user_id").asText();
    jdbc.update("update app_user set status='PENDING',active=false where id=?",UUID.fromString(pendingId));

    // EP-021 queue shows the pending account
    MvcResult queue = doGet("/api/v1/accounts", adminToken);
    assertEquals(200, queue.getResponse().getStatus());
    assertTrue(json(queue).path("data").toString().contains(pendingId));

    // EP-022 approve via alias
    MvcResult approve = doPost("/api/v1/accounts/" + pendingId + "/approve", adminToken,
        Map.of());
    assertEquals(200, approve.getResponse().getStatus());

    // EP-023 reject via alias
    MvcResult reg2 = doPost("/api/v1/auth/register", null, Map.of(
        "username", "f3c1rej_" + tag(), "email", "f3c1r_" + tag() + "@itg.io",
        "password", "Secret123!", "display_name", "Reject Me"));
    String rejectId = json(reg2).path("data").path("user_id").asText();
    MvcResult reject = doPost("/api/v1/accounts/" + rejectId + "/reject", adminToken,
        Map.of());
    assertEquals(200, reject.getResponse().getStatus());

    // legacy endpoints kept: approve + admin-create
    MvcResult reg3 = doPost("/api/v1/auth/register", null, Map.of(
        "username", "f3c1leg_" + tag(), "email", "f3c1l_" + tag() + "@itg.io",
        "password", "Secret123!", "display_name", "Legacy Path"));
    String legId = json(reg3).path("data").path("user_id").asText();
    MvcResult legacyApprove = doPost("/api/v1/auth/approve", adminToken,
        Map.of("target_id", legId, "approve", true));
    assertEquals(200, legacyApprove.getResponse().getStatus());
    MvcResult legacyCreate = doPost("/api/v1/auth/users", adminToken, Map.of(
        "username", "f3c1byadm_" + tag(), "email", "f3c1b_" + tag() + "@itg.io",
        "password", "Secret123!", "display_name", "By Admin"));
    assertEquals(200, legacyCreate.getResponse().getStatus());

    // approved user can log in
    var approved = users.findById(UUID.fromString(pendingId)).orElseThrow();
    assertEquals("ACTIVE", approved.getStatus());
  }

  @Test
  void reauthChallengeVerifyAndAlias() throws Exception {
    var u = activeUser("f3c2u", "Secret123!");
    String token = loginToken(u.getUsername(), "Secret123!");

    MvcResult ch = doPost("/api/v1/auth/reauth/challenge", token,
        Map.of("action", "sensitive"));
    assertEquals(200, ch.getResponse().getStatus());
    String ticket = json(ch).path("data").path("ticket_id").asText();
    assertFalse(ticket.isBlank());

    // wrong password -> 401, ticket stays live
    MvcResult bad = doPost("/api/v1/auth/reauth/verify", token, Map.of(
        "ticket_id", ticket, "password", "Nope123!", "action", "sensitive"));
    assertEquals(401, bad.getResponse().getStatus());

    // right password -> verified
    MvcResult ok = doPost("/api/v1/auth/reauth/verify", token, Map.of(
        "ticket_id", ticket, "password", "Secret123!", "action", "sensitive"));
    assertEquals(200, ok.getResponse().getStatus());
    assertTrue(json(ok).path("data").path("verified").asBoolean());

    // replay -> 428 single-use
    MvcResult replay = doPost("/api/v1/auth/reauth/verify", token, Map.of(
        "ticket_id", ticket, "password", "Secret123!", "action", "sensitive"));
    assertEquals(428, replay.getResponse().getStatus());

    // legacy alias still issues tickets
    MvcResult legacy = doPost("/api/v1/auth/reauth", token, Map.of("action", "sensitive"));
    assertEquals(200, legacy.getResponse().getStatus());
    assertFalse(json(legacy).path("data").path("ticket_id").asText().isBlank());
  }

  @Test
  void pageResponseHasItemsAndTotals() throws Exception {
    var u = activeUser("f3c3u", "Secret123!");
    String token = loginToken(u.getUsername(), "Secret123!");

    MvcResult tasks = doGet("/api/v1/tasks?sort=-created_at&limit=20", token);
    assertEquals(200, tasks.getResponse().getStatus());
    var data = json(tasks).path("data");
    assertTrue(data.has("items"), "PageResponse must carry items");
    assertTrue(data.has("next_cursor"));
    assertTrue(data.has("has_more"));
    assertTrue(data.has("limit"));
    assertTrue(data.has("total"), "countable feeds must carry total");

    MvcResult projects = doGet("/api/v1/projects?limit=5", token);
    assertEquals(200, projects.getResponse().getStatus());
    assertTrue(json(projects).path("data").has("items"));
  }

  @Test
  void unknownSortIs400NeverSilent() throws Exception {
    var u = activeUser("f3c4u", "Secret123!");
    String token = loginToken(u.getUsername(), "Secret123!");
    MvcResult r = doGet("/api/v1/tasks?sort=nope_not_a_field", token);
    assertEquals(400, r.getResponse().getStatus());
    assertEquals("UNSUPPORTED_SORT_FIELD", reasonCode(r));
  }

  @Test
  void missingIfMatchIs428AndVersionConflictCarriesVersion() throws Exception {
    var mgr = activeUser("f3c5mgr", "Secret123!");
    UUID ws = mkWorkspace(mgr.getId(), "f3c5");
    var p = mkActiveProject(mgr.getId(), ws, "f3c5");
    var t = mkTask(mgr.getId(), p.getId(), "if-match probe");
    String token = loginToken(mgr.getUsername(), "Secret123!");

    // missing If-Match -> 428 (docs), code kept
    MvcResult missing = doPatch("/api/v1/tasks/" + t.getId(), token,
        Map.of("title", "v2"), Map.of("Idempotency-Key", idem()));
    assertEquals(428, missing.getResponse().getStatus());
    assertEquals("IF_MATCH_REQUIRED", json(missing).path("error").path("code").asText());

    // stale version -> 409 with resource_version
    MvcResult stale = doPatch("/api/v1/tasks/" + t.getId(), token,
        Map.of("title", "v-stale"),
        Map.of("Idempotency-Key", idem(), "If-Match", "9999"));
    assertEquals(409, stale.getResponse().getStatus());
    assertTrue(json(stale).path("error").path("resource_version").asLong() >= 1);
  }
}
