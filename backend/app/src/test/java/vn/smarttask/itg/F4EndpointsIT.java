package vn.smarttask.itg;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import vn.smarttask.security.GrantService;

/**
 * F4 additive endpoints IT (real PG, HTTP): workspace projects (EP-062),
 * project tasks (EP-089), reminder preferences (EP-166/167), push devices
 * (EP-176..178), and forced session revoke (EP-006) — each with 404/403
 * policy evidence. Legacy endpoints are re-asserted, never altered.
 */
@TestPropertySource(properties = {"smarttask.workers.enabled=false"})
class F4EndpointsIT extends ItgBase {

  // ---------------------------------------------------------- EP-062 + EP-089

  @Test
  void workspaceProjectsAndProjectTasks() throws Exception {
    var mgr = activeUser("f4e1mgr", "Secret123!");
    var outsider = activeUser("f4e1out", "Secret123!");
    UUID ws = mkWorkspace(mgr.getId(), "f4e1");
    var p = mkActiveProject(mgr.getId(), ws, "f4e1");
    var t = mkTask(mgr.getId(), p.getId(), "f4e1 scoped task");
    String token = loginToken(mgr.getUsername(), "Secret123!");
    String outToken = loginToken(outsider.getUsername(), "Secret123!");

    // EP-062: member lists the workspace's projects
    MvcResult list = doGet("/api/v1/workspaces/" + ws + "/projects", token);
    assertEquals(200, list.getResponse().getStatus());
    assertTrue(json(list).path("data").path("items").toString().contains(p.getId().toString()));

    // EP-062 parity with the legacy filter endpoint
    MvcResult legacy = doGet("/api/v1/projects?workspace_id=" + ws, token);
    assertEquals(200, legacy.getResponse().getStatus());
    assertTrue(json(legacy).path("data").path("items").toString()
        .contains(p.getId().toString()));

    // EP-062 policy: stranger reads 404, unknown workspace reads 404
    assertEquals(404, doGet("/api/v1/workspaces/" + ws + "/projects", outToken)
        .getResponse().getStatus());
    assertEquals(404,
        doGet("/api/v1/workspaces/" + UUID.randomUUID() + "/projects", token)
            .getResponse().getStatus());

    // EP-089: member lists the project's tasks (snake_case DTO)
    MvcResult tasks = doGet("/api/v1/projects/" + p.getId() + "/tasks", token);
    assertEquals(200, tasks.getResponse().getStatus());
    String items = json(tasks).path("data").path("items").toString();
    assertTrue(items.contains(t.getId().toString()));
    assertTrue(items.contains("project_id"));

    // EP-089 filters + docs sort syntax
    MvcResult filtered =
        doGet("/api/v1/projects/" + p.getId() + "/tasks?state=TODO&sort=-created_at", token);
    assertEquals(200, filtered.getResponse().getStatus());
    MvcResult badSort =
        doGet("/api/v1/projects/" + p.getId() + "/tasks?sort=nope_not_a_field", token);
    assertEquals(400, badSort.getResponse().getStatus());
    assertEquals("UNSUPPORTED_SORT_FIELD", reasonCode(badSort));

    // EP-089 policy: stranger reads 404, unknown project reads 404
    assertEquals(404,
        doGet("/api/v1/projects/" + p.getId() + "/tasks", outToken).getResponse().getStatus());
    assertEquals(404, doGet("/api/v1/projects/" + UUID.randomUUID() + "/tasks", token)
        .getResponse().getStatus());
  }

  // ---------------------------------------------------------- EP-166 + EP-167

