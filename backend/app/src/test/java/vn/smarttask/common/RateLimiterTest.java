package vn.smarttask.common;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class RateLimiterTest {
  private RateLimiter fixedLimiter(AtomicReference<Instant> now) {
    Clock clock = new Clock() {
      public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
      public Clock withZone(java.time.ZoneId z) { return this; }
      public Instant instant() { return now.get(); }
    };
    return new RateLimiter(clock);
  }

  @Test
  void authBucketTripsAndReportsRetryWithoutThreshold() {
    AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
    RateLimiter lim = fixedLimiter(now);
    int max = RateLimiter.limitFor(RateLimiter.Bucket.AUTH).maxRequests();
    for (int i = 0; i < max; i++) {
      assertTrue(lim.check(RateLimiter.Bucket.AUTH, "1.2.3.4").allowed());
    }
    var v = lim.check(RateLimiter.Bucket.AUTH, "1.2.3.4");
    assertFalse(v.allowed());
    assertTrue(v.retryAfterSeconds() > 0);
    assertEquals(RateLimiter.Bucket.AUTH, v.bucket());
  }

  @Test
  void bucketsAreIndependent() {
    AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
    RateLimiter lim = fixedLimiter(now);
    int max = RateLimiter.limitFor(RateLimiter.Bucket.AUTH).maxRequests();
    for (int i = 0; i < max; i++) lim.check(RateLimiter.Bucket.AUTH, "ip");
    assertFalse(lim.check(RateLimiter.Bucket.AUTH, "ip").allowed());
    assertTrue(lim.check(RateLimiter.Bucket.DEFAULT, "ip").allowed());
  }

  @Test
  void windowResets() {
    AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
    RateLimiter lim = fixedLimiter(now);
    int max = RateLimiter.limitFor(RateLimiter.Bucket.RECOVERY).maxRequests();
    for (int i = 0; i < max; i++) lim.check(RateLimiter.Bucket.RECOVERY, "ip");
    assertFalse(lim.check(RateLimiter.Bucket.RECOVERY, "ip").allowed());
    now.set(now.get().plusSeconds(61));
    assertTrue(lim.check(RateLimiter.Bucket.RECOVERY, "ip").allowed());
  }

  @Test
  void bucketRouting() {
    assertEquals(RateLimiter.Bucket.AUTH, RateLimitFilter.bucketFor("/api/v1/auth/login"));
    assertEquals(RateLimiter.Bucket.FILE, RateLimitFilter.bucketFor("/api/v1/files/upload"));
    assertEquals(RateLimiter.Bucket.REPORT, RateLimitFilter.bucketFor("/api/v1/reports/dashboard"));
    assertEquals(RateLimiter.Bucket.DEFAULT, RateLimitFilter.bucketFor("/api/v1/tasks"));
  }
}
