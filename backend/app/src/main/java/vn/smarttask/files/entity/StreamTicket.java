package vn.smarttask.files.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Single-use stream ticket, TTL 30s. Consumed on first use. */
@Entity
@Table(name = "stream_ticket")
public class StreamTicket {
  @Id private UUID id;
  @Column(name = "attachment_id", nullable = false) private UUID attachmentId;
  @Column(name = "file_version_id") private UUID fileVersionId;
  @Column(name = "ticket_hash", nullable = false, unique = true) private String ticketHash;
  @Column(name = "actor_user_id") private UUID actorUserId;
  @Column(name = "used", nullable = false) private boolean used = false;
  @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
  @Column(name = "expires_at", nullable = false) private OffsetDateTime expiresAt;

  protected StreamTicket() {}
  public StreamTicket(UUID id, UUID attachmentId, String ticketHash, UUID actor, OffsetDateTime expiresAt) {
    this.id = id; this.attachmentId = attachmentId; this.ticketHash = ticketHash;
    this.actorUserId = actor; this.expiresAt = expiresAt;
  }
  @PrePersist void prePersist() {
    if (createdAt == null) createdAt = OffsetDateTime.now(ZoneOffset.UTC);
  }
  public UUID getId() { return id; }
  public UUID getAttachmentId() { return attachmentId; }
  public UUID getFileVersionId() { return fileVersionId; }
  public void setFileVersionId(UUID v) { this.fileVersionId = v; }
  public String getTicketHash() { return ticketHash; }
  public UUID getActorUserId() { return actorUserId; }
  public boolean isUsed() { return used; }
  public void setUsed(boolean v) { this.used = v; }
  public OffsetDateTime getExpiresAt() { return expiresAt; }
}
