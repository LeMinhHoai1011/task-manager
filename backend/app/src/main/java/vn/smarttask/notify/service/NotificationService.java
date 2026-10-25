package vn.smarttask.notify.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.smarttask.common.UuidV7;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.notify.entity.Notification;
import vn.smarttask.notify.repo.NotificationPreferenceRepository;
import vn.smarttask.notify.repo.NotificationRepository;
import vn.smarttask.realtime.RealtimePublisher;

/**
 * PH-4 NotificationService (JPA wiring over NotificationCenter semantics):
 * account-level read state, mark-all-read, per-type mute (SECURITY_CRITICAL bypass),
 * redaction on revoke, deep-link recheck at click time.
 */
@Service
public class NotificationService {
  private final NotificationRepository notifications;
  private final NotificationPreferenceRepository prefs;
  private final AuditService audit;

  public NotificationService(NotificationRepository notifications,
      NotificationPreferenceRepository prefs, AuditService audit) {
    this.notifications = notifications; this.prefs = prefs; this.audit = audit;
  }

  /** Optional in plain-unit tests; production injects it. Signals are advisory. */
  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private RealtimePublisher realtime;

  private void signal(UUID userId, String kind, UUID notificationId) {
    if (realtime != null) {
      realtime.user(userId, kind, "USER", userId, notificationId);
    }
  }

  private boolean isMuted(UUID userId, String type, String severity) {
    if ("SECURITY_CRITICAL".equals(severity)) return false;
    return prefs.findByUserId(userId).stream()
        .anyMatch(p -> (p.getType().equals(type) || p.getType().equals("*")) && p.isMuted());
  }

  @Transactional
  public Notification send(UUID userId, String type, String severity, String scopeType, UUID scopeId,
      String titleKey, String bodyKey, String deepLink, String dedupToken) {
    if (isMuted(userId, type, severity)) return null;
    Notification n = new Notification(UuidV7.generate(), userId, type);
    n.setSeverity(severity);
    n.setScopeType(scopeType);
    n.setScopeId(scopeId);
    n.setTitleKey(titleKey);
    n.setBodyKey(bodyKey);
    n.setDeepLink(deepLink);
    n.setDedupToken(dedupToken);
    Notification saved = notifications.save(n);
    signal(userId, "NOTIFICATION_CREATED", saved.getId());
    return saved;
  }

  @Transactional
  public Notification markRead(UUID userId, UUID id) {
    Notification n = notifications.findById(id)
        .filter(x -> x.getUserId().equals(userId))
        .orElseThrow(() -> vn.smarttask.common.AppException.notFound("Notification not found"));
    n.setReadAt(OffsetDateTime.now(ZoneOffset.UTC));
    Notification saved = notifications.save(n);
    signal(userId, "NOTIFICATION_READ", saved.getId());
    return saved;
  }

  /** Account-level mark-all-read. */
  @Transactional
  public int markAllRead(UUID userId) {
    int count = 0;
    for (Notification n : notifications.findByUserIdOrderByOccurredAtDesc(userId)) {
      if (n.getReadAt() == null && !n.isRedacted()) {
        n.setReadAt(OffsetDateTime.now(ZoneOffset.UTC));
        notifications.save(n);
        count++;
      }
    }
    return count;
  }

  /** Revoke -> redact payload + clear deep link (history kept). */
  @Transactional
  public int redactOnRevoke(UUID userId, String scopeType, UUID scopeId, String reason) {
    int count = 0;
    for (Notification n : notifications.findByUserIdAndScopeTypeAndScopeId(userId, scopeType, scopeId)) {
      if (!n.isRedacted()) {
        n.setRedacted(true);
        n.setRedactedAt(OffsetDateTime.now(ZoneOffset.UTC));
        n.setRedactionReason(reason);
        n.setPayload("{}");
        n.setDeepLink(null);
        notifications.save(n);
        count++;
      }
    }
    audit.append(userId, "notification.redact", "notification", null, "ALLOWED", reason, "{}");
    return count;
  }

  @Transactional(readOnly = true)
  public List<Notification> list(UUID userId) {
    return notifications.findByUserIdOrderByOccurredAtDesc(userId);
  }
}
