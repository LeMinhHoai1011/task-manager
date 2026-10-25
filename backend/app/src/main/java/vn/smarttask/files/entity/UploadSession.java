package vn.smarttask.files.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "upload_session")
public class UploadSession {
  @Id private UUID id;
  @Column(name = "attachment_id") private UUID attachmentId;
  @Column(name = "status", nullable = false) private String status = "PENDING";
  @Column(name = "owner_user_id") private UUID ownerUserId;
  @Column(name = "task_id") private UUID taskId;
  @Column(name = "file_name") private String fileName;
  @Column(name = "mime_type") private String mimeType;
  @Column(name = "declared_size") private Long declaredSize;
  @Column(name = "staging_key") private String stagingKey;
  @Column(name = "idempotency_key") private String idempotencyKey;
  @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
  @Column(name = "expires_at", nullable = false) private OffsetDateTime expiresAt;

  protected UploadSession() {}
  public UploadSession(UUID id, UUID owner, UUID taskId, String fileName, String mimeType,
      long declaredSize, OffsetDateTime expiresAt) {
    this.id = id; this.ownerUserId = owner; this.taskId = taskId;
    this.fileName = fileName; this.mimeType = mimeType; this.declaredSize = declaredSize;
    this.expiresAt = expiresAt;
  }
  @PrePersist void prePersist() {
    if (createdAt == null) createdAt = OffsetDateTime.now(ZoneOffset.UTC);
  }
  public UUID getId() { return id; }
  public UUID getAttachmentId() { return attachmentId; }
  public void setAttachmentId(UUID v) { this.attachmentId = v; }
  public String getStatus() { return status; }
  public void setStatus(String v) { this.status = v; }
  public UUID getOwnerUserId() { return ownerUserId; }
  public UUID getTaskId() { return taskId; }
  public String getFileName() { return fileName; }
  public String getMimeType() { return mimeType; }
  public Long getDeclaredSize() { return declaredSize; }
  public String getStagingKey() { return stagingKey; }
  public void setStagingKey(String v) { this.stagingKey = v; }
  public String getIdempotencyKey() { return idempotencyKey; }
  public void setIdempotencyKey(String v) { this.idempotencyKey = v; }
  public OffsetDateTime getExpiresAt() { return expiresAt; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
}
