package vn.smarttask.notify;

import java.time.Clock;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * PH-4 pure notification center: monotonic seq, per-account read state, mark-all-read,
 * per-type mute (SECURITY_CRITICAL always delivered), quiet-hours deferral by user tz
 * (SECURITY_CRITICAL sent immediately), redaction on revoke, deep-link auth recheck.
 * Clock injectable. No infrastructure imports.
 */
public class NotificationCenter {
  public enum Severity { NORMAL, IMPORTANT, SECURITY_CRITICAL }

  public record Notification(UUID id, long seq, UUID userId, String type, Severity severity,
      String scopeType, UUID scopeId, String titleKey, String bodyKey, String deepLink,
      OffsetDateTime occurredAt, OffsetDateTime readAt, boolean redacted,
      OffsetDateTime deferredUntil, String dedupToken) {
    public boolean isUnread() { return readAt == null && !redacted; }
  }

  public record Preference(String type, boolean muted) {}

  public record UserProfile(ZoneId timezone, boolean quietHoursEnabled,
      LocalTime quietStart, LocalTime quietEnd) {}

  private final AtomicLong seq = new AtomicLong(0);
  private final Map<UUID, Notification> store = new ConcurrentHashMap<>();
  private final Map<UUID, Map<String, Preference>> prefs = new ConcurrentHashMap<>();
  private final Clock clock;

  public NotificationCenter(Clock clock) { this.clock = clock; }
  public NotificationCenter() { this(java.time.Clock.systemUTC()); }

  public void setPreference(UUID userId, String type, boolean muted) {
    prefs.computeIfAbsent(userId, k -> new ConcurrentHashMap<>()).put(type, new Preference(type, muted));
  }

  private boolean isMuted(UUID userId, String type, Severity severity) {
    if (severity == Severity.SECURITY_CRITICAL) return false; // mute bypassed
    var m = prefs.get(userId);
    if (m == null) return false;
    Preference p = m.get(type);
    if (p != null && p.muted()) return true;
    Preference star = m.get("*");
    return star != null && star.muted();
  }

  private OffsetDateTime quietDeferral(UserProfile profile, Severity severity) {
    if (severity == Severity.SECURITY_CRITICAL) return null; // sent immediately
    if (profile == null || !profile.quietHoursEnabled()
        || profile.quietStart() == null || profile.quietEnd() == null) return null;
    LocalTime nowT = OffsetDateTime.now(clock).atZoneSameInstant(profile.timezone()).toLocalTime();
    if (isInQuiet(nowT, profile.quietStart(), profile.quietEnd())) {
      OffsetDateTime now = OffsetDateTime.now(clock);
      var zoned = now.atZoneSameInstant(profile.timezone());
      var endToday = zoned.toLocalDate().atTime(profile.quietEnd()).atZone(profile.timezone());
      if (!endToday.isAfter(zoned)) endToday = endToday.plusDays(1);
      return endToday.toOffsetDateTime();
    }
    return null;
  }

  static boolean isInQuiet(LocalTime t, LocalTime start, LocalTime end) {
    if (start.equals(end)) return false;
    if (start.isBefore(end)) return !t.isBefore(start) && t.isBefore(end);
    return !t.isBefore(start) || t.isBefore(end); // overnight window
  }

  /** Returns null when muted (not stored). SECURITY_CRITICAL always stored. */
  public Notification send(UUID userId, String type, Severity severity, String scopeType, UUID scopeId,
      String titleKey, String bodyKey, String deepLink, UserProfile profile, String dedupToken) {
    if (isMuted(userId, type, severity)) return null;
    OffsetDateTime now = OffsetDateTime.now(clock);
    Notification n = new Notification(UUID.randomUUID(), seq.incrementAndGet(), userId, type,
        severity, scopeType, scopeId, titleKey, bodyKey, deepLink, now, null, false,
        quietDeferral(profile, severity), dedupToken);
    store.put(n.id(), n);
    return n;
  }

  public Notification markRead(UUID userId, UUID notificationId) {
    Notification n = requireOwned(userId, notificationId);
    Notification upd = replace(n, now(), n.redacted(), n.deepLink());
    store.put(n.id(), upd);
    return upd;
  }

  /** Account-level mark-all-read: marks every unread notification of the account. */
  public int markAllRead(UUID userId) {
    int count = 0;
    for (Notification n : store.values()) {
      if (n.userId().equals(userId) && n.isUnread()) {
        store.put(n.id(), replace(n, now(), n.redacted(), n.deepLink()));
        count++;
      }
    }
    return count;
  }

  /** Revoke scope access -> redact payload + clear deep link (history kept, content hidden). */
  public int redactOnRevoke(UUID userId, String scopeType, UUID scopeId, String reason) {
    int count = 0;
    for (Notification n : store.values()) {
      if (n.userId().equals(userId) && scopeType.equals(n.scopeType())
          && scopeId.equals(n.scopeId()) && !n.redacted()) {
        store.put(n.id(), replace(n, n.readAt(), true, null));
        count++;
      }
    }
    return count;
  }

  /** Deep link click must re-validate permission; revoked -> blocked + redacted view. */
  public boolean canFollowDeepLink(UUID userId, UUID notificationId, boolean hasScopeReadNow) {
    Notification n = requireOwned(userId, notificationId);
    if (n.redacted() || !hasScopeReadNow) return false;
    return n.deepLink() != null;
  }

  /** WebSocket resync: notifications with seq > cursor, re-checked against current access. */
  public List<Notification> resync(UUID userId, long lastSeenSeq, Set<UUID> revokedScopeIds) {
    List<Notification> out = new ArrayList<>();
    for (Notification n : store.values()) {
      if (!n.userId().equals(userId) || n.seq() <= lastSeenSeq) continue;
      if (n.scopeId() != null && revokedScopeIds.contains(n.scopeId())) continue;
      if (n.redacted()) continue;
      out.add(n);
    }
    out.sort((a, b) -> Long.compare(a.seq(), b.seq()));
    return out;
  }

  private Notification requireOwned(UUID userId, UUID notificationId) {
    Notification n = store.get(notificationId);
    if (n == null || !n.userId().equals(userId)) {
      throw new IllegalStateException("NOTIFICATION_NOT_OWNED");
    }
    return n;
  }

  private OffsetDateTime now() { return OffsetDateTime.now(clock); }

  private static Notification replace(Notification n, OffsetDateTime readAt, boolean redacted, String deepLink) {
    return new Notification(n.id(), n.seq(), n.userId(), n.type(), n.severity(), n.scopeType(),
        n.scopeId(), n.titleKey(), redacted ? null : n.bodyKey(), redacted ? null : deepLink,
        n.occurredAt(), readAt, redacted, n.deferredUntil(), n.dedupToken());
  }
}
