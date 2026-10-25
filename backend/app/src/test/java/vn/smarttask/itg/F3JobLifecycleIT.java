package vn.smarttask.itg;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import vn.smarttask.common.AppException;
import vn.smarttask.common.entity.BackgroundJob;
import vn.smarttask.common.repo.BackgroundJobRepository;
import vn.smarttask.common.service.JobService;
import vn.smarttask.jobs.JobHandler;
import vn.smarttask.jobs.JobRunner;
import vn.smarttask.reports.service.ReportService;

/**
 * F3 JobLifecycleIT (real PostgreSQL): idempotent enqueue, QUEUED -> RUNNING
 * -> SUCCEEDED / FAILED-retry / CANCELLED-revoked, timeout, crash recovery.
 */
@TestPropertySource(properties = {"smarttask.workers.enabled=false"})
class F3JobLifecycleIT extends ItgBase {

  /** Test-only kinds (this context only): echo + fail-once. */
  @TestConfiguration
  static class EchoHandlers {
    @Bean
    JobHandler testEcho() {
      return new JobHandler() {
        @Override public String kind() { return "TEST_ECHO"; }
        @Override public void handle(BackgroundJob job, String owner) {
          if (job.getPayload() != null && job.getPayload().contains("fail-once")
              && job.getAttempts() < 2) {
            throw new AppException("STORAGE_UNAVAILABLE", "storage.unavailable",
                "STORAGE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE, "flaky");
          }
        }
      };
    }
  }

  @Autowired JobService jobs;
  @Autowired JobRunner runner;
  @Autowired BackgroundJobRepository repo;
  @Autowired ReportService reports;

  private long auditCount(String action, UUID entityId) {
    Integer n = jdbc.queryForObject(
        "select count(*) from audit_event where action=? and entity_id=?", Integer.class,
        action, entityId);
    return n == null ? 0 : n;
  }

  /**
   * The scratch DB is shared across IT classes: run worker passes until OUR
   * row reaches the expected state. Backoff-aware: when the row waits for a
   * retry instant (e.g. after a transient infra stall), sleep until due
   * instead of spinning, so one hiccup cannot flake the suite.
   */
  private BackgroundJob runUntil(UUID id,
      java.util.function.Predicate<BackgroundJob> done) {
    BackgroundJob row = repo.findById(id).orElseThrow();
    long deadline = System.currentTimeMillis() + 90000;
    while (System.currentTimeMillis() < deadline) {
      runner.processBatch("w-loop");
      row = repo.findById(id).orElseThrow();
      if (done.test(row)) return row;
      if (isTerminal(row) && row.getAttempts() >= row.getMaxAttempts()) return row;
      sleepFor(row.getNextAttemptAt());
    }
    return row;
  }

  private static boolean isTerminal(BackgroundJob row) {
    return "SUCCEEDED".equals(row.getStatus()) || "FAILED".equals(row.getStatus())
        || "CANCELLED".equals(row.getStatus());
  }

