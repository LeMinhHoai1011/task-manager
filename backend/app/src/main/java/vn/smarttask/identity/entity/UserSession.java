package vn.smarttask.identity.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_session")
public class UserSession {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;
  @Column(name = "user_id", nullable = false)
  private UUID userId;
  @Column(name = "session_token_hash", nullable = false, unique = true)
  private String sessionTokenHash;
  @Column(name = "active", nullable = false)
  private boolean active = true;
  @Column(name = "status", nullable = false)
  private String status = "ACTIVE";
  @Column(name = "device_label")
  private String deviceLabel;
  @Column(name = "client_type")
  private String clientType;
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;
  @Column(name = "expires_at", nullable = false)
  private OffsetDateTime expiresAt;

  protected UserSession() {}
  public UserSession(UUID id, UUID userId, String tokenHash, OffsetDateTime expiresAt) {
    this.id = id; this.userId = userId; this.sessionTokenHash = tokenHash; this.expiresAt = expiresAt;
  }
  @PrePersist
  void prePersist() {
    if (createdAt == null) createdAt = OffsetDateTime.now(java.time.ZoneOffset.UTC);
  }
  public UUID getId() { return id; }
  public void setId(UUID v) { this.id = v; }
  public UUID getUserId() { return userId; }
  public String getSessionTokenHash() { return sessionTokenHash; }
  public void setSessionTokenHash(String v) { this.sessionTokenHash = v; }
  public boolean isActive() { return active; }
  public void setActive(boolean v) { this.active = v; }
  public String getStatus() { return status; }
  public void setStatus(String v) { this.status = v; }
  public String getDeviceLabel() { return deviceLabel; }
  public void setDeviceLabel(String v) { this.deviceLabel = v; }
  public String getClientType() { return clientType; }
  public void setClientType(String v) { this.clientType = v; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public OffsetDateTime getExpiresAt() { return expiresAt; }
  public void setExpiresAt(OffsetDateTime v) { this.expiresAt = v; }
}
