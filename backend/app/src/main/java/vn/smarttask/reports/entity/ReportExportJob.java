package vn.smarttask.reports.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "report_export_job")
public class ReportExportJob {
  @Id private UUID id;
  @Column(name = "background_job_id") private UUID backgroundJobId;
  @Column(name = "report_code", nullable = false) private String reportCode;
  @Column(name = "scope_type", nullable = false) private String scopeType;
  @Column(name = "scope_id") private UUID scopeId;
  @Column(name = "period_from", nullable = false) private java.time.LocalDate periodFrom;
  @Column(name = "period_to", nullable = false) private java.time.LocalDate periodTo;
  @Column(name = "format", nullable = false) private String format;
  @Column(name = "requested_by", nullable = false) private UUID requestedBy;
  @Column(name = "status", nullable = false) private String status = "QUEUED";
  @Column(name = "attachment_id") private UUID attachmentId;
  @Column(name = "expires_at", nullable = false) private OffsetDateTime expiresAt;
  @Column(name = "row_count") private Long rowCount;
  @Column(name = "authorized_at_generation") private OffsetDateTime authorizedAtGeneration;
  @Column(name = "authorized_at_delivery") private OffsetDateTime authorizedAtDelivery;
  @Column(name = "idempotency_key", nullable = false, unique = true) private String idempotencyKey;
  @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
  // V12 export worker columns (F3; additive).
  @Column(name = "object_key") private String objectKey;
  @Column(name = "file_size_bytes") private Long fileSizeBytes;
  @Column(name = "error_code") private String errorCode;
  @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;

  protected ReportExportJob() {}
  public ReportExportJob(UUID id, String reportCode, String scopeType, UUID scopeId,
      java.time.LocalDate from, java.time.LocalDate to, String format, UUID requestedBy,
      String idempotencyKey, OffsetDateTime expiresAt) {
    this.id = id; this.reportCode = reportCode; this.scopeType = scopeType; this.scopeId = scopeId;
    this.periodFrom = from; this.periodTo = to; this.format = format;
    this.requestedBy = requestedBy; this.idempotencyKey = idempotencyKey; this.expiresAt = expiresAt;
  }
  @PrePersist void prePersist() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
  }
  @PreUpdate void preUpdate() { updatedAt = OffsetDateTime.now(ZoneOffset.UTC); }
  public UUID getId() { return id; }
  public String getReportCode() { return reportCode; }
  public String getScopeType() { return scopeType; }
  public UUID getScopeId() { return scopeId; }
  public java.time.LocalDate getPeriodFrom() { return periodFrom; }
  public java.time.LocalDate getPeriodTo() { return periodTo; }
  public String getFormat() { return format; }
  public UUID getRequestedBy() { return requestedBy; }
  public String getStatus() { return status; }
  public void setStatus(String v) { this.status = v; }
  public UUID getAttachmentId() { return attachmentId; }
  public void setAttachmentId(UUID v) { this.attachmentId = v; }
  public OffsetDateTime getExpiresAt() { return expiresAt; }
  public void setExpiresAt(OffsetDateTime v) { this.expiresAt = v; }
  public Long getRowCount() { return rowCount; }
  public void setRowCount(Long v) { this.rowCount = v; }
  public void setAuthorizedAtGeneration(OffsetDateTime v) { this.authorizedAtGeneration = v; }
  public void setAuthorizedAtDelivery(OffsetDateTime v) { this.authorizedAtDelivery = v; }
  public String getIdempotencyKey() { return idempotencyKey; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public void setBackgroundJobId(UUID v) { this.backgroundJobId = v; }
  public UUID getBackgroundJobId() { return backgroundJobId; }
  public String getObjectKey() { return objectKey; }
  public void setObjectKey(String v) { this.objectKey = v; }
  public Long getFileSizeBytes() { return fileSizeBytes; }
  public void setFileSizeBytes(Long v) { this.fileSizeBytes = v; }
  public String getErrorCode() { return errorCode; }
  public void setErrorCode(String v) { this.errorCode = v; }
  public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
