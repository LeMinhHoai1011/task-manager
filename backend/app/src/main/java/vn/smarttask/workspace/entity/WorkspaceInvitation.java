package vn.smarttask.workspace.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "workspace_invitation")
public class WorkspaceInvitation {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;
  @Column(name = "workspace_id", nullable = false)
  private UUID workspaceId;
  @Column(name = "email", nullable = false, columnDefinition = "citext")
  private String email;
  @Column(name = "role", nullable = false)
  private String role;
  @Column(name = "token_hash", nullable = false, unique = true)
  private String tokenHash;
  @Column(name = "accepted", nullable = false)
  private boolean accepted = false;
  @Column(name = "status", nullable = false)
  private String status = "PENDING";
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;
  @Column(name = "expires_at", nullable = false)
  private OffsetDateTime expiresAt;

  protected WorkspaceInvitation() {}
  public WorkspaceInvitation(UUID id, UUID workspaceId, String email, String role,
      String tokenHash, OffsetDateTime expiresAt) {
    this.id = id; this.workspaceId = workspaceId; this.email = email;
    this.role = role; this.tokenHash = tokenHash; this.expiresAt = expiresAt;
  }
  @PrePersist
  void prePersist() {
    if (createdAt == null) createdAt = OffsetDateTime.now(java.time.ZoneOffset.UTC);
  }
  public UUID getId() { return id; }
  public OffsetDateTime getCreatedAt(){return createdAt;}
  public UUID getWorkspaceId() { return workspaceId; }
  public String getEmail() { return email; }
  public String getRole() { return role; }
  public String getTokenHash() { return tokenHash; }
  public boolean isAccepted() { return accepted; }
  public void setAccepted(boolean v) { this.accepted = v; }
  public String getStatus() { return status; }
  public void setStatus(String v) { this.status = v; }
  public OffsetDateTime getExpiresAt() { return expiresAt; }
}
