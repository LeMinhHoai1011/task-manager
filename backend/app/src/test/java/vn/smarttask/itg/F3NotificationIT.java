package vn.smarttask.itg;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import vn.smarttask.common.service.OutboxService;
import vn.smarttask.jobs.OutboxRelay;
import vn.smarttask.notify.repo.DeliveryAttemptRepository;
import vn.smarttask.notify.repo.NotificationRepository;
import vn.smarttask.notify.service.NotificationProcessor;

/**
 * F3 NotificationIT (real PostgreSQL): outbox -> processor persist, dedup
 * token, redaction on lost scope, quiet-hours deferral, revoked session ->
 * no push, delivery retry.
 */
@TestPropertySource(properties = {"smarttask.workers.enabled=false"})
class F3NotificationIT extends ItgBase {

  @Autowired NotificationProcessor processor;
  @Autowired NotificationRepository notifications;
  @Autowired DeliveryAttemptRepository deliveryAttempts;
  @Autowired OutboxService outbox;
  @Autowired OutboxRelay relay;

  private UUID[] pair(String prefix) {
    var mgr = activeUser(prefix + "mgr", "Secret123!");
    var user = activeUser(prefix + "usr", "Secret123!");
    UUID ws = mkWorkspace(mgr.getId(), prefix);
    var p = mkActiveProject(mgr.getId(), ws, prefix);
    addWsMember(ws, user.getId(), "MEMBER");
    addProjectMember(mgr.getId(), p.getId(), user.getId(), "MEMBER");
    loginToken(user.getUsername(), "Secret123!"); // ensure a live session exists
    return new UUID[]{mgr.getId(), user.getId(), p.getId()};
  }

  private NotificationProcessor.Delivery base(UUID user, UUID scope, String token) {
    return new NotificationProcessor.Delivery(user, "TASK_ASSIGNED", "NORMAL", "PROJECT",
        scope, "task.assigned", "task.assigned.body", "/tasks/1", token, null, null);
  }

