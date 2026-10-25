package vn.smarttask.workflow.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "approval_round",
    uniqueConstraints = @UniqueConstraint(columnNames = {"task_id", "round_no"}))
public class ApprovalRound {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;
  @Column(name = "task_id", nullable = false)
  private UUID taskId;
  @Column(name = "round_no", nullable = false)
  private int roundNo;
  @Column(name = "status", nullable = false)
  private String status = "OPEN";
  @Column(name = "submitted_by", nullable = false)
  private UUID submittedBy;
  // V1 legacy column, still NOT NULL in the schema: dual-written with submitted_by.
  @Column(name = "submitter_user_id", nullable = false)
  private UUID submitterUserId;
  @Column(name = "round_outcome")
  private String roundOutcome;
  @Column(name = "sla_breached", nullable = false)
  private boolean slaBreached = false;
  @Column(name = "version", nullable = false)
  private int version = 1;
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;
  @Column(name = "decided_at")
  private OffsetDateTime decidedAt;

  protected ApprovalRound() {}
  public ApprovalRound(UUID id, UUID taskId, int roundNo, UUID submittedBy) {
    this.id = id; this.taskId = taskId; this.roundNo = roundNo; this.submittedBy = submittedBy;
    this.submitterUserId = submittedBy;
  }
  @PrePersist
  void prePersist() {
    if (createdAt == null) createdAt = OffsetDateTime.now(java.time.ZoneOffset.UTC);
  }
  public UUID getId() { return id; }
  public UUID getTaskId() { return taskId; }
  public int getRoundNo() { return roundNo; }
  public String getStatus() { return status; }
  public void setStatus(String v) { this.status = v; }
  public UUID getSubmittedBy() { return submittedBy; }
  public UUID getSubmitterUserId() { return submitterUserId; }
  public String getRoundOutcome() { return roundOutcome; }
  public void setRoundOutcome(String v) { this.roundOutcome = v; }
  public boolean isSlaBreached() { return slaBreached; }
  public void setSlaBreached(boolean v) { this.slaBreached = v; }
  public int getVersion() { return version; }
  public void setVersion(int v) { this.version = v; }
  public OffsetDateTime getDecidedAt() { return decidedAt; }
  public void setDecidedAt(OffsetDateTime v) { this.decidedAt = v; }
}
