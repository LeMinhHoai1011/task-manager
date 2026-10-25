package vn.smarttask.files.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Append-only security evidence. No updates/deletes. */
@Entity
@Table(name = "file_access_event")
public class FileAccessEvent {
  @Id private UUID id;
  @Column(name = "attachment_id") private UUID attachmentId;
  @Column(name = "file_version_id") private UUID fileVersionId;
  @Column(name = "actor_user_id") private UUID actorUserId;
  @Column(name = "action", nullable = false) private String action;
  @Column(name = "outcome", nullable = false) private String outcome;
  @Column(name = "reason") private String reason;
  @Column(name = "correlation_id") private UUID correlationId;
  @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;

  protected FileAccessEvent() {}
  public FileAccessEvent(UUID id, UUID attachmentId, UUID actor, String action, String outcome, String reason) {
    this.id = id; this.attachmentId = attachmentId; this.actorUserId = actor;
    this.action = action; this.outcome = outcome; this.reason = reason;
  }
  @PrePersist void prePersist() {
    if (createdAt == null) createdAt = OffsetDateTime.now(ZoneOffset.UTC);
  }
  public UUID getId() { return id; }
  public String getAction() { return action; }
  public String getOutcome() { return outcome; }
  public String getReason() { return reason; }
  public void setFileVersionId(UUID v) { this.fileVersionId = v; }
  public void setCorrelationId(UUID v) { this.correlationId = v; }
}
