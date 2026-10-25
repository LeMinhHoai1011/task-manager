package vn.smarttask.identity.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "reauth_ticket")
public class ReauthTicket {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;
  @Column(name = "user_id", nullable = false)
  private UUID userId;
  @Column(name = "action", nullable = false)
  private String action;
  @Column(name = "target_id")
  private UUID targetId;
  @Column(name = "used", nullable = false)
  private boolean used = false;
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;
  @Column(name = "expires_at", nullable = false)
  private OffsetDateTime expiresAt;

  protected ReauthTicket() {}
  public ReauthTicket(UUID id, UUID userId, String action, UUID targetId, OffsetDateTime expiresAt) {
    this.id = id; this.userId = userId; this.action = action;
    this.targetId = targetId; this.expiresAt = expiresAt;
  }
  @PrePersist
  void prePersist() {
    if (createdAt == null) createdAt = OffsetDateTime.now(java.time.ZoneOffset.UTC);
  }
  public UUID getId() { return id; }
  public UUID getUserId() { return userId; }
  public String getAction() { return action; }
  public UUID getTargetId() { return targetId; }
  public boolean isUsed() { return used; }
  public void setUsed(boolean v) { this.used = v; }
  public OffsetDateTime getExpiresAt() { return expiresAt; }
}
