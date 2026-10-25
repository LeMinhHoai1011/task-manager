package vn.smarttask.identity.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "recovery_code")
public class RecoveryCode {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;
  @Column(name = "user_id", nullable = false)
  private UUID userId;
  @Column(name = "code_hash", nullable = false)
  private String codeHash;
  @Column(name = "used", nullable = false)
  private boolean used = false;
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;
  @Column(name = "expires_at")
  private OffsetDateTime expiresAt;

  protected RecoveryCode() {}
  public RecoveryCode(UUID id, UUID userId, String codeHash, OffsetDateTime expiresAt) {
    this.id = id; this.userId = userId; this.codeHash = codeHash; this.expiresAt = expiresAt;
  }
  @PrePersist
  void prePersist() {
    if (createdAt == null) createdAt = OffsetDateTime.now(java.time.ZoneOffset.UTC);
  }
  public UUID getId() { return id; }
  public UUID getUserId() { return userId; }
  public String getCodeHash() { return codeHash; }
  public boolean isUsed() { return used; }
  public void setUsed(boolean v) { this.used = v; }
}
