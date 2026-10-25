package vn.smarttask.project.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "project_membership",
    uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "user_id"}))
public class ProjectMembership {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;
  @Column(name = "project_id", nullable = false)
  private UUID projectId;
  @Column(name = "user_id", nullable = false)
  private UUID userId;
  @Column(name = "role", nullable = false)
  private String role;
  @Column(name = "status", nullable = false)
  private String status = "ACTIVE";
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected ProjectMembership() {}
  public ProjectMembership(UUID id, UUID projectId, UUID userId, String role) {
    this.id = id; this.projectId = projectId; this.userId = userId; this.role = role;
  }
  @PrePersist
  void prePersist() {
    if (createdAt == null) createdAt = OffsetDateTime.now(java.time.ZoneOffset.UTC);
  }
  public UUID getId() { return id; }
  public UUID getProjectId() { return projectId; }
  public UUID getUserId() { return userId; }
  public String getRole() { return role; }
  public void setRole(String v) { this.role = v; }
  public String getStatus() { return status; }
  public void setStatus(String v) { this.status = v; }
}
