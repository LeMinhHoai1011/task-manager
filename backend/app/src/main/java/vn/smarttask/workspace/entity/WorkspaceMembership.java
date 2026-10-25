package vn.smarttask.workspace.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "workspace_membership",
    uniqueConstraints = @UniqueConstraint(columnNames = {"workspace_id", "user_id"}))
public class WorkspaceMembership {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;
  @Column(name = "workspace_id", nullable = false)
  private UUID workspaceId;
  @Column(name = "user_id", nullable = false)
  private UUID userId;
  @Column(name = "role", nullable = false)
  private String role;
  @Column(name = "status", nullable = false)
  private String status = "ACTIVE";
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected WorkspaceMembership() {}
  public WorkspaceMembership(UUID id, UUID workspaceId, UUID userId, String role) {
    this.id = id; this.workspaceId = workspaceId; this.userId = userId; this.role = role;
  }
  @PrePersist
  void prePersist() {
    if (createdAt == null) createdAt = OffsetDateTime.now(java.time.ZoneOffset.UTC);
  }
  public UUID getId() { return id; }
  public UUID getWorkspaceId() { return workspaceId; }
  public UUID getUserId() { return userId; }
  public String getRole() { return role; }
  public void setRole(String v) { this.role = v; }
  public String getStatus() { return status; }
  public void setStatus(String v) { this.status = v; }
}
