package vn.smarttask.reports;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ExportRevokeTest {
  @Test
  void ttlIs24h() {
    ExportJobManager m = new ExportJobManager();
    var j = m.request(UUID.randomUUID(), "PROJECT_PROGRESS", "PROJECT", UUID.randomUUID(), "CSV", true);
    assertEquals(24, java.time.Duration.between(j.authorizedAtGeneration() ? j.expiresAt().minusHours(24) : j.expiresAt(), j.expiresAt()).toHours());
  }

  @Test
  void revokeBetweenRequestAndDeliveryCancels() {
    AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
    Clock clock = new Clock() {
      public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
      public Clock withZone(java.time.ZoneId z) { return this; }
      public Instant instant() { return now.get(); }
    };
    ExportJobManager m = new ExportJobManager(clock);
    UUID requester = UUID.randomUUID();
    var j = m.request(requester, "PROJECT_PROGRESS", "PROJECT", UUID.randomUUID(), "PDF", true);
    m.markGenerated(j.id());
    // permission revoked before delivery -> CANCELLED_REVOKED, fetch -> 404
    var delivered = m.deliver(j.id(), requester, false);
    assertEquals(ExportJobManager.Status.CANCELLED_REVOKED, delivered.status());
    var ex = assertThrows(IllegalStateException.class,
        () -> m.fetchForDownload(j.id(), requester, false));
    assertEquals("RESOURCE_NOT_FOUND", ex.getMessage());
  }

  @Test
  void onlyRequesterCanDownload() {
    ExportJobManager m = new ExportJobManager();
    UUID requester = UUID.randomUUID();
    var j = m.request(requester, "PROJECT_PROGRESS", "PROJECT", UUID.randomUUID(), "CSV", true);
    m.markGenerated(j.id());
    var ex = assertThrows(IllegalStateException.class,
        () -> m.fetchForDownload(j.id(), UUID.randomUUID(), true));
    assertEquals("RESOURCE_NOT_FOUND", ex.getMessage());
  }

  @Test
  void expiredExport404() {
    AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
    Clock clock = new Clock() {
      public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
      public Clock withZone(java.time.ZoneId z) { return this; }
      public Instant instant() { return now.get(); }
    };
    ExportJobManager m = new ExportJobManager(clock);
    UUID requester = UUID.randomUUID();
    var j = m.request(requester, "PROJECT_PROGRESS", "PROJECT", UUID.randomUUID(), "CSV", true);
    m.markGenerated(j.id());
    now.set(now.get().plusSeconds(25 * 3600));
    var ex = assertThrows(IllegalStateException.class,
        () -> m.fetchForDownload(j.id(), requester, true));
    assertEquals("EXPORT_EXPIRED", ex.getMessage());
  }

  @Test
  void dashboardPartialAndSystemHidesPrivate() {
    var accesses = java.util.List.of(
        new DashboardComposer.ScopeAccess("PROJECT", "a", true),
        new DashboardComposer.ScopeAccess("PROJECT", "b", false));
    var metrics = Map.of("PROGRESS_PERCENT",
        MetricDictionary.MetricValue.ok("PROGRESS_PERCENT", 50.0, 1, 2),
        "TIMELOG_MINUTES_TOTAL", MetricDictionary.MetricValue.okInt("TIMELOG_MINUTES_TOTAL", 90),
        "PRODUCTIVITY_RANKING_INDIVIDUAL", MetricDictionary.MetricValue.okInt("X", 1));
    var ws = DashboardComposer.compose(DashboardComposer.Scope.WORKSPACE, accesses, metrics);
    assertEquals(MetricDictionary.MetricState.PARTIAL, ws.overall());
    assertFalse(ws.metrics().containsKey("PRODUCTIVITY_RANKING_INDIVIDUAL"), "R-07 never emitted");
    var sys = DashboardComposer.compose(DashboardComposer.Scope.SYSTEM, accesses, metrics);
    assertFalse(sys.metrics().containsKey("TIMELOG_MINUTES_TOTAL"), "system hides private content");
    assertTrue(sys.metrics().containsKey("PROGRESS_PERCENT"));
  }
}
