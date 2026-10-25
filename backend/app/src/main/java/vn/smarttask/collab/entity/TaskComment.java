package vn.smarttask.collab.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "comment")
public class TaskComment {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;
  @Column(name = "task_id", nullable = false)
  private UUID taskId;
  @Column(name = "parent_comment_id")
  private UUID parentCommentId;
  @Column(name = "depth", nullable = false)
  private int depth = 0;
  @Column(name = "author_user_id", nullable = false)
  private UUID authorUserId;
  @Column(name = "body", nullable = false)
  private String body;
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;
  @Column(name = "delete_reason")
  private String deleteReason;

  protected TaskComment() {}
  public TaskComment(UUID id, UUID taskId, UUID author, String body, int depth, UUID parentId) {
    this.id = id; this.taskId = taskId; this.authorUserId = author;
    this.body = body; this.depth = depth; this.parentCommentId = parentId;
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
  public UUID getTaskId() { return taskId; }
  public UUID getParentCommentId() { return parentCommentId; }
  public int getDepth() { return depth; }
  public UUID getAuthorUserId() { return authorUserId; }
  public String getBody() { return body; }
  public void setBody(String v) { this.body = v; }
  public OffsetDateTime getDeletedAt() { return deletedAt; }
  public void setDeletedAt(OffsetDateTime v) { this.deletedAt = v; }
  public String getDeleteReason() { return deleteReason; }
  public void setDeleteReason(String v) { this.deleteReason = v; }
}