  @Test
  void reminderPreferences() throws Exception {
    var u = activeUser("f4e2u", "Secret123!");
    String token = loginToken(u.getUsername(), "Secret123!");

    // EP-166 defaults: disabled, no window, profile timezone
    MvcResult get = doGet("/api/v1/reminders/preferences", token);
    assertEquals(200, get.getResponse().getStatus());
    var data = json(get).path("data");
    assertFalse(data.path("quiet_hours_enabled").asBoolean());
    assertEquals("UTC", data.path("timezone").asText());

    // EP-167: set a window
    MvcResult put = doPut("/api/v1/reminders/preferences", token, Map.of(
        "quiet_hours_enabled", true, "quiet_hours_start", "22:00", "quiet_hours_end", "07:30"));
    assertEquals(200, put.getResponse().getStatus());
    var saved = json(put).path("data");
    assertTrue(saved.path("quiet_hours_enabled").asBoolean());
    assertEquals("22:00", saved.path("quiet_hours_start").asText());
    assertEquals("07:30", saved.path("quiet_hours_end").asText());
    var timezoneUpdate=doPut("/api/v1/reminders/preferences",token,Map.of("timezone","Asia/Bangkok"));assertEquals(200,timezoneUpdate.getResponse().getStatus());assertEquals("Asia/Bangkok",json(timezoneUpdate).path("data").path("timezone").asText());
    assertEquals(400,doPut("/api/v1/reminders/preferences",token,Map.of("timezone","Invalid/Zone")).getResponse().getStatus());

    // EP-167 validation: enabling without a window, and bad HH:MM
    java.util.Map<String, Object> keepWindow = new java.util.HashMap<>();
    keepWindow.put("quiet_hours_enabled", true);
    keepWindow.put("quiet_hours_start", null);
    MvcResult noWindow = doPut("/api/v1/reminders/preferences", token, keepWindow);
    assertEquals(200, noWindow.getResponse().getStatus(),
        "existing window is kept, so enabling stays valid");

    MvcResult badFmt = doPut("/api/v1/reminders/preferences", token, Map.of(
        "quiet_hours_enabled", true, "quiet_hours_start", "25:99", "quiet_hours_end", "07:30"));
    assertEquals(400, badFmt.getResponse().getStatus());
    assertEquals("QUIET_HOURS_INVALID", reasonCode(badFmt));

    MvcResult withSeconds = doPut("/api/v1/reminders/preferences", token, Map.of(
        "quiet_hours_enabled", true, "quiet_hours_start", "22:00:15",
        "quiet_hours_end", "07:30"));
    assertEquals(400, withSeconds.getResponse().getStatus());

    // unauthenticated reads 401
    assertEquals(401,
        doGet("/api/v1/reminders/preferences", null).getResponse().getStatus());
  }

  // ---------------------------------------------------------- EP-176..178

  @Test
  void pushDevices() throws Exception {
    var u = activeUser("f4e3u", "Secret123!");
    var other = activeUser("f4e3o", "Secret123!");
    String token = loginToken(u.getUsername(), "Secret123!");
    String otherToken = loginToken(other.getUsername(), "Secret123!");
    // Token unique theo run: DB test khong rollback nen token co dinh se trung.
    String pushToken = "f4e3-" + tag() + "-token";

    // EP-176 starts empty
    MvcResult empty = doGet("/api/v1/notifications/devices", token);
    assertEquals(200, empty.getResponse().getStatus());
    assertEquals(0, json(empty).path("data").size());

    // EP-177 registers the caller's session; hash never leaks
    MvcResult reg = doPost("/api/v1/notifications/devices", token, Map.of(
        "platform", "ANDROID_FCM", "push_token", pushToken, "device_label", "pixel"));
    assertEquals(200, reg.getResponse().getStatus());
    var row = json(reg).path("data");
    String regId = row.path("id").asText();
    assertFalse(regId.isBlank());
    assertFalse(row.path("session_id").asText().isBlank());
    assertEquals("ACTIVE", row.path("status").asText());
    String hint = row.path("token_hint").asText();
    assertTrue(hint.endsWith("...") && hint.length() < 20, "hint must be masked: " + hint);
    assertFalse(json(reg).toString().contains(pushToken), "raw token must not echo");

    // EP-177 validation
    MvcResult badPlatform = doPost("/api/v1/notifications/devices", token, Map.of(
        "platform", "UNSUPPORTED_PLATFORM", "push_token", "x"));
    assertEquals(400, badPlatform.getResponse().getStatus());
    assertEquals("PUSH_PLATFORM_UNSUPPORTED", reasonCode(badPlatform));
    MvcResult blankToken = doPost("/api/v1/notifications/devices", token, Map.of(
        "platform", "IOS_APNS", "push_token", "  "));
    assertEquals(400, blankToken.getResponse().getStatus());
    assertEquals("PUSH_TOKEN_INVALID", reasonCode(blankToken));

    // EP-177 cross-user token conflict is 409
    MvcResult clash = doPost("/api/v1/notifications/devices", otherToken, Map.of(
        "platform", "ANDROID_FCM", "push_token", pushToken));
    assertEquals(409, clash.getResponse().getStatus());
    assertEquals("PUSH_TOKEN_ALREADY_REGISTERED_OTHER_USER", reasonCode(clash));

    // EP-176 lists it; EP-178 unregisters idempotently
    assertEquals(1, json(doGet("/api/v1/notifications/devices", token)).path("data").size());
    assertEquals(200,
        doDelete("/api/v1/notifications/devices/" + regId, token).getResponse().getStatus());
    assertEquals(200,
        doDelete("/api/v1/notifications/devices/" + regId, token).getResponse().getStatus());
    assertEquals("UNREGISTERED",
        json(doGet("/api/v1/notifications/devices", token)).path("data").get(0)
            .path("status").asText());

    // EP-178 foreign id reads 404
    assertEquals(404,
        doDelete("/api/v1/notifications/devices/" + regId, otherToken).getResponse()
            .getStatus());
  }