  @Test
  void outboxToProcessorPersistsExactlyOnce() {
    var ids = pair("f3n1");
    UUID user = ids[1], project = ids[2];
    String token = "f3n-dedup-" + tag();
    String payload = "{\"user_id\":\"" + user + "\",\"type\":\"TASK_ASSIGNED\","
        + "\"severity\":\"NORMAL\",\"scope_type\":\"PROJECT\",\"scope_id\":\"" + project + "\","
        + "\"title_key\":\"task.assigned\",\"dedup_token\":\"" + token + "\"}";
    outbox.emit("task", UUID.randomUUID(), "notification.dispatch", "notification.dispatch",
        payload, "f3n-" + tag(), null);

    relay.processBatch("w-n");
    // redrive until OUR notification lands (shared-DB backlog), then once more
    vn.smarttask.notify.entity.Notification landed = null;
    for (int i = 0; i < 40 && landed == null; i++) {
      relay.processBatch("w-n");
      landed = notifications.findByUserIdOrderByOccurredAtDesc(user).stream()
          .filter(n -> token.equals(n.getDedupToken())).findFirst().orElse(null);
      if (landed == null) {
        try {
          Thread.sleep(200);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    }
    assertNotNull(landed);
    relay.processBatch("w-n"); // redrive: ledger SKIP
    long rows = notifications.findByUserIdOrderByOccurredAtDesc(user).stream()
        .filter(n -> token.equals(n.getDedupToken())).count();
    assertEquals(1, rows, "dedup token must yield exactly one notification");
  }

  @Test
  void duplicateTokenSkipsWithoutSideEffects() {
    var ids = pair("f3n2");
    UUID user = ids[1], project = ids[2];
    String token = "f3n-dup-" + tag();
    var first = processor.deliver(base(user, project, token));
    assertNotNull(first);
    var second = processor.deliver(base(user, project, token));
    assertEquals(first.getId(), second.getId());
    long rows = notifications.findByUserIdOrderByOccurredAtDesc(user).stream()
        .filter(n -> token.equals(n.getDedupToken())).count();
    assertEquals(1, rows);
  }

  @Test
  void lostScopeAccessRedacts() {
    var ids = pair("f3n3");
    UUID mgr = ids[0], user = ids[1], project = ids[2];
    projects.removeMember(mgr, project, user);
    var n = processor.deliver(base(user, project, "f3n-redact-" + tag()));
    assertNotNull(n);
    assertTrue(n.isRedacted());
    assertNull(n.getDeepLink());
    assertEquals("{}", n.getPayload());
  }

  @Test
  void quietHoursDefersNonCriticalOnly() {
    var ids = pair("f3n4");
    UUID user = ids[1], project = ids[2];
    var u = users.findById(user).orElseThrow();
    LocalTime now = LocalTime.now(ZoneOffset.UTC).withNano(0);
    u.setTimezone("UTC");
    u.setQuietHoursEnabled(true);
    u.setQuietStart(now.minusHours(1));
    u.setQuietEnd(now.plusHours(1));
    users.save(u);

    var deferred = processor.deliver(base(user, project, "f3n-quiet-" + tag()));
    assertNotNull(deferred.getDeferredUntil(), "normal notification must defer in quiet hours");

    var critical = new NotificationProcessor.Delivery(user, "SECURITY", "SECURITY_CRITICAL",
        "PROJECT", project, "sec", "sec", null, "f3n-crit-" + tag(), null, null);
    var immediate = processor.deliver(critical);
    assertNull(immediate.getDeferredUntil(), "SECURITY_CRITICAL must send immediately");
  }

  @Test
  void revokedSessionMeansNoPush() {
    var ids = pair("f3n5");
    UUID user = ids[1], project = ids[2];
    // revoke every live session of the user
    for (var s : sessions.findByUserIdAndActiveTrue(user)) {
      s.setActive(false);
      sessions.save(s);
    }
    var n = processor.deliver(base(user, project, "f3n-nopush-" + tag()));
    assertNotNull(n, "IN_APP is still persisted");
    var push = deliveryAttempts.findByNotificationIdOrderByAttemptNoAsc(n.getId()).stream()
        .filter(a -> "PUSH".equals(a.getChannel())).findFirst().orElseThrow();
    assertEquals("FAILED_PERMANENT", push.getStatus());
    assertEquals("SESSION_REVOKED", push.getErrorCode());
  }

  @Test
  void retryableDeliveryRetries() {
    var ids = pair("f3n6");
    UUID user = ids[1], project = ids[2];
    var n = processor.deliver(base(user, project, "f3n-retry-" + tag()));
    // flip the recorded PUSH attempt to retryable, then retry
    var push = deliveryAttempts.findByNotificationIdOrderByAttemptNoAsc(n.getId()).stream()
        .filter(a -> "PUSH".equals(a.getChannel())).findFirst().orElseThrow();
    push.setStatus("FAILED_RETRYABLE");
    deliveryAttempts.save(push);

    assertEquals(1, processor.retryPending());
    var rows = deliveryAttempts.findByNotificationIdOrderByAttemptNoAsc(n.getId());
    assertTrue(rows.stream().anyMatch(a -> "SENT".equals(a.getStatus())));
  }

  @Test
  void mutedTypeIsNotStoredCriticalBypasses() {
    var ids = pair("f3n7");
    UUID user = ids[1], project = ids[2];
    jdbc.update("insert into notification_preference(id,user_id,type,muted) values (?,?,?,?)",
        UUID.randomUUID(), user, "TASK_ASSIGNED", true);
    assertNull(processor.deliver(base(user, project, "f3n-mute-" + tag())));
    var critical = new NotificationProcessor.Delivery(user, "TASK_ASSIGNED",
        "SECURITY_CRITICAL", "PROJECT", project, "sec", "sec", null,
        "f3n-mutecrit-" + tag(), null, null);
    assertNotNull(processor.deliver(critical));
  }
}
