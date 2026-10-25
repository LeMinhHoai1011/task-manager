package vn.smarttask.project.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "project")
public class Project {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;
  @Column(name = "workspace_id", nullable = false)
  private UUID workspaceId;
  @Column(name = "name", nullable = false)
  private String name;
  @Column(name = "description")
  private String description;
  @Column(name = "created_by")
  private UUID createdBy;
  @Column(name = "status", nullable = false)
  private String status = "PLANNING";
  @Column(name = "previous_status")
  private String previousStatus;
  @Column(name = "approval_enabled", nullable = false)
  private boolean approvalEnabled = false;
  @Column(name = "allow_member_create_task", nullable = false)
  private boolean allowMemberCreateTask = true;
  @Version
  @Column(name = "version", nullable = false)
  private int version = 1;
  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;
  @Column(name = "deleted_by")
  private UUID deletedBy;
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected Project() {}
  public Project(UUID id, UUID workspaceId, String name, UUID createdBy) {
    this.id = id; this.workspaceId = workspaceId; this.name = name; this.createdBy = createdBy;
  }
  @PrePersist
  void prePersist() {
    OffsetDateTime now = OffsetDateTime.now(java.time.ZoneOffset.UTC);
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
  }
  @PreUpdate
  void preUpdate() { updatedAt = OffsetDateTime.now(java.time.ZoneOffset.UTC); }

  public UUID getId() { return id; }
  public UUID getWorkspaceId() { return workspaceId; }
  public String getName() { return name; }
  public void setName(String v) { this.name = v; }
  public String getDescription() { return description; }
  public void setDescription(String v) { this.description = v; }
  public UUID getCreatedBy() { return createdBy; }
  public String getStatus() { return status; }
  public void setStatus(String v) { this.status = v; }
  public String getPreviousStatus() { return previousStatus; }
  public void setPreviousStatus(String v) { this.previousStatus = v; }
  public boolean isApprovalEnabled() { return approvalEnabled; }
  public void setApprovalEnabled(boolean v) { this.approvalEnabled = v; }
  public boolean isAllowMemberCreateTask() { return allowMemberCreateTask; }
  public void setAllowMemberCreateTask(boolean v) { this.allowMemberCreateTask = v; }
  public int getVersion() { return version; }
  public OffsetDateTime getDeletedAt() { return deletedAt; }
  public void setDeletedAt(OffsetDateTime v) { this.deletedAt = v; }
  public UUID getDeletedBy() { return deletedBy; }
  public void setDeletedBy(UUID v) { this.deletedBy = v; }
}
