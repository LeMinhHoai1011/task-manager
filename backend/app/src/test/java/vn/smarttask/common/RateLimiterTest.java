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

  @Test
  void concurrentAuthenticationCannotExceedTheLimit() throws Exception {
    var limiter = new RateLimiter();
    var start = new java.util.concurrent.CountDownLatch(1);
    try (var executor = java.util.concurrent.Executors.newFixedThreadPool(32)) {
      var calls = new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
      for (int i = 0; i < 100; i++) calls.add(executor.submit(() -> {
        start.await();
        return limiter.check(RateLimiter.Bucket.AUTH, "same-client").allowed();
      }));
      start.countDown();
      int allowed = 0;
      for (var call : calls) if (call.get(10, java.util.concurrent.TimeUnit.SECONDS)) allowed++;
      assertEquals(RateLimiter.limitFor(RateLimiter.Bucket.AUTH).maxRequests(), allowed);
    }
  }

  @Test
  void distinctClientFloodIsBoundedAndExpiredKeysAreReclaimed() {
    var now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
    var limiter = fixedLimiter(now);
    for (int i = 0; i < 10_000; i++) assertTrue(limiter.check(RateLimiter.Bucket.AUTH, "client-" + i).allowed());
    assertFalse(limiter.check(RateLimiter.Bucket.AUTH, "overflow").allowed());
    now.set(now.get().plusSeconds(61));
    for (int i = 0; i < 256; i++) limiter.check(RateLimiter.Bucket.AUTH, "expired-client");
    assertTrue(limiter.check(RateLimiter.Bucket.AUTH, "new-client").allowed());
  }
}
