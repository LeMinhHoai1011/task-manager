package vn.smarttask.security.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * F1 global permission grant row. Deny-by-default: absence of a live row
 * means no permission. A row is live when {@code revokedAt == null} and
 * ({@code expiresAt == null} or in the future).
 */
@Entity
@Table(name = "global_permission_grant")
public class GlobalPermissionGrant {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;
  @Column(name = "user_id", nullable = false)
  private UUID userId;
  @Column(name = "code", nullable = false)
  private String code;
  @Column(name = "scope_type", nullable = false)
  private String scopeType = "GLOBAL";
  @Column(name = "scope_id")
  private UUID scopeId;
  @Column(name = "granted_by")
  private UUID grantedBy;
  @Column(name = "reason")
  private String reason;
  @Column(name = "expires_at")
  private OffsetDateTime expiresAt;
  @Column(name = "revoked_at")
  private OffsetDateTime revokedAt;
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected GlobalPermissionGrant() {}

  public GlobalPermissionGrant(UUID id, UUID userId, String code, String scopeType,
      UUID scopeId, UUID grantedBy, String reason, OffsetDateTime expiresAt) {
    this.id = id;
    this.userId = userId;
    this.code = code;
    this.scopeType = scopeType == null ? "GLOBAL" : scopeType;
    this.scopeId = scopeId;
    this.grantedBy = grantedBy;
    this.reason = reason;
    this.expiresAt = expiresAt;
  }

  @PrePersist
  void prePersist() {
    if (createdAt == null) createdAt = OffsetDateTime.now(ZoneOffset.UTC);
  }

  public boolean isLive(OffsetDateTime now) {
    if (revokedAt != null) return false;
    return expiresAt == null || now.isBefore(expiresAt);
  }

  public UUID getId() { return id; }
  public UUID getUserId() { return userId; }
  public String getCode() { return code; }
  public String getScopeType() { return scopeType; }
  public UUID getScopeId() { return scopeId; }
  public UUID getGrantedBy() { return grantedBy; }
  public String getReason() { return reason; }
  public void setReason(String v) { this.reason = v; }
  public OffsetDateTime getExpiresAt() { return expiresAt; }
  public OffsetDateTime getRevokedAt() { return revokedAt; }
  public void setRevokedAt(OffsetDateTime v) { this.revokedAt = v; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
}
