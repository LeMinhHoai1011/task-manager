package vn.smarttask.workflow.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "approval_designation",
    uniqueConstraints = @UniqueConstraint(columnNames = {"round_id", "user_id"}))
public class ApprovalDesignation {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;
  @Column(name = "round_id", nullable = false)
  private UUID roundId;
  @Column(name = "user_id", nullable = false)
  private UUID userId;
  @Column(name = "type", nullable = false)
  private String type = "DEFAULT";
  @Column(name = "revoked_at")
  private OffsetDateTime revokedAt;
  @Column(name = "revoked_reason")
  private String revokedReason;
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected ApprovalDesignation() {}
  public ApprovalDesignation(UUID id, UUID roundId, UUID userId, String type) {
    this.id = id; this.roundId = roundId; this.userId = userId; this.type = type;
  }
  @PrePersist
  void prePersist() {
    if (createdAt == null) createdAt = OffsetDateTime.now(java.time.ZoneOffset.UTC);
  }
  public UUID getId() { return id; }
  public UUID getRoundId() { return roundId; }
  public UUID getUserId() { return userId; }
  public String getType() { return type; }
  public OffsetDateTime getRevokedAt() { return revokedAt; }
  public void setRevokedAt(OffsetDateTime v) { this.revokedAt = v; }
  public String getRevokedReason() { return revokedReason; }
  public void setRevokedReason(String v) { this.revokedReason = v; }
  public boolean isActive() { return revokedAt == null; }
}
