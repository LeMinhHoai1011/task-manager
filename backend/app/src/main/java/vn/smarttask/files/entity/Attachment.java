package vn.smarttask.files.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "attachment")
public class Attachment {
  @Id private UUID id;
  @Column(name = "task_id") private UUID taskId;
  @Column(name = "file_name", nullable = false) private String fileName;
  @Column(name = "mime_type", nullable = false) private String mimeType;
  @Column(name = "size_bytes", nullable = false) private long sizeBytes;
  @Column(name = "current_version", nullable = false) private int currentVersion = 1;
  @Column(name = "status", nullable = false) private String status = "AVAILABLE";
  @Column(name = "version", nullable = false) private int version = 1;
  @Column(name = "target_type", nullable = false) private String targetType = "TASK";
  @Column(name = "target_id") private UUID targetId;
  @Column(name = "created_by") private UUID createdBy;
  @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
  @Column(name = "deleted_at") private OffsetDateTime deletedAt;
  @Column(name = "deleted_by") private UUID deletedBy;
  @Column(name = "delete_group_id") private UUID deleteGroupId;

  protected Attachment() {}
  public Attachment(UUID id, UUID taskId, String fileName, String mimeType, long sizeBytes, UUID createdBy) {
    this.id = id; this.taskId = taskId; this.targetId = taskId;
    this.fileName = fileName; this.mimeType = mimeType; this.sizeBytes = sizeBytes;
    this.createdBy = createdBy;
  }
  @PrePersist void prePersist() {
    if (createdAt == null) createdAt = OffsetDateTime.now(ZoneOffset.UTC);
  }
  public UUID getId() { return id; }
  public UUID getTaskId() { return taskId; }
  public UUID getTargetId() { return targetId; }
  public String getTargetType() { return targetType; }
  public void setTargetType(String v) { this.targetType = v; }
  public String getFileName() { return fileName; }
  public String getMimeType() { return mimeType; }
  public long getSizeBytes() { return sizeBytes; }
  public void setSizeBytes(long v) { this.sizeBytes = v; }
  public int getCurrentVersion() { return currentVersion; }
  public void setCurrentVersion(int v) { this.currentVersion = v; }
  public String getStatus() { return status; }
  public void setStatus(String v) { this.status = v; }
  public int getVersion() { return version; }
  public void setVersion(int v) { this.version = v; }
  public UUID getCreatedBy() { return createdBy; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public OffsetDateTime getDeletedAt() { return deletedAt; }
  public void setDeletedAt(OffsetDateTime v) { this.deletedAt = v; }
  public UUID getDeletedBy() { return deletedBy; }
  public void setDeletedBy(UUID v) { this.deletedBy = v; }
  public UUID getDeleteGroupId() { return deleteGroupId; }
  public void setDeleteGroupId(UUID v) { this.deleteGroupId = v; }
}
