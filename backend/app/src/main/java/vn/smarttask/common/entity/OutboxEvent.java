package vn.smarttask.common.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_event")
public class OutboxEvent {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;
  @Column(name = "aggregate_type", nullable = false)
  private String aggregateType;
  @Column(name = "aggregate_id", nullable = false)
  private UUID aggregateId;
  @Column(name = "type", nullable = false)
  private String type;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
  private String payload = "{}";
  @Column(name = "published", nullable = false)
  private boolean published = false;
  @Column(name = "correlation_id")
  private UUID correlationId;
  @Column(name = "idempotency_key")
  private String idempotencyKey;
  // V12 relay columns (F3 workers; additive, defaults keep old rows valid).
  @Column(name = "status", nullable = false)
  private String status = "PENDING";
  @Column(name = "handler")
  private String handler;
  @Column(name = "attempts", nullable = false)
  private int attempts = 0;
  @Column(name = "next_attempt_at", nullable = false)
  private OffsetDateTime nextAttemptAt;
  @Column(name = "claimed_by")
  private String claimedBy;
  @Column(name = "claimed_until")
  private OffsetDateTime claimedUntil;
  @Column(name = "last_error")
  private String lastError;
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected OutboxEvent() {}
  public OutboxEvent(UUID id, String aggregateType, UUID aggregateId, String type, String payload) {
    this.id = id; this.aggregateType = aggregateType; this.aggregateId = aggregateId;
    this.type = type; this.payload = payload;
    this.handler = type;
  }
  @PrePersist
  void prePersist() {
    if (createdAt == null) createdAt = OffsetDateTime.now(java.time.ZoneOffset.UTC);
    if (nextAttemptAt == null) nextAttemptAt = OffsetDateTime.now(java.time.ZoneOffset.UTC);
    if (handler == null) handler = type;
    if (status == null) status = "PENDING";
  }
  public UUID getId() { return id; }
  public String getAggregateType() { return aggregateType; }
  public UUID getAggregateId() { return aggregateId; }
  public String getType() { return type; }
  public String getPayload() { return payload; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public boolean isPublished() { return published; }
  public void setPublished(boolean v) { this.published = v; }
  public UUID getCorrelationId() { return correlationId; }
  public void setCorrelationId(UUID v) { this.correlationId = v; }
  public String getIdempotencyKey() { return idempotencyKey; }
  public void setIdempotencyKey(String v) { this.idempotencyKey = v; }
  public String getStatus() { return status; }
  public void setStatus(String v) { this.status = v; }
  public String getHandler() { return handler; }
  public void setHandler(String v) { this.handler = v; }
  public int getAttempts() { return attempts; }
  public void setAttempts(int v) { this.attempts = v; }
  public OffsetDateTime getNextAttemptAt() { return nextAttemptAt; }
  public void setNextAttemptAt(OffsetDateTime v) { this.nextAttemptAt = v; }
  public String getClaimedBy() { return claimedBy; }
  public void setClaimedBy(String v) { this.claimedBy = v; }
  public OffsetDateTime getClaimedUntil() { return claimedUntil; }
  public void setClaimedUntil(OffsetDateTime v) { this.claimedUntil = v; }
  public String getLastError() { return lastError; }
  public void setLastError(String v) { this.lastError = v; }
}
