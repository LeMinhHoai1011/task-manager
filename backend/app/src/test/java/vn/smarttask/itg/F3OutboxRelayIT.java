package vn.smarttask.itg;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import vn.smarttask.common.repo.OutboxDispatchRepository;
import vn.smarttask.common.repo.OutboxEventRepository;
import vn.smarttask.common.service.OutboxService;
import vn.smarttask.jobs.OutboxRelay;

/**
 * F3 OutboxRelayIT (real PostgreSQL): emit -> relay dispatches exactly once,
 * retry/backoff, duplicate-delivery SKIP, concurrent claim (1 winner), crash
 * reclaim. Schedulers stay off; the relay is driven directly.
 *
 * <p>The scratch DB is shared across IT classes, so every test first drains
 * the inherited PENDING backlog (idempotent dispatches) and then asserts
 * exact outcomes for its own row.
 */
@TestPropertySource(properties = {"smarttask.workers.enabled=false"})
class F3OutboxRelayIT extends ItgBase {

  @Autowired OutboxService outbox;
  @Autowired OutboxRelay relay;
  @Autowired OutboxEventRepository events;
  @Autowired OutboxDispatchRepository ledger;

  private long auditCount(String action, UUID entityId) {
    Integer n = jdbc.queryForObject(
        "select count(*) from audit_event where action=? and entity_id=?", Integer.class,
        action, entityId);
    return n == null ? 0 : n;
  }

  /** Drain inherited backlog so each test starts from an empty due set. */
  @BeforeEach
  void drainBacklog() {
    for (int i = 0; i < 20; i++) {
      Map<String, Integer> c = relay.processBatch("w-drain");
      int total = c.values().stream().mapToInt(Integer::intValue).sum();
      if (total == 0) return;
    }
    fail("outbox backlog did not drain");
  }

  @Test
  void emitRelayDispatchesExactlyOnce() {
    var e = outbox.emit("task", UUID.randomUUID(), "audit.task pong", "audit.task.pong",
        "{\"action\":\"ping\"}", "f3-once-" + tag(), null);

    Map<String, Integer> first = relay.processBatch("w1");
    assertEquals(1, first.get("DISPATCHED"));

    var row = events.findById(e.getId()).orElseThrow();
    assertEquals("DISPATCHED", row.getStatus());
    assertTrue(row.isPublished());
    assertTrue(ledger.findById(new vn.smarttask.common.entity.OutboxDispatch.Key(
        e.getId(), "audit.task.pong")).isPresent());
    assertEquals(1, auditCount("ping", e.getAggregateId()));

    // Second pass: nothing due (exactly-once).
    Map<String, Integer> second = relay.processBatch("w1");
    assertEquals(0, second.values().stream().mapToInt(Integer::intValue).sum());
    assertEquals(1, auditCount("ping", e.getAggregateId()));
  }

  @Test
  void transientFailureRetriesThenDeadWithAlert() {
    // notification.* with an unparsable user_id -> 400 (retryable, never succeeds).
    var e = outbox.emit("user", UUID.randomUUID(), "notification.dispatch",
        "notification.dispatch", "{\"user_id\":\"not-a-uuid\"}", "f3-retry-" + tag(), null);

    for (int i = 0; i < 10; i++) {
      // force due (skip the backoff wait)
      jdbc.update("update outbox_event set next_attempt_at=now(), claimed_until=null"
          + " where id=?", e.getId());
      relay.processBatch("w-retry");
    }
    var row = events.findById(e.getId()).orElseThrow();
    assertEquals("DEAD", row.getStatus());
    assertEquals(10, row.getAttempts());
    assertNotNull(row.getLastError());
    assertEquals(1, auditCount("outbox.event.dead", e.getId()));
  }

  @Test
  void duplicateDeliverySkipsViaLedger() {
    var e = outbox.emit("task", UUID.randomUUID(), "audit.task dup", "audit.task.dup",
        "{\"action\":\"dup-ping\"}", "f3-dup-" + tag(), null);
    // A previous worker already dispatched this (event, handler).
    jdbc.update("insert into outbox_dispatch(event_id,handler) values (?,?)",
        e.getId(), "audit.task.dup");

    Map<String, Integer> outcome = relay.processBatch("w-dup");
    assertEquals(1, outcome.get("SKIP"));
    assertEquals(0, outcome.get("DISPATCHED"));
    var row = events.findById(e.getId()).orElseThrow();
    assertEquals("DISPATCHED", row.getStatus());
    // No handler side effect ran.
    assertEquals(0, auditCount("dup-ping", e.getAggregateId()));
  }

  @Test
  void concurrentClaimHasSingleWinner() throws Exception {
    var e = outbox.emit("task", UUID.randomUUID(), "audit.task race", "audit.task.race",
        "{\"action\":\"race-ping\"}", "f3-race-" + tag(), null);
    var pool = Executors.newFixedThreadPool(2);
    var gate = new CountDownLatch(1);
    var f1 = pool.submit(() -> { gate.await(5, TimeUnit.SECONDS); relay.processBatch("w-a"); return null; });
    var f2 = pool.submit(() -> { gate.await(5, TimeUnit.SECONDS); relay.processBatch("w-b"); return null; });
    gate.countDown();
    f1.get(30, TimeUnit.SECONDS);
    f2.get(30, TimeUnit.SECONDS);
    pool.shutdown();
    // Exactly-once under race: one ledger entry, one side effect, DISPATCHED.
    Integer ledgers = jdbc.queryForObject(
        "select count(*) from outbox_dispatch where event_id=?", Integer.class, e.getId());
    assertEquals(1, ledgers);
    assertEquals(1, auditCount("race-ping", e.getAggregateId()));
    assertEquals("DISPATCHED", events.findById(e.getId()).orElseThrow().getStatus());
  }

  @Test
  void crashedClaimIsReclaimed() {
    var e = outbox.emit("task", UUID.randomUUID(), "audit.task crash", "audit.task.crash",
        "{\"action\":\"crash-ping\"}", "f3-crash-" + tag(), null);
    // Simulate a worker that died holding the claim.
    jdbc.update("update outbox_event set claimed_by='dead-worker',"
        + " claimed_until=now() - interval '1 minute' where id=?", e.getId());

    Map<String, Integer> outcome = relay.processBatch("w-reclaim");
    assertEquals(1, outcome.get("DISPATCHED"));
    assertEquals(1, auditCount("crash-ping", e.getAggregateId()));
  }
}
