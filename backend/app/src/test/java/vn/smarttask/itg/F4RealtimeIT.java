package vn.smarttask.itg;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import vn.smarttask.common.service.TokenHash;
import vn.smarttask.realtime.RealtimeRegistry;

/**
 * F4 realtime transport IT (real PG + real SSE stream over MockMvc async).
 *
 * <p>Covers: valid-ticket 200 + signal-only payload (routing keys, never
 * private content), bad/reused/expired tickets -&gt; 401, reconnects need a
 * fresh ticket, and session revoke/logout closes the old stream.
 */
@TestPropertySource(properties = {"smarttask.workers.enabled=false"})
class F4RealtimeIT extends ItgBase {

  @Autowired RealtimeRegistry realtime;

  private String issueTicket(String token) throws Exception {
    MvcResult r = doPost("/api/v1/realtime/ticket", token, Map.of());
    assertEquals(200, r.getResponse().getStatus());
    String ticket = json(r).path("data").path("ticket").asText();
    assertFalse(ticket.isBlank());
    return ticket;
  }

  private UUID sessionIdOf(String accessToken) {
    return sessions.findBySessionTokenHash(TokenHash.sha256(accessToken)).orElseThrow().getId();
  }

  private MvcResult openStream(String ticket) throws Exception {
    var b = get("/api/v1/realtime/stream").param("ticket", ticket)
        .header("X-Forwarded-For", fwd());
    if (ticket != null && ticket.startsWith("probe-accept:")) {
      b.accept(MediaType.TEXT_EVENT_STREAM);
    }
    return mvc.perform(b).andReturn();
  }

  @Test
  void validTicketStreamsSignalOnlyPayload() throws Exception {
    var u = activeUser("f4rt1u", "Secret123!");
    String token = loginToken(u.getUsername(), "Secret123!");
    UUID sid = sessionIdOf(token);
    String ticket = issueTicket(token);

    MvcResult started = openStream(ticket);
    assertTrue(started.getRequest().isAsyncStarted(), "stream must stay open (async)");

    UUID scope = UUID.randomUUID(), obj = UUID.randomUUID();
    realtime.publishToSession(sid, "TASK_STATE_CHANGED", "PROJECT", scope, obj);
    realtime.closeBySession(sid);

    MvcResult done = mvc.perform(asyncDispatch(started)).andReturn();
    assertEquals(200, done.getResponse().getStatus());
    String body = done.getResponse().getContentAsString();
    assertTrue(body.contains("event:signal"), "must arrive as a signal event: " + body);
    assertTrue(body.contains("\"kind\":\"TASK_STATE_CHANGED\""), body);
    assertTrue(body.contains("\"scope_type\":\"PROJECT\""), body);
    assertTrue(body.contains("\"scope_id\":\"" + scope + "\""), body);
    assertTrue(body.contains("\"object_id\":\"" + obj + "\""), body);
    assertTrue(body.contains("\"seq\":"), body);
    assertTrue(body.contains("\"at\":"), body);
    assertFalse(body.contains("title"), "signal must not carry titles: " + body);
    assertFalse(body.contains("content"), "signal must not carry content: " + body);
    assertFalse(body.contains("payload"), "signal must not carry payloads: " + body);
  }

  @Test
  void badAndMissingTicketsAre401() throws Exception {
    var u = activeUser("f4rt2u", "Secret123!");
    String token = loginToken(u.getUsername(), "Secret123!");

    MvcResult bogus = openStream("not-a-real-ticket");
    assertEquals(401, bogus.getResponse().getStatus());
    assertEquals("INVALID_SESSION", errorCode(bogus));

    MvcResult missing = mvc.perform(get("/api/v1/realtime/stream")
        .header("X-Forwarded-For", fwd()).accept(MediaType.TEXT_EVENT_STREAM)).andReturn();
    assertEquals(401, missing.getResponse().getStatus());

    // issuing a ticket needs a session: unauthenticated is 401, not 404
    MvcResult noAuth = doPost("/api/v1/realtime/ticket", null, Map.of());
    assertEquals(401, noAuth.getResponse().getStatus());
  }

  @Test
  void ticketReuseIsRejectedReconnectNeedsFreshTicket() throws Exception {
    var u = activeUser("f4rt3u", "Secret123!");
    String token = loginToken(u.getUsername(), "Secret123!");
    UUID sid = sessionIdOf(token);
    String ticket = issueTicket(token);

    MvcResult first = openStream(ticket);
    assertTrue(first.getRequest().isAsyncStarted());
    realtime.closeBySession(sid);
    mvc.perform(asyncDispatch(first)).andReturn();

    // the consumed ticket is dead: reconnect with it fails
    MvcResult replay = openStream(ticket);
    assertEquals(401, replay.getResponse().getStatus());
    assertEquals("INVALID_SESSION", errorCode(replay));

    // a fresh ticket reconnects fine
    MvcResult second = openStream(issueTicket(token));
    assertTrue(second.getRequest().isAsyncStarted());
    realtime.closeBySession(sid);
    mvc.perform(asyncDispatch(second)).andReturn();
  }