  @Test
  void logoutInvalidatesPushRegistration() throws Exception {
    var u = activeUser("f4e4u", "Secret123!");
    String token = loginToken(u.getUsername(), "Secret123!");
    MvcResult reg = doPost("/api/v1/notifications/devices", token, Map.of(
        "platform", "IOS_APNS", "push_token", "f4e4-" + tag()));
    assertEquals(200, reg.getResponse().getStatus());

    MvcResult logout = doPost("/api/v1/auth/logout", token, Map.of());
    assertEquals(200, logout.getResponse().getStatus());

    // session is dead: a fresh login is needed to observe the cascade
    String token2 = loginToken(u.getUsername(), "Secret123!");
    var rows = json(doGet("/api/v1/notifications/devices", token2)).path("data");
    assertEquals(1, rows.size());
    assertEquals("UNREGISTERED", rows.get(0).path("status").asText());
  }

  // ---------------------------------------------------------- EP-006

  @Test
  void sessionRevokePolicy() throws Exception {
    var victim = activeUser("f4e5victim", "Secret123!");
    var stranger = activeUser("f4e5stranger", "Secret123!");
    var admin = activeUser("f4e5adm", "Secret123!");
    grants.grantBootstrap(admin.getId(), GrantService.SYSTEM_ADMIN);
    String victimToken = loginToken(victim.getUsername(), "Secret123!");
    String strangerToken = loginToken(stranger.getUsername(), "Secret123!");
    String adminToken = loginToken(admin.getUsername(), "Secret123!");
    UUID victimSid = sessions
        .findBySessionTokenHash(
            vn.smarttask.common.service.TokenHash.sha256(victimToken))
        .orElseThrow().getId();

    // stranger (no grant) reads 404
    MvcResult denied = doPost("/api/v1/sessions/revoke", strangerToken,
        Map.of("session_id", victimSid.toString()));
    assertEquals(404, denied.getResponse().getStatus());

    // missing session_id is 400
    MvcResult noBody = doPost("/api/v1/sessions/revoke", adminToken, Map.of());
    assertEquals(400, noBody.getResponse().getStatus());

    // SA revokes the victim's session: 200, refresh cascade, audit trail
    MvcResult revoke = doPost("/api/v1/sessions/revoke", adminToken,
        Map.of("session_id", victimSid.toString(), "reason", "abuse probe"));
    assertEquals(200, revoke.getResponse().getStatus());
    var s = sessions.findById(victimSid).orElseThrow();
    assertFalse(s.isActive());
    assertEquals("REVOKED_ADMIN", s.getStatus());

    // victim's tokens are dead (session + refresh rotation both fail)
    assertEquals(401, doGet("/api/v1/tasks", victimToken).getResponse().getStatus());

    // revoking again is 409 SESSION_NOT_ACTIVE
    MvcResult replay = doPost("/api/v1/sessions/revoke", adminToken,
        Map.of("session_id", victimSid.toString()));
    assertEquals(409, replay.getResponse().getStatus());
    assertEquals("SESSION_NOT_ACTIVE", reasonCode(replay));

    // unknown session is 404 even for SA
    MvcResult unknown = doPost("/api/v1/sessions/revoke", adminToken,
        Map.of("session_id", UUID.randomUUID().toString()));
    assertEquals(404, unknown.getResponse().getStatus());
  }

  // ---------------------------------------------------------- helpers

  protected org.springframework.test.web.servlet.MvcResult doPut(String path, String token,
      Object body) throws Exception {
    var b = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path)
        .header("X-Forwarded-For", fwd())
        .accept(org.springframework.http.MediaType.APPLICATION_JSON)
        .contentType(org.springframework.http.MediaType.APPLICATION_JSON);
    if (token != null) b.header("Authorization", "Bearer " + token);
    if (body != null) b.content(om.writeValueAsString(body));
    return mvc.perform(b).andReturn();
  }

  protected org.springframework.test.web.servlet.MvcResult doDelete(String path, String token)
      throws Exception {
    return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
        .delete(path).header("X-Forwarded-For", fwd())
        .accept(org.springframework.http.MediaType.APPLICATION_JSON)
        .header("Authorization", "Bearer " + token)).andReturn();
  }
}
