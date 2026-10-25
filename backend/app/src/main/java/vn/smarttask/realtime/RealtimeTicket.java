package vn.smarttask.realtime;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * F0 short-lived single-use WebSocket handshake ticket bound to a session.
 * Replaces passing the opaque access token as a WS query param / first frame.
 */
@Entity
@Table(name = "realtime_ticket")
public class RealtimeTicket {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;
  @Column(name = "session_id", nullable = false)
  private UUID sessionId;
  @Column(name = "ticket_hash", nullable = false, unique = true)
  private String ticketHash;
  @Column(name = "used", nullable = false)
  private boolean used = false;
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;
  @Column(name = "expires_at", nullable = false)
  private OffsetDateTime expiresAt;

  protected RealtimeTicket() {}

  public RealtimeTicket(UUID id, UUID sessionId, String ticketHash, OffsetDateTime expiresAt) {
    this.id = id;
    this.sessionId = sessionId;
    this.ticketHash = ticketHash;
    this.expiresAt = expiresAt;
  }

  @PrePersist
  void prePersist() {
    if (createdAt == null) createdAt = OffsetDateTime.now(java.time.ZoneOffset.UTC);
  }

  public UUID getId() { return id; }
  public UUID getSessionId() { return sessionId; }
  public String getTicketHash() { return ticketHash; }
  public boolean isUsed() { return used; }
  public void setUsed(boolean v) { this.used = v; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public OffsetDateTime getExpiresAt() { return expiresAt; }
}
