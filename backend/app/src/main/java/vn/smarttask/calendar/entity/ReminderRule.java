package vn.smarttask.calendar.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "reminder_rule")
public class ReminderRule {
  @Id private UUID id;
  @Column(name = "task_id", nullable = false) private UUID taskId;
  @Column(name = "user_id", nullable = false) private UUID userId;
  @Column(name = "remind_at", nullable = false) private OffsetDateTime remindAt;
  @Column(name = "status", nullable = false) private String status = "ACTIVE";
  @Column(name = "paused", nullable = false) private boolean paused = false;
  @Column(name = "rule_kind", nullable = false) private String ruleKind = "ONE_SHOT";
  @Column(name = "digest_sent", nullable = false) private boolean digestSent = false;
  @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;

  protected ReminderRule() {}
  public ReminderRule(UUID id, UUID taskId, UUID userId, OffsetDateTime remindAt) {
    this.id = id; this.taskId = taskId; this.userId = userId; this.remindAt = remindAt;
  }
  @PrePersist void prePersist() {
    if (createdAt == null) createdAt = OffsetDateTime.now(java.time.ZoneOffset.UTC);
  }
  public UUID getId() { return id; }
  public UUID getTaskId() { return taskId; }
  public UUID getUserId() { return userId; }
  public OffsetDateTime getRemindAt() { return remindAt; }
  public void setRemindAt(OffsetDateTime v) { this.remindAt = v; }
  public String getStatus() { return status; }
  public void setStatus(String v) { this.status = v; }
  public boolean isPaused() { return paused; }
  public void setPaused(boolean v) { this.paused = v; }
  public boolean isDigestSent() { return digestSent; }
  public void setDigestSent(boolean v) { this.digestSent = v; }
}
