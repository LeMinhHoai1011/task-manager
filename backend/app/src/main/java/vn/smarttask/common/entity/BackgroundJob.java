package vn.smarttask.common.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "background_job")
public class BackgroundJob {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;
  @Column(name = "kind", nullable = false)
  private String kind;
  @Column(name = "status", nullable = false)
  private String status = "QUEUED";
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
  private String payload = "{}";
  @Column(name = "attempts", nullable = false)
  private int attempts = 0;
  @Column(name = "correlation_id")
  private UUID correlationId;
  @Column(name = "idempotency_key")
  private String idempotencyKey;
  // V12 worker columns (F3; additive, defaults keep old rows valid).
  @Column(name = "owner")
  private String owner;
  @Column(name = "lease_expires_at")
  private OffsetDateTime leaseExpiresAt;
  @Column(name = "max_attempts", nullable = false)
  private int maxAttempts = 5;
  @Column(name = "error_code")
  private String errorCode;
  @Column(name = "started_at")
  private OffsetDateTime startedAt;
  @Column(name = "finished_at")
  private OffsetDateTime finishedAt;
  @Column(name = "next_attempt_at", nullable = false)
  private OffsetDateTime nextAttemptAt;
  @Column(name = "timeout_ms", nullable = false)
  private long timeoutMs = 300000L;
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected BackgroundJob() {}
  public BackgroundJob(UUID id, String kind, String payload) {
    this.id = id; this.kind = kind; this.payload = payload;
  }
  @PrePersist
  void prePersist() {
    OffsetDateTime now = OffsetDateTime.now(java.time.ZoneOffset.UTC);
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
    if (nextAttemptAt == null) nextAttemptAt = now;
  }
  @PreUpdate
  void preUpdate() { updatedAt = OffsetDateTime.now(java.time.ZoneOffset.UTC); }
  public UUID getId() { return id; }
  public String getKind() { return kind; }
  public String getStatus() { return status; }
  public void setStatus(String v) { this.status = v; }
  public String getPayload() { return payload; }
  public int getAttempts() { return attempts; }
  public void setAttempts(int v) { this.attempts = v; }
  public UUID getCorrelationId() { return correlationId; }
  public void setCorrelationId(UUID v) { this.correlationId = v; }
  public String getIdempotencyKey() { return idempotencyKey; }
  public void setIdempotencyKey(String v) { this.idempotencyKey = v; }
  public String getOwner() { return owner; }
  public void setOwner(String v) { this.owner = v; }
  public OffsetDateTime getLeaseExpiresAt() { return leaseExpiresAt; }
  public void setLeaseExpiresAt(OffsetDateTime v) { this.leaseExpiresAt = v; }
  public int getMaxAttempts() { return maxAttempts; }
  public void setMaxAttempts(int v) { this.maxAttempts = v; }
  public String getErrorCode() { return errorCode; }
  public void setErrorCode(String v) { this.errorCode = v; }
  public OffsetDateTime getStartedAt() { return startedAt; }
  public void setStartedAt(OffsetDateTime v) { this.startedAt = v; }
  public OffsetDateTime getFinishedAt() { return finishedAt; }
  public void setFinishedAt(OffsetDateTime v) { this.finishedAt = v; }
  public OffsetDateTime getNextAttemptAt() { return nextAttemptAt; }
  public void setNextAttemptAt(OffsetDateTime v) { this.nextAttemptAt = v; }
  public long getTimeoutMs() { return timeoutMs; }
  public void setTimeoutMs(long v) { this.timeoutMs = v; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
