package vn.smarttask.workflow.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "approval_decision",
    uniqueConstraints = @UniqueConstraint(columnNames = {"round_id"}))
public class ApprovalDecision {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;
  @Column(name = "round_id", nullable = false)
  private UUID roundId;
  @Column(name = "outcome", nullable = false)
  private String outcome;
  @Column(name = "reason")
  private String reason;
  @Column(name = "decided_by", nullable = false)
  private UUID decidedBy;
  @Column(name = "decided_at", nullable = false)
  private OffsetDateTime decidedAt;
  // V1 legacy columns, still NOT NULL in the schema: dual-written with outcome/decided_by.
  @Column(name = "decider_user_id", nullable = false)
  private UUID deciderUserId;
  @Column(name = "decision", nullable = false)
  private String decision;

  protected ApprovalDecision() {}
  public ApprovalDecision(UUID id, UUID roundId, String outcome, UUID decidedBy, String reason) {
    this.id = id; this.roundId = roundId; this.outcome = outcome;
    this.decidedBy = decidedBy; this.reason = reason;
    this.deciderUserId = decidedBy; this.decision = outcome;
    this.decidedAt = OffsetDateTime.now(java.time.ZoneOffset.UTC);
  }
  public UUID getId() { return id; }
  public UUID getRoundId() { return roundId; }
  public String getOutcome() { return outcome; }
  public String getReason() { return reason; }
  public UUID getDecidedBy() { return decidedBy; }
  public OffsetDateTime getDecidedAt() { return decidedAt; }
}
