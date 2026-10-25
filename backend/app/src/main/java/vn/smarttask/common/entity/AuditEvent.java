package vn.smarttask.common.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_event")
public class AuditEvent {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;
  @Column(name = "actor_user_id")
  private UUID actorUserId;
  @Column(name = "action", nullable = false)
  private String action;
  @Column(name = "entity_type", nullable = false)
  private String entityType;
  @Column(name = "entity_id")
  private UUID entityId;
  @Column(name = "target_type")
  private String targetType;
  @Column(name = "target_id")
  private UUID targetId;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
  private String payload = "{}";
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "before_json", columnDefinition = "jsonb")
  private String beforeJson;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "after_json", columnDefinition = "jsonb")
  private String afterJson;
  @Column(name = "reason")
  private String reason;
  @Column(name = "outcome", nullable = false)
  private String outcome = "ALLOWED";
  @Column(name = "scope_type")
  private String scopeType;
  @Column(name = "scope_id")
  private UUID scopeId;
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;
  @Column(name = "occurred_at", nullable = false)
  private OffsetDateTime occurredAt;

  protected AuditEvent() {}
  public AuditEvent(UUID id, UUID actorUserId, String action, String entityType, UUID entityId,
      String outcome, String reason) {
    this.id = id; this.actorUserId = actorUserId; this.action = action;
    this.entityType = entityType; this.entityId = entityId;
    this.outcome = outcome; this.reason = reason;
  }
  @PrePersist
  void prePersist() {
    OffsetDateTime now = OffsetDateTime.now(java.time.ZoneOffset.UTC);
    if (createdAt == null) createdAt = now;
    if (occurredAt == null) occurredAt = now;
  }
  public UUID getId() { return id; }
  public UUID getActorUserId() { return actorUserId; }
  public String getAction() { return action; }
  public String getOutcome() { return outcome; }
  public String getReason() { return reason; }
  public void setPayload(String v) { this.payload = v; }
  public void setTargetType(String v) { this.targetType = v; }
  public void setTargetId(UUID v) { this.targetId = v; }
  public void setBeforeJson(String v) { this.beforeJson = v; }
  public void setAfterJson(String v) { this.afterJson = v; }
  public void setScopeType(String v) { this.scopeType = v; }
  public void setScopeId(UUID v) { this.scopeId = v; }
}
