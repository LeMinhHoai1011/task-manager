package vn.smarttask.note.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "note")
public class Note {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "workspace_id")
  private UUID workspaceId;

  @Column(name = "project_id")
  private UUID projectId;

  @Column(name = "task_id")
  private UUID taskId;

  @Column(name = "owner_user_id", nullable = false)
  private UUID ownerUserId;

  @Column(name = "scope", nullable = false)
  private String scope = "PERSONAL";

  @Column(name = "title", nullable = false)
  private String title;

  @Column(name = "content", nullable = false, columnDefinition = "TEXT")
  private String content = "";

  @Column(name = "tags")
  private String tags;

  @Column(name = "is_pinned", nullable = false)
  private boolean isPinned = false;

  @Version
  @Column(name = "version", nullable = false)
  private long version = 1;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;

  protected Note() {}

  public Note(UUID id, UUID ownerUserId, String scope, String title, String content) {
    this.id = id;
    this.ownerUserId = ownerUserId;
    this.scope = scope != null ? scope : "PERSONAL";
    this.title = title;
    this.content = content != null ? content : "";
  }

  @PrePersist
  void prePersist() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
  }

  public UUID getId() { return id; }
  public UUID getWorkspaceId() { return workspaceId; }
  public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }
  public UUID getProjectId() { return projectId; }
  public void setProjectId(UUID projectId) { this.projectId = projectId; }
  public UUID getTaskId() { return taskId; }
  public void setTaskId(UUID taskId) { this.taskId = taskId; }
  public UUID getOwnerUserId() { return ownerUserId; }
  public String getScope() { return scope; }
  public void setScope(String scope) { this.scope = scope; }
  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }
  public String getContent() { return content; }
  public void setContent(String content) { this.content = content; }
  public String getTags() { return tags; }
  public void setTags(String tags) { this.tags = tags; }
  public boolean isPinned() { return isPinned; }
  public void setPinned(boolean pinned) { isPinned = pinned; }
  public long getVersion() { return version; }
  public void setVersion(long version) { this.version = version; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public OffsetDateTime getUpdatedAt() { return updatedAt; }
  public OffsetDateTime getDeletedAt() { return deletedAt; }
  public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }
}
