package vn.smarttask.notify.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * F4 {@code device_push_registration} row (table from V5, provider stub):
 * a push registration is bound to the login session
 * ({@code ON DELETE CASCADE}); revoking the session invalidates the
 * registration immediately. Only the SHA-256 of the push token is stored
 * ({@code push_token_hash} UNIQUE) — the raw token never touches the DB.
 */
@Entity
@Table(name = "device_push_registration")
public class DevicePushRegistration {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;
  @Column(name = "user_id", nullable = false)
  private UUID userId;
  @Column(name = "session_id", nullable = false)
  private UUID sessionId;
  @Column(name = "platform", nullable = false)
  private String platform;
  @Column(name = "push_token_hash", nullable = false)
  private String pushTokenHash;
  @Column(name = "device_label")
  private String deviceLabel;
  @Column(name = "status", nullable = false)
  private String status = "ACTIVE";
  @Column(name = "registered_at", nullable = false)
  private OffsetDateTime registeredAt;
  @Column(name = "unregistered_at")
  private OffsetDateTime unregisteredAt;
  @Column(name = "last_success_at")
  private OffsetDateTime lastSuccessAt;
  @Column(name = "failure_count", nullable = false)
  private int failureCount = 0;

  protected DevicePushRegistration() {}

  public DevicePushRegistration(UUID id, UUID userId, UUID sessionId, String platform,
      String pushTokenHash, String deviceLabel) {
    this.id = id;
    this.userId = userId;
    this.sessionId = sessionId;
    this.platform = platform;
    this.pushTokenHash = pushTokenHash;
    this.deviceLabel = deviceLabel;
  }

  @PrePersist
  void prePersist() {
    if (registeredAt == null) registeredAt = OffsetDateTime.now(ZoneOffset.UTC);
  }

  public UUID getId() { return id; }
  public UUID getUserId() { return userId; }
  public UUID getSessionId() { return sessionId; }
  public String getPlatform() { return platform; }
  public String getPushTokenHash() { return pushTokenHash; }
  public String getDeviceLabel() { return deviceLabel; }
  public void setDeviceLabel(String v) { this.deviceLabel = v; }
  public String getStatus() { return status; }
  public void setStatus(String v) { this.status = v; }
  public OffsetDateTime getRegisteredAt() { return registeredAt; }
  public OffsetDateTime getUnregisteredAt() { return unregisteredAt; }
  public void setUnregisteredAt(OffsetDateTime v) { this.unregisteredAt = v; }
  public OffsetDateTime getLastSuccessAt() { return lastSuccessAt; }
  public int getFailureCount() { return failureCount; }
}
