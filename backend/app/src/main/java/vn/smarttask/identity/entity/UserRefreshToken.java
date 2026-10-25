package vn.smarttask.identity.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_refresh_token")
public class UserRefreshToken {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;
  @Column(name = "user_id", nullable = false)
  private UUID userId;
  @Column(name = "session_id")
  private UUID sessionId;
  @Column(name = "rotated_from_id")
  private UUID rotatedFromId;
  @Column(name = "token_hash", nullable = false, unique = true)
  private String tokenHash;
  @Column(name = "revoked", nullable = false)
  private boolean revoked = false;
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;
  @Column(name = "expires_at", nullable = false)
  private OffsetDateTime expiresAt;

  protected UserRefreshToken() {}
  public UserRefreshToken(UUID id, UUID userId, UUID sessionId, String tokenHash, OffsetDateTime expiresAt) {
    this.id = id; this.userId = userId; this.sessionId = sessionId;
    this.tokenHash = tokenHash; this.expiresAt = expiresAt;
  }
  @PrePersist
  void prePersist() {
    if (createdAt == null) createdAt = OffsetDateTime.now(java.time.ZoneOffset.UTC);
  }
  public UUID getId() { return id; }
  public UUID getUserId() { return userId; }
  public UUID getSessionId() { return sessionId; }
  public void setSessionId(UUID v) { this.sessionId = v; }
  public UUID getRotatedFromId() { return rotatedFromId; }
  public void setRotatedFromId(UUID v) { this.rotatedFromId = v; }
  public String getTokenHash() { return tokenHash; }
  public boolean isRevoked() { return revoked; }
  public void setRevoked(boolean v) { this.revoked = v; }
  public OffsetDateTime getExpiresAt() { return expiresAt; }
}
