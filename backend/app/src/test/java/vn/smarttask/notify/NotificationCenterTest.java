package vn.smarttask.notify;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationCenterTest {
  private final Clock fixed = Clock.fixed(Instant.parse("2026-05-01T23:30:00Z"), ZoneOffset.UTC);

  private NotificationCenter.UserProfile quietProfile() {
    // 23:30Z = 16:30 PDT; quiet 22:00-07:00 America/Los_Angeles -> not quiet yet; use UTC window instead
    return new NotificationCenter.UserProfile(ZoneId.of("UTC"), true,
        LocalTime.of(23, 0), LocalTime.of(7, 0));
  }

  @Test
  void seqIsMonotonic() {
    NotificationCenter c = new NotificationCenter(fixed);
    UUID u = UUID.randomUUID();
    var a = c.send(u, "T", NotificationCenter.Severity.NORMAL, "SYSTEM", null, "t", "b", null, null, "d1");
    var b = c.send(u, "T", NotificationCenter.Severity.NORMAL, "SYSTEM", null, "t", "b", null, null, "d2");
    assertTrue(b.seq() > a.seq());
  }

  @Test
  void mutePerTypeButSecurityCriticalStillSent() {
    NotificationCenter c = new NotificationCenter(fixed);
    UUID u = UUID.randomUUID();
    c.setPreference(u, "TASK_ASSIGNED", true);
    assertNull(c.send(u, "TASK_ASSIGNED", NotificationCenter.Severity.NORMAL,
        "TASK", UUID.randomUUID(), "t", "b", null, null, "d1"));
    assertNotNull(c.send(u, "TASK_ASSIGNED", NotificationCenter.Severity.SECURITY_CRITICAL,
        "TASK", UUID.randomUUID(), "t", "b", null, null, "d2"));
  }

  @Test
  void quietHoursDeferNormalButNotCritical() {
    NotificationCenter c = new NotificationCenter(fixed);
    UUID u = UUID.randomUUID();
    var normal = c.send(u, "T", NotificationCenter.Severity.NORMAL, "SYSTEM", null,
        "t", "b", null, quietProfile(), "d1");
    assertNotNull(normal.deferredUntil(), "normal defers in quiet hours");
    var crit = c.send(u, "T", NotificationCenter.Severity.SECURITY_CRITICAL, "SYSTEM", null,
        "t", "b", null, quietProfile(), "d2");
    assertNull(crit.deferredUntil(), "critical sent immediately");
  }

  @Test
  void markAllReadIsAccountLevel() {
    NotificationCenter c = new NotificationCenter(fixed);
    UUID u = UUID.randomUUID();
    c.send(u, "A", NotificationCenter.Severity.NORMAL, "SYSTEM", null, "t", "b", null, null, "d1");
    c.send(u, "B", NotificationCenter.Severity.NORMAL, "SYSTEM", null, "t", "b", null, null, "d2");
    assertEquals(2, c.markAllRead(u));
    assertEquals(0, c.markAllRead(u));
  }

  @Test
  void redactionOnRevokeBlocksDeepLink() {
    NotificationCenter c = new NotificationCenter(fixed);
    UUID u = UUID.randomUUID();
    UUID scope = UUID.randomUUID();
    var n = c.send(u, "T", NotificationCenter.Severity.NORMAL, "PROJECT", scope,
        "t", "b", "app://projects/" + scope, null, "d1");
    assertTrue(c.canFollowDeepLink(u, n.id(), true));
    assertEquals(1, c.redactOnRevoke(u, "PROJECT", scope, "MEMBERSHIP_REVOKED"));
    assertFalse(c.canFollowDeepLink(u, n.id(), false));
    assertFalse(c.canFollowDeepLink(u, n.id(), true), "redacted stays blocked even if access returns");
    assertTrue(c.resync(u, 0, Set.of(scope)).isEmpty(), "resync skips revoked scope");
  }

  @Test
  void resyncCursorReturnsOnlyNewer() {
    NotificationCenter c = new NotificationCenter(fixed);
    UUID u = UUID.randomUUID();
    var a = c.send(u, "A", NotificationCenter.Severity.NORMAL, "SYSTEM", null, "t", "b", null, null, "d1");
    var b = c.send(u, "B", NotificationCenter.Severity.NORMAL, "SYSTEM", null, "t", "b", null, null, "d2");
    var after = c.resync(u, a.seq(), Set.of());
    assertEquals(1, after.size());
    assertEquals(b.id(), after.get(0).id());
  }
}