  static void sleepFor(java.time.OffsetDateTime nextAttemptAt) {
    try {
      if (nextAttemptAt != null) {
        long wait = nextAttemptAt.toInstant().toEpochMilli() - System.currentTimeMillis();
        if (wait > 0) {
          Thread.sleep(Math.min(wait, 3000));
          return;
        }
      }
      Thread.sleep(200);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Test
  void idempotentEnqueueReturnsOriginal() {
    String key = "f3-idem-" + tag();
    var a = jobs.enqueue("TEST_ECHO", "{}", null, key);
    var b = jobs.enqueue("TEST_ECHO", "{}", null, key);
    assertEquals(a.getId(), b.getId());
    assertEquals("QUEUED", b.getStatus());
  }

  @Test
  void queuedRunningSucceededWithAudit() {
    var j = jobs.enqueue("TEST_ECHO", "{\"mode\":\"ok\"}", null, "f3-ok-" + tag());
    var row = runUntil(j.getId(), r -> "SUCCEEDED".equals(r.getStatus()));
    assertEquals("SUCCEEDED", row.getStatus(),
        () -> "expected SUCCEEDED but was " + row.getStatus()
            + " error=" + row.getErrorCode() + " attempts=" + row.getAttempts()
            + " owner=" + row.getOwner() + " kind=" + row.getKind()
            + " payload=" + row.getPayload());
    assertEquals(1, row.getAttempts());
    assertNotNull(row.getStartedAt());
    assertNotNull(row.getFinishedAt());
    assertEquals(1, auditCount("job.lease", j.getId()));
    assertEquals(1, auditCount("job.complete", j.getId()));
  }

  @Test
  void failedRetriesWithBackoffThenSucceeds() {
    var j = jobs.enqueue("TEST_ECHO", "{\"mode\":\"fail-once\"}", null, "f3-flaky-" + tag());
    var failed = runUntil(j.getId(), r -> "FAILED".equals(r.getStatus()));
    assertEquals("FAILED", failed.getStatus());
    assertEquals("STORAGE_UNAVAILABLE", failed.getErrorCode());
    assertTrue(failed.getNextAttemptAt().isAfter(failed.getCreatedAt()));

    // force due (skip the backoff wait) -> retry succeeds
    jdbc.update("update background_job set next_attempt_at=now() where id=?", j.getId());
    var done = runUntil(j.getId(), r -> "SUCCEEDED".equals(r.getStatus()));
    assertEquals("SUCCEEDED", done.getStatus());
    assertEquals(2, done.getAttempts());
    assertEquals(1, auditCount("job.retry", j.getId()));
  }

  @Test
  void revokedScopeCancelsExportJob() {
    var mgr = activeUser("f3xmgr", "Secret123!");
    var member = activeUser("f3xmmbr", "Secret123!");
    UUID ws = mkWorkspace(mgr.getId(), "f3x");
    var p = mkActiveProject(mgr.getId(), ws, "f3x");
    addWsMember(ws, member.getId(), "MEMBER");
    addProjectMember(mgr.getId(), p.getId(), member.getId(), "MEMBER");
    var exp = reports.requestExport(member.getId(), "PROJECT_PROGRESS", "PROJECT", p.getId(),
        java.time.LocalDate.now().minusDays(7), java.time.LocalDate.now(), "CSV",
        "f3x-" + tag());
    // revoke between request and processing
    projects.removeMember(mgr.getId(), p.getId(), member.getId());

    var cancelled = runUntil(exp.getBackgroundJobId(),
        r -> "CANCELLED".equals(r.getStatus()));
    assertEquals("CANCELLED", cancelled.getStatus());
    var bg = repo.findById(exp.getBackgroundJobId()).orElseThrow();
    assertEquals("CANCELLED", bg.getStatus());
    assertEquals("CANCELLED_REVOKED", bg.getErrorCode());
  }

  @Test
  void runningTimeoutRetries() {
    var j = jobs.enqueue("TEST_ECHO", "{\"mode\":\"ok\"}", null, "f3-tmo-" + tag());
    // simulate a stuck RUNNING lease (started long ago, tiny timeout)
    jdbc.update("update background_job set status='RUNNING', owner='stuck',"
        + " started_at=now() - interval '10 minutes', timeout_ms=1000 where id=?", j.getId());

    assertTrue(jobs.sweepTimeouts(50) >= 1);
    var timed = repo.findById(j.getId()).orElseThrow();
    assertEquals("FAILED", timed.getStatus());
    assertEquals("FAILED_TIMEOUT", timed.getErrorCode());
    assertEquals(1, auditCount("job.timeout", j.getId()));

    jdbc.update("update background_job set next_attempt_at=now() where id=?", j.getId());
    var retried = runUntil(j.getId(), r -> "SUCCEEDED".equals(r.getStatus()));
    assertEquals("SUCCEEDED", retried.getStatus());
  }

  @Test
  void crashedLeaseIsReclaimedAndRetried() {
    var j = jobs.enqueue("TEST_ECHO", "{\"mode\":\"ok\"}", null, "f3-crash-" + tag());
    jdbc.update("update background_job set status='RUNNING', owner='dead',"
        + " lease_expires_at=now() - interval '1 minute', attempts=1 where id=?", j.getId());

    assertTrue(jobs.reclaimExpiredLeases(50, "startup") >= 1);
    var reclaimed = repo.findById(j.getId()).orElseThrow();
    assertEquals("FAILED", reclaimed.getStatus());
    assertEquals("WORKER_CRASH", reclaimed.getErrorCode());

    var counts = runUntil(j.getId(), r -> "SUCCEEDED".equals(r.getStatus()));
    assertEquals("SUCCEEDED", counts.getStatus());
  }

  @Test
  void unknownKindParksTerminalWithoutSpinning() {
    var j = jobs.enqueue("NO_SUCH_KIND_F3", "{}", null, "f3-park-" + tag());
    var row = runUntil(j.getId(), r -> "FAILED".equals(r.getStatus()));
    assertEquals("FAILED", row.getStatus());
    assertEquals("UNKNOWN_KIND_PARKED", row.getErrorCode());
    // terminal: re-driven batches never touch it again (lease cleared, attempts=max)
    runner.processBatch("w-park");
    var same = repo.findById(j.getId()).orElseThrow();
    assertEquals("FAILED", same.getStatus());
    assertNull(same.getOwner());
  }
}
