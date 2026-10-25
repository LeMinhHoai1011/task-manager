package vn.smarttask.task.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "task")
public class Task {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;
  @Column(name = "project_id", nullable = false)
  private UUID projectId;
  @Column(name = "parent_task_id")
  private UUID parentTaskId;
  @Column(name = "depth", nullable = false)
  private int depth = 0;
  @Column(name = "title", nullable = false)
  private String title;
  @Column(name = "description")
  private String description;
  @Column(name = "state", nullable = false)
  private String state = "TODO";
  @Column(name = "blocked_flag", nullable = false)
  private boolean blockedFlag = false;
  @Column(name = "blocked_reason")
  private String blockedReason;
  @Column(name = "priority", nullable = false)
  private String priority = "MEDIUM";
  @Column(name = "start_at")
  private OffsetDateTime startAt;
  @Column(name = "due_at")
  private OffsetDateTime dueAt;
  @Column(name = "due_timezone")
  private String dueTimezone;
  @Column(name = "due_is_date_only", nullable = false)
  private boolean dueIsDateOnly = false;
  @Column(name = "milestone_id")
  private UUID milestoneId;
  @Column(name = "version", nullable = false)
  private int version = 1;
  @Column(name = "created_by")
  private UUID createdBy;
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;
  @Column(name = "deleted_by")
  private UUID deletedBy;
  @Column(name = "delete_group_id")
  private UUID deleteGroupId;
  @Column(name = "completed_at")
  private OffsetDateTime completedAt;
  @Column(name = "completed_by")
  private UUID completedBy;
  @Column(name = "cancelled_at")
  private OffsetDateTime cancelledAt;
  @Column(name = "cancelled_by")
  private UUID cancelledBy;
  @Column(name = "cancelled_reason")
  private String cancelledReason;
  @Column(name = "reopened_at")
  private OffsetDateTime reopenedAt;
  @Column(name = "reopened_by")
  private UUID reopenedBy;
  @Column(name = "reopened_reason")
  private String reopenedReason;
  @Column(name = "allow_member_create_task", nullable = false)
  private boolean allowMemberCreateTask = false;

  protected Task() {}
  public Task(UUID id, UUID projectId, String title, UUID createdBy) {
    this.id = id; this.projectId = projectId; this.title = title; this.createdBy = createdBy;
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
  public UUID getProjectId() { return projectId; }
  public UUID getParentTaskId() { return parentTaskId; }
  public void setParentTaskId(UUID v) { this.parentTaskId = v; }
  public int getDepth() { return depth; }
  public void setDepth(int v) { this.depth = v; }
  public String getTitle() { return title; }
  public void setTitle(String v) { this.title = v; }
  public String getDescription() { return description; }
  public void setDescription(String v) { this.description = v; }
  public String getState() { return state; }
  public void setState(String v) { this.state = v; }
  public boolean isBlockedFlag() { return blockedFlag; }
  public void setBlockedFlag(boolean v) { this.blockedFlag = v; }
  public String getBlockedReason() { return blockedReason; }
  public void setBlockedReason(String v) { this.blockedReason = v; }
  public String getPriority() { return priority; }
  public void setPriority(String v) { this.priority = v; }
  public OffsetDateTime getStartAt() { return startAt; }
  public void setStartAt(OffsetDateTime v) { this.startAt = v; }
  public OffsetDateTime getDueAt() { return dueAt; }
  public void setDueAt(OffsetDateTime v) { this.dueAt = v; }
  public String getDueTimezone() { return dueTimezone; }
  public void setDueTimezone(String v) { this.dueTimezone = v; }
  public boolean isDueIsDateOnly() { return dueIsDateOnly; }
  public void setDueIsDateOnly(boolean v) { this.dueIsDateOnly = v; }
  public UUID getMilestoneId() { return milestoneId; }
  public void setMilestoneId(UUID v) { this.milestoneId = v; }
  public int getVersion() { return version; }
  public void setVersion(int v) { this.version = v; }
  public UUID getCreatedBy() { return createdBy; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public OffsetDateTime getUpdatedAt() { return updatedAt; }
  public OffsetDateTime getDeletedAt() { return deletedAt; }
  public void setDeletedAt(OffsetDateTime v) { this.deletedAt = v; }
  public UUID getDeletedBy() { return deletedBy; }
  public void setDeletedBy(UUID v) { this.deletedBy = v; }
  public UUID getDeleteGroupId() { return deleteGroupId; }
  public void setDeleteGroupId(UUID v) { this.deleteGroupId = v; }
  public OffsetDateTime getCompletedAt() { return completedAt; }
  public void setCompletedAt(OffsetDateTime v) { this.completedAt = v; }
  public UUID getCompletedBy() { return completedBy; }
  public void setCompletedBy(UUID v) { this.completedBy = v; }
  public OffsetDateTime getCancelledAt() { return cancelledAt; }
  public void setCancelledAt(OffsetDateTime v) { this.cancelledAt = v; }
  public UUID getCancelledBy() { return cancelledBy; }
  public void setCancelledBy(UUID v) { this.cancelledBy = v; }
  public String getCancelledReason() { return cancelledReason; }
  public void setCancelledReason(String v) { this.cancelledReason = v; }
  public OffsetDateTime getReopenedAt() { return reopenedAt; }
  public void setReopenedAt(OffsetDateTime v) { this.reopenedAt = v; }
  public UUID getReopenedBy() { return reopenedBy; }
  public void setReopenedBy(UUID v) { this.reopenedBy = v; }
  public String getReopenedReason() { return reopenedReason; }
  public void setReopenedReason(String v) { this.reopenedReason = v; }
}
