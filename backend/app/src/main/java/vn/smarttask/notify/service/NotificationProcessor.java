package vn.smarttask.notify.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.smarttask.common.AppException;
import vn.smarttask.common.UuidV7;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.identity.repo.AppUserRepository;
import vn.smarttask.identity.repo.UserSessionRepository;
import vn.smarttask.notify.PushEmailGateway;
import vn.smarttask.notify.entity.DeliveryAttempt;
import vn.smarttask.notify.entity.Notification;
import vn.smarttask.notify.repo.DeliveryAttemptRepository;
import vn.smarttask.notify.repo.NotificationPreferenceRepository;
import vn.smarttask.notify.repo.NotificationRepository;
import vn.smarttask.project.repo.ProjectMembershipRepository;

/**
 * F3 notification processor: the outbox consumer for {@code notification.*}
 * events. Persists IN_APP, dedups by token, redacts when scope access is
 * lost, skips push when the session is revoked, defers into quiet hours, and
 * retries FAILED_RETRYABLE delivery attempts. No WS transport (V1 has no
 * provider wired; the gateway stays an explicit stub).
 */
@Service
public class NotificationProcessor {
  @org.springframework.beans.factory.annotation.Autowired(required=false) private org.springframework.jdbc.core.JdbcTemplate scopeJdbc;
  private final NotificationRepository notifications;
  private final NotificationPreferenceRepository prefs;
  private final DeliveryAttemptRepository attempts;
  private final AppUserRepository users;
  private final UserSessionRepository sessions;
  private final ProjectMembershipRepository projectMembers;
  private final PushEmailGateway gateway;
  private final AuditService audit;
  private final ObjectMapper om;
  private final Clock clock;

  public NotificationProcessor(NotificationRepository notifications,
      NotificationPreferenceRepository prefs, DeliveryAttemptRepository attempts,
      AppUserRepository users, UserSessionRepository sessions,
      ProjectMembershipRepository projectMembers, PushEmailGateway gateway,
      AuditService audit, ObjectMapper om, Clock clock) {
    this.notifications = notifications; this.prefs = prefs; this.attempts = attempts;
    this.users = users; this.sessions = sessions; this.projectMembers = projectMembers;
    this.gateway = gateway; this.audit = audit; this.om = om; this.clock = clock;
  }

  public record Delivery(UUID userId, String type, String severity, String scopeType,
      UUID scopeId, String titleKey, String bodyKey, String deepLink, String dedupToken,
      UUID sessionId, UUID correlationId) {}

  /** Parses the outbox payload for {@code notification.*} events. */
  public Delivery parse(String payloadJson) {
    try {
      JsonNode n = om.readTree(payloadJson == null ? "{}" : payloadJson);
      return new Delivery(
          n.hasNonNull("user_id") ? UUID.fromString(n.get("user_id").asText()) : null,
          n.path("type").asText("TASK_ASSIGNED"),
          n.path("severity").asText("NORMAL"),
          n.path("scope_type").asText("SYSTEM"),
          n.hasNonNull("scope_id") ? UUID.fromString(n.get("scope_id").asText()) : null,
          n.path("title_key").asText(""),
          n.path("body_key").asText(""),
          n.hasNonNull("deep_link") ? n.get("deep_link").asText() : null,
          n.hasNonNull("dedup_token") ? n.get("dedup_token").asText() : null,
          n.hasNonNull("session_id") ? UUID.fromString(n.get("session_id").asText()) : null,
          n.hasNonNull("correlation_id") ? UUID.fromString(n.get("correlation_id").asText()) : null);
    } catch (Exception e) {
      throw AppException.badRequest("NOTIFICATION_PAYLOAD_INVALID", "Bad notification payload");
    }
  }

  private boolean isMuted(UUID userId, String type, String severity) {
    if ("SECURITY_CRITICAL".equals(severity)) return false;
    return prefs.findByUserId(userId).stream()
        .anyMatch(p -> (p.getType().equals(type) || p.getType().equals("*")) && p.isMuted());
  }

