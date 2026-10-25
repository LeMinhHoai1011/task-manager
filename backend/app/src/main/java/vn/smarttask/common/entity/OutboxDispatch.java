package vn.smarttask.common.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

/**
 * F3 dispatch ledger: exactly-once consumer evidence per (event, handler).
 * The (event_id, handler) PK is the dedup index: a claimed outbox row whose
 * ledger entry already exists is SKIPped, never re-dispatched.
 */
@Entity
@Table(name = "outbox_dispatch")
@IdClass(OutboxDispatch.Key.class)
public class OutboxDispatch {
  @Id
  @Column(name = "event_id", nullable = false)
  private UUID eventId;
  @Id
  @Column(name = "handler", nullable = false)
  private String handler;
  @Column(name = "dispatched_at", nullable = false)
  private OffsetDateTime dispatchedAt;

  protected OutboxDispatch() {}

  public OutboxDispatch(UUID eventId, String handler) {
    this.eventId = eventId;
    this.handler = handler;
  }

  @PrePersist
  void prePersist() {
    if (dispatchedAt == null) dispatchedAt = OffsetDateTime.now(ZoneOffset.UTC);
  }

  public UUID getEventId() { return eventId; }
  public String getHandler() { return handler; }
  public OffsetDateTime getDispatchedAt() { return dispatchedAt; }

  public static class Key implements Serializable {
    private UUID eventId;
    private String handler;

    public Key() {}
    public Key(UUID eventId, String handler) { this.eventId = eventId; this.handler = handler; }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof Key k)) return false;
      return Objects.equals(eventId, k.eventId) && Objects.equals(handler, k.handler);
    }

    @Override
    public int hashCode() { return Objects.hash(eventId, handler); }
  }
}