  @Test
  void expiredTicketIs401() throws Exception {
    var u = activeUser("f4rt4u", "Secret123!");
    String token = loginToken(u.getUsername(), "Secret123!");
    String ticket = issueTicket(token);
    // The V10 CHECK requires expires_at > created_at, so age both columns
    // instead of writing an expiry earlier than creation.
    jdbc.update("UPDATE realtime_ticket SET created_at = ?, expires_at = ? WHERE ticket_hash = ?",
        OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(2),
        OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1), TokenHash.sha256(ticket));

    MvcResult r = openStream(ticket);
    assertEquals(401, r.getResponse().getStatus());
    assertEquals("INVALID_SESSION", errorCode(r));
  }

  @Test
  void sessionRevokeClosesStreamAndKillsSession() throws Exception {
    var u = activeUser("f4rt5u", "Secret123!");
    String token = loginToken(u.getUsername(), "Secret123!");
    UUID sid = sessionIdOf(token);

    MvcResult started = openStream(issueTicket(token));
    assertTrue(started.getRequest().isAsyncStarted());
    assertEquals(1, realtime.connectionCount(sid));

    MvcResult revoke = doPost("/api/v1/sessions/revoke", token,
        Map.of("session_id", sid.toString()));
    assertEquals(200, revoke.getResponse().getStatus());
    assertEquals("REVOKED_ADMIN", json(revoke).path("data").path("status").asText());
    assertEquals(0, realtime.connectionCount(sid), "revoke must close the stream");

    // nothing new can arrive on the dead stream
    realtime.publishToSession(sid, "TASK_STATE_CHANGED", "PROJECT", UUID.randomUUID(),
        UUID.randomUUID());
    MvcResult done = mvc.perform(asyncDispatch(started)).andReturn();
    assertFalse(done.getResponse().getContentAsString().contains("event:signal"));

    // the access token is dead too
    MvcResult after = doGet("/api/v1/tasks", token);
    assertEquals(401, after.getResponse().getStatus());
  }

  @Test
  void logoutClosesStream() throws Exception {
    var u = activeUser("f4rt6u", "Secret123!");
    String token = loginToken(u.getUsername(), "Secret123!");
    UUID sid = sessionIdOf(token);

    MvcResult started = openStream(issueTicket(token));
    assertTrue(started.getRequest().isAsyncStarted());

    MvcResult logout = doPost("/api/v1/auth/logout", token, Map.of());
    assertEquals(200, logout.getResponse().getStatus());
    assertEquals(0, realtime.connectionCount(sid), "logout must close the stream");

    realtime.closeBySession(sid);
    mvc.perform(asyncDispatch(started)).andReturn();
  }

  /**
   * Real wiring proof: a committed API mutation (comment) publishes a signal to
   * the project members' open streams. This is not a direct registry call.
   */
  @Test
  void committedMutationPublishesSignalToProjectMembers() throws Exception {
    var mgr = activeUser("f4rt7m", "Secret123!");
    var member = activeUser("f4rt7x", "Secret123!");
    UUID ws = mkWorkspace(mgr.getId(), "f4rt7");
    addWsMember(ws, member.getId(), "MEMBER");
    var p = mkActiveProject(mgr.getId(), ws, "f4rt7");
    addProjectMember(mgr.getId(), p.getId(), member.getId(), "MEMBER");
    var t = mkTask(mgr.getId(), p.getId(), "f4rt7 task " + tag());
    tasks.assign(mgr.getId(),t.getId(),member.getId(),t.getVersion());

    String token = loginToken(member.getUsername(), "Secret123!");
    MvcResult started = openStream(issueTicket(token));
    assertTrue(started.getRequest().isAsyncStarted());

    MvcResult added = doPost("/api/v1/tasks/" + t.getId() + "/comments", token,
        Map.of("body", "signal-body-must-not-leak"),
        Map.of("Idempotency-Key", idem()));
    assertEquals(200, added.getResponse().getStatus(), added.getResponse().getContentAsString());

    realtime.closeBySession(sessionIdOf(token));
    MvcResult done = mvc.perform(asyncDispatch(started)).andReturn();
    String body = done.getResponse().getContentAsString();
    assertTrue(body.contains("event:signal"), "real mutation must signal: " + body);
    assertTrue(body.contains("\"kind\":\"COMMENT_ADDED\""), body);
    assertTrue(body.contains("\"scope_type\":\"PROJECT\""), body);
    assertFalse(body.contains("signal-body-must-not-leak"), "no content in signal: " + body);
  }
}
