package vn.smarttask.common;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PH-7 rate limiter (in-memory fixed window, per 04 §15 buckets).
 * 429 responses carry ONLY Retry-After + X-RateLimit-Scope (never the threshold).
 * Pure domain — clock injectable, unit-tested.
 */
public class RateLimiter {
  public enum Bucket { AUTH, RECOVERY, FILE, AI, REPORT, DEFAULT }

  public record Limit(int maxRequests, Duration window) {}

  private static final Map<Bucket, Limit> LIMITS = Map.of(
      Bucket.AUTH, new Limit(10, Duration.ofMinutes(1)),
      Bucket.RECOVERY, new Limit(5, Duration.ofMinutes(1)),
      Bucket.FILE, new Limit(30, Duration.ofMinutes(1)),
      Bucket.AI, new Limit(10, Duration.ofMinutes(1)),
      Bucket.REPORT, new Limit(10, Duration.ofMinutes(1)),
      Bucket.DEFAULT, new Limit(100, Duration.ofMinutes(1)));

  private record Window(Instant start, int count) {}

  private final Map<String, Window> windows = new ConcurrentHashMap<>();
  private final Clock clock;
  private int checks;
  private static final int MAX_KEYS = 10_000;

  public RateLimiter(Clock clock) { this.clock = clock; }
  public RateLimiter() { this(java.time.Clock.systemUTC()); }

  public record Verdict(boolean allowed, long retryAfterSeconds, Bucket bucket) {}

  public synchronized Verdict check(Bucket bucket, String key) {
    Limit lim = LIMITS.get(bucket);
    String k = bucket.name() + ":" + key;
    Instant now = clock.instant();
    if ((++checks & 255) == 0) {
      windows.entrySet().removeIf(e -> !e.getValue().start().plusSeconds(60).isAfter(now));
    }
    Window w = windows.get(k);
    if (w == null && windows.size() >= MAX_KEYS) {
      return new Verdict(false, 60, bucket);
    }
    if (w == null || Duration.between(w.start(), now).compareTo(lim.window()) >= 0) {
      windows.put(k, new Window(now, 1));
      return new Verdict(true, 0, bucket);
    }
    if (w.count() < lim.maxRequests()) {
      windows.put(k, new Window(w.start(), w.count() + 1));
      return new Verdict(true, 0, bucket);
    }
    long retry = lim.window().minus(Duration.between(w.start(), now)).toSeconds() + 1;
    return new Verdict(false, Math.max(retry, 1), bucket);
  }

  public static Limit limitFor(Bucket b) { return LIMITS.get(b); }
}
