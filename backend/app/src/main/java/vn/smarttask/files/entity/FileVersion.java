package vn.smarttask.files.entity;

import jakarta.persistence.*;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;

/** Append-only: versions only increase, never overwrite. */
@Entity
@Table(name = "file_version")
public class FileVersion {
  @Id private UUID id;
  @Column(name = "attachment_id", nullable = false) private UUID attachmentId;
  @Column(name = "version_no", nullable = false) private int versionNo;
  @Column(name = "object_key", nullable = false) private String objectKey;
  @Column(name = "size_bytes", nullable = false) private long sizeBytes;
  @Column(name = "change_note") private String changeNote;
  @Column(name = "created_by") private UUID createdBy;
  @JdbcTypeCode(Types.CHAR)
  @Column(name = "checksum_sha256", columnDefinition = "char(64)") private String checksumSha256;
  @Column(name = "mime_type") private String mimeType;
  @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;

  protected FileVersion() {}
  public FileVersion(UUID id, UUID attachmentId, int versionNo, String objectKey, long sizeBytes) {
    this.id = id; this.attachmentId = attachmentId; this.versionNo = versionNo;
    this.objectKey = objectKey; this.sizeBytes = sizeBytes;
  }
  @PrePersist void prePersist() {
    if (createdAt == null) createdAt = OffsetDateTime.now(ZoneOffset.UTC);
  }
  public UUID getId() { return id; }
  public UUID getAttachmentId() { return attachmentId; }
  public int getVersionNo() { return versionNo; }
  public String getObjectKey() { return objectKey; }
  public long getSizeBytes() { return sizeBytes; }
  public String getChangeNote() { return changeNote; }
  public void setChangeNote(String v) { this.changeNote = v; }
  public UUID getCreatedBy() { return createdBy; }
  public void setCreatedBy(UUID v) { this.createdBy = v; }
  public String getChecksumSha256() { return checksumSha256; }
  public void setChecksumSha256(String v) { this.checksumSha256 = v; }
  public String getMimeType() { return mimeType; }
  public void setMimeType(String v) { this.mimeType = v; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
}
