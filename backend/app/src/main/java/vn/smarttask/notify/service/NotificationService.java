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
  @org.springframework.beans.factory.annotation.Autowired(required=false) private org.springframework.jdbc.core.JdbcTemplate jdbc;
  @Transactional
  public int releaseDeferred(){
    if(jdbc==null)return 0;
    var due=jdbc.queryForList("select id,user_id from notification where quiet_hours_deferred_until<=now() and not redacted order by quiet_hours_deferred_until,id limit 100 for update skip locked");
    for(var row:due){UUID id=(UUID)row.get("id");jdbc.update("update notification set quiet_hours_deferred_until=null,seq=nextval(pg_get_serial_sequence('notification','seq')) where id=?",id);signal((UUID)row.get("user_id"),"NOTIFICATION_CREATED",id);}
    return due.size();
  }
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
    int count = notifications.markAllRead(userId,OffsetDateTime.now(ZoneOffset.UTC));
    if (count > 0) signal(userId, "NOTIFICATION_READ", userId);
    return count;
  }
  @Transactional(readOnly = true)
  public long unreadCount(UUID userId) { return notifications.visibleUnread(userId); }

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

  public record InboxPage(List<Notification> items, String next, long total) {}

  /** Stable keyset pagination: concurrent inserts never shift an existing page. */
  @Transactional(readOnly = true)
  public InboxPage inbox(UUID userId, String cursor, Long sinceSeq, int limit) {
    return inbox(userId, cursor, sinceSeq, limit, "all");
  }

  @Transactional(readOnly = true)
  public InboxPage inbox(UUID userId, String cursor, Long sinceSeq, int limit, String filter) {
    if (!List.of("all", "unread", "task", "reminder", "system").contains(filter))
      throw vn.smarttask.common.AppException.badRequest("INVALID_FILTER", "Unknown notification filter");
    if (sinceSeq != null && sinceSeq < 0)
      throw vn.smarttask.common.AppException.badRequest("INVALID_SEQUENCE", "since_seq must be non-negative");
    Long before = null;
    if (cursor != null && !cursor.isBlank()) {
      UUID id;
      try { id = UUID.fromString(cursor); }
      catch (IllegalArgumentException e) { throw vn.smarttask.common.AppException.badRequest("INVALID_CURSOR", "Invalid notification cursor"); }
      before = notifications.findById(id).filter(n -> userId.equals(n.getUserId()) && !n.isRedacted())
          .map(Notification::getSeq)
          .orElseThrow(() -> vn.smarttask.common.AppException.notFound("Notification cursor not found"));
    }
    org.springframework.data.jpa.domain.Specification<Notification> scope = (root, query, cb) -> {
      var owned = cb.and(cb.equal(root.get("userId"), userId), cb.isFalse(root.get("redacted")),cb.isNull(root.get("deferredUntil")));
      var kind = cb.lower(root.<String>get("kind"));
      var task = cb.like(kind, "%task%");
      var reminder = cb.like(kind, "%remind%");
      var selected = switch (filter) {
        case "unread" -> cb.isNull(root.get("readAt"));
        case "task" -> task;
        case "reminder" -> reminder;
        case "system" -> cb.and(cb.not(task), cb.not(reminder));
        default -> cb.conjunction();
      };
      var match = cb.and(owned, selected);
      return sinceSeq == null ? match : cb.and(match, cb.greaterThan(root.get("seq"), sinceSeq));
    };
    final Long boundary = before;
    var pageScope = boundary == null ? scope : scope.and((root, query, cb) -> cb.lessThan(root.get("seq"), boundary));
    int size = Math.min(Math.max(limit, 1), 100);
    var rows = notifications.findAll(pageScope, org.springframework.data.domain.PageRequest.of(0, size + 1,
        org.springframework.data.domain.Sort.by("seq").descending())).getContent();
    boolean more = rows.size() > size;
    var items = List.copyOf(rows.subList(0, Math.min(size, rows.size())));
    return new InboxPage(items, more ? items.get(items.size()-1).getId().toString() : null, notifications.count(scope));
  }
}
