package vn.smarttask.notify.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * F3 delivery-attempt log over the V5 {@code delivery_attempt} table
 * (append-only provider-stub log). No migration needed: V5 created the table.
 */
@Entity
@Table(name = "delivery_attempt")
public class DeliveryAttempt {
  @Id private UUID id;
  @Column(name = "notification_id", nullable = false) private UUID notificationId;
  @Column(name = "channel", nullable = false) private String channel;
  @Column(name = "provider") private String provider;
  @Column(name = "status", nullable = false) private String status = "QUEUED";
  @Column(name = "attempt_no", nullable = false) private int attemptNo = 1;
  @Column(name = "error_code") private String errorCode;
  @Column(name = "error_detail") private String errorDetail;
  @Column(name = "dedup_token", nullable = false, unique = true) private String dedupToken;
  @Column(name = "correlation_id") private UUID correlationId;
  @Column(name = "attempted_at", nullable = false) private OffsetDateTime attemptedAt;

  protected DeliveryAttempt() {}

  public DeliveryAttempt(UUID id, UUID notificationId, String channel, String dedupToken) {
    this.id = id;
    this.notificationId = notificationId;
    this.channel = channel;
    this.dedupToken = dedupToken;
  }

  @PrePersist
  void prePersist() {
    if (attemptedAt == null) attemptedAt = OffsetDateTime.now(ZoneOffset.UTC);
  }

  public UUID getId() { return id; }
  public UUID getNotificationId() { return notificationId; }
  public String getChannel() { return channel; }
  public String getProvider() { return provider; }
  public void setProvider(String v) { this.provider = v; }
  public String getStatus() { return status; }
  public void setStatus(String v) { this.status = v; }
  public int getAttemptNo() { return attemptNo; }
  public void setAttemptNo(int v) { this.attemptNo = v; }
  public String getErrorCode() { return errorCode; }
  public void setErrorCode(String v) { this.errorCode = v; }
  public String getErrorDetail() { return errorDetail; }
  public void setErrorDetail(String v) { this.errorDetail = v; }
  public String getDedupToken() { return dedupToken; }
  public UUID getCorrelationId() { return correlationId; }
  public void setCorrelationId(UUID v) { this.correlationId = v; }
  public OffsetDateTime getAttemptedAt() { return attemptedAt; }
}
