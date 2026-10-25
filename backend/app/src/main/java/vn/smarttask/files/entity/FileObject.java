package vn.smarttask.files.entity;

import jakarta.persistence.*;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "file_object")
public class FileObject {
  @Id private UUID id;
  @Column(name = "object_key", nullable = false, unique = true) private String objectKey;
  @Column(name = "bucket", nullable = false) private String bucket;
  @Column(name = "size_bytes", nullable = false) private long sizeBytes;
  @JdbcTypeCode(Types.CHAR)
  @Column(name = "checksum_sha256", columnDefinition = "char(64)") private String checksumSha256;
  @Column(name = "content_type") private String contentType;
  /** PENDING/CLEAN/INFECTED/UNSUPPORTED. No malware engine in V1 -> UNSUPPORTED (NOT protected). */
  @Column(name = "scan_status", nullable = false) private String scanStatus = "UNSUPPORTED";
  @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;

  protected FileObject() {}
  public FileObject(UUID id, String objectKey, String bucket, long sizeBytes) {
    this.id = id; this.objectKey = objectKey; this.bucket = bucket; this.sizeBytes = sizeBytes;
  }
  @PrePersist void prePersist() {
    if (createdAt == null) createdAt = OffsetDateTime.now(ZoneOffset.UTC);
  }
  public UUID getId() { return id; }
  public String getObjectKey() { return objectKey; }
  public String getBucket() { return bucket; }
  public long getSizeBytes() { return sizeBytes; }
  public String getScanStatus() { return scanStatus; }
  public void setScanStatus(String v) { this.scanStatus = v; }
  public String getChecksumSha256() { return checksumSha256; }
  public void setChecksumSha256(String v) { this.checksumSha256 = v; }
  public String getContentType() { return contentType; }
  public void setContentType(String v) { this.contentType = v; }
}