  /**
   * Deliver once: dedup token hit returns the stored row (SKIP, no side
   * effects); muted types return null (not stored); lost scope access
   * persists a redacted row; revoked session persists IN_APP but records
   * PUSH as FAILED_PERMANENT (no push).
   */
  @Transactional
  public Notification deliver(Delivery d) {
    if (d.userId() == null) throw AppException.notFound("User not found");
    var user = users.findById(d.userId())
        .orElseThrow(() -> AppException.notFound("User not found"));
    if (d.dedupToken() != null) {
      var dup = notifications.findByDedupToken(d.dedupToken());
      if (dup.isPresent()) return dup.get();
    }
    if (isMuted(d.userId(), d.type(), d.severity())) return null;

    boolean scopeLost = "PROJECT".equals(d.scopeType()) && d.scopeId() != null
        && projectMembers.findMembership(d.scopeId(), d.userId()).isEmpty();
    if("TEAM".equals(d.scopeType())&&scopeJdbc!=null)scopeLost=!Boolean.TRUE.equals(scopeJdbc.queryForObject("select exists(select 1 from team_member m join workspace_team t on t.id=m.team_id join workspace_membership w on w.workspace_id=t.workspace_id and w.user_id=m.user_id and w.status='ACTIVE' where m.team_id=? and m.user_id=? and m.status='ACTIVE')",Boolean.class,d.scopeId(),d.userId()));

    Notification n = new Notification(UuidV7.generate(), d.userId(), d.type());
    n.setSeverity(d.severity());
    n.setScopeType(d.scopeType());
    n.setScopeId(d.scopeId());
    n.setTitleKey(d.titleKey());
    n.setBodyKey(d.bodyKey());
    n.setDeepLink(d.deepLink());
    n.setDedupToken(d.dedupToken());
    if (scopeLost) {
      n.setRedacted(true);
      n.setRedactedAt(OffsetDateTime.now(clock));
      n.setRedactionReason("PERMISSION_REVOKED_AT_DELIVERY");
      n.setPayload("{}");
      n.setDeepLink(null);
    }
    OffsetDateTime defer = quietDeferral(user, d.severity());
    if (defer != null) n.setDeferredUntil(defer);
    notifications.save(n);

    // IN_APP is always recorded for a persisted row.
    recordAttempt(n.getId(), "IN_APP", "SENT", null, d.correlationId());

    // PUSH only when a live session backs it; revoked -> no push (permanent).
    boolean pushable = hasLiveSession(d);
    if (pushable && !scopeLost && defer == null) {
      gateway.send(PushEmailGateway.Channel.PUSH, "user:" + d.userId(), d.titleKey(),
          d.correlationId() == null ? null : d.correlationId().toString());
      recordAttempt(n.getId(), "PUSH", "FAILED_PERMANENT", "STUB", d.correlationId(),
          "PROVIDER_DISABLED", "No push provider is implemented");
    } else {
      recordAttempt(n.getId(), "PUSH", "FAILED_PERMANENT", "STUB",
          d.correlationId(), "SESSION_REVOKED", "no live session: push skipped");
    }
    if (scopeLost) {
      audit.appendDenied(d.userId(), "notification.deliver", "notification", n.getId(),
          "PERMISSION_REVOKED_AT_DELIVERY");
    } else {
      audit.append(d.userId(), "notification.deliver", "notification", n.getId(),
          "ALLOWED", null, "{}");
    }
    return n;
  }

  /** Retry FAILED_RETRYABLE attempts (bounded: attempt_no <= 5, else permanent). */
  @Transactional
  public int retryPending() {
    List<DeliveryAttempt> pending = attempts.findByStatus("FAILED_RETRYABLE");
    int n = 0;
    for (DeliveryAttempt a : pending) {
      if (a.getAttemptNo() >= 5) {
        a.setStatus("FAILED_PERMANENT");
        a.setErrorCode("RETRY_EXHAUSTED");
        attempts.save(a);
        continue;
      }
      // Historical retryable rows cannot be delivered by a missing provider.
      // Terminal failure also prevents reprocessing them on every scheduler tick.
      a.setStatus("FAILED_PERMANENT");
      a.setErrorCode("PROVIDER_DISABLED");
      a.setErrorDetail("No external delivery provider is implemented");
      attempts.save(a);
      n++;
    }
    return n;
  }

  private boolean hasLiveSession(Delivery d) {
    if (d.sessionId() != null) {
      return sessions.findById(d.sessionId()).map(s -> s.isActive()
          && !OffsetDateTime.now(clock).isAfter(s.getExpiresAt())).orElse(false);
    }
    return !sessions.findByUserIdAndActiveTrue(d.userId()).isEmpty();
  }

  private void recordAttempt(UUID notificationId, String channel, String status,
      String provider, UUID correlationId) {
    recordAttempt(notificationId, channel, status, provider, correlationId, null, null);
  }

  private void recordAttempt(UUID notificationId, String channel, String status,
      String provider, UUID correlationId, String errorCode, String errorDetail) {
    DeliveryAttempt a = new DeliveryAttempt(UuidV7.generate(), notificationId, channel,
        notificationId + ":" + channel + ":1:" + UUID.randomUUID().toString().substring(0, 8));
    a.setStatus(status);
    a.setProvider(provider);
    a.setCorrelationId(correlationId);
    a.setErrorCode(errorCode);
    a.setErrorDetail(errorDetail);
    attempts.save(a);
  }

  /** Quiet-hours deferral (SECURITY_CRITICAL always immediate). */
  public OffsetDateTime quietDeferral(vn.smarttask.identity.entity.AppUser user, String severity) {
    if ("SECURITY_CRITICAL".equals(severity)) return null;
    if (user == null || !user.isQuietHoursEnabled()
        || user.getQuietStart() == null || user.getQuietEnd() == null) return null;
    ZoneId zone;
    try {
      zone = user.getTimezone() == null ? ZoneId.of("UTC") : ZoneId.of(user.getTimezone());
    } catch (Exception e) {
      zone = ZoneId.of("UTC");
    }
    LocalTime nowT = OffsetDateTime.now(clock).atZoneSameInstant(zone).toLocalTime();
    if (!inQuiet(nowT, user.getQuietStart(), user.getQuietEnd())) return null;
    OffsetDateTime now = OffsetDateTime.now(clock);
    var zoned = now.atZoneSameInstant(zone);
    var endToday = zoned.toLocalDate().atTime(user.getQuietEnd()).atZone(zone);
    if (!endToday.isAfter(zoned)) endToday = endToday.plusDays(1);
    return endToday.toOffsetDateTime();
  }

  static boolean inQuiet(LocalTime t, LocalTime start, LocalTime end) {
    if (start.equals(end)) return false;
    if (start.isBefore(end)) return !t.isBefore(start) && t.isBefore(end);
    return !t.isBefore(start) || t.isBefore(end);
  }
}
