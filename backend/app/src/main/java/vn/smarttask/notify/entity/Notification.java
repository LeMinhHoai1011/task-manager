package vn.smarttask.notify.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "notification")
public class Notification {
  @Id private UUID id;
  @Column(name = "user_id", nullable = false) private UUID userId;
  @Column(name = "kind", nullable = false) private String kind;
  @Column(name = "type") private String type;
  @Column(name = "severity", nullable = false) private String severity = "NORMAL";
  @Column(name = "scope_type", nullable = false) private String scopeType = "SYSTEM";
  @Column(name = "scope_id") private UUID scopeId;
  @Column(name = "title_key") private String titleKey;
  @Column(name = "body_key") private String bodyKey;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", nullable = false, columnDefinition = "jsonb") private String payload = "{}";
  @Column(name = "deep_link") private String deepLink;
  @Column(name = "redacted", nullable = false) private boolean redacted = false;
  @Column(name = "redacted_at") private OffsetDateTime redactedAt;
  @Column(name = "redaction_reason") private String redactionReason;
  @Column(name = "read", nullable = false) private boolean read = false;
  @Column(name = "read_at") private OffsetDateTime readAt;
  @Column(name = "occurred_at") private OffsetDateTime occurredAt;
  @Column(name = "dedup_token") private String dedupToken;
  @Column(name = "quiet_hours_deferred_until") private OffsetDateTime deferredUntil;
  @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
  // V5 BIGSERIAL seq: DB-generated, read-only mapping (ddl-auto=validate alignment).
  @Column(name = "seq", insertable = false, updatable = false)
  private Long seq;

  protected Notification() {}
  public Notification(UUID id, UUID userId, String kind) {
    this.id = id; this.userId = userId; this.kind = kind; this.type = kind;
  }
  @PrePersist void prePersist() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    if (createdAt == null) createdAt = now;
    if (occurredAt == null) occurredAt = now;
  }
  public UUID getId() { return id; }
  public UUID getUserId() { return userId; }
  public String getKind() { return kind; }
  public String getSeverity() { return severity; }
  public void setSeverity(String v) { this.severity = v; }
  public String getScopeType() { return scopeType; }
  public void setScopeType(String v) { this.scopeType = v; }
  public UUID getScopeId() { return scopeId; }
  public void setScopeId(UUID v) { this.scopeId = v; }
  public String getTitleKey() { return titleKey; }
  public void setTitleKey(String v) { this.titleKey = v; }
  public String getBodyKey() { return bodyKey; }
  public void setBodyKey(String v) { this.bodyKey = v; }
  public String getPayload() { return payload; }
  public void setPayload(String v) { this.payload = v; }
  public String getDeepLink() { return deepLink; }
  public void setDeepLink(String v) { this.deepLink = v; }
  public boolean isRedacted() { return redacted; }
  public void setRedacted(boolean v) { this.redacted = v; }
  public void setRedactedAt(OffsetDateTime v) { this.redactedAt = v; }
  public void setRedactionReason(String v) { this.redactionReason = v; }
  public boolean isRead() { return read; }
  public OffsetDateTime getReadAt() { return readAt; }
  public void setReadAt(OffsetDateTime v) { this.readAt = v; this.read = v != null; }
  public OffsetDateTime getOccurredAt() { return occurredAt; }
  public String getDedupToken() { return dedupToken; }
  public void setDedupToken(String v) { this.dedupToken = v; }
  public OffsetDateTime getDeferredUntil() { return deferredUntil; }
  public void setDeferredUntil(OffsetDateTime v) { this.deferredUntil = v; }
  public Long getSeq() { return seq; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
}
