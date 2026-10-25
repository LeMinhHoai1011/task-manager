package vn.smarttask.task.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "time_log")
public class TimeLog {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;
  @Column(name = "task_id", nullable = false)
  private UUID taskId;
  @Column(name = "user_id", nullable = false)
  private UUID userId;
  @Column(name = "work_date", nullable = false)
  private LocalDate workDate;
  @Column(name = "minutes", nullable = false)
  private int minutes;
  @Column(name = "note")
  private String note;
  @Column(name = "locked_at")
  private OffsetDateTime lockedAt;
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected TimeLog() {}
  public TimeLog(UUID id, UUID taskId, UUID userId, LocalDate workDate, int minutes) {
    this.id = id; this.taskId = taskId; this.userId = userId;
    this.workDate = workDate; this.minutes = minutes;
  }
  @PrePersist
  void prePersist() {
    if (createdAt == null) createdAt = OffsetDateTime.now(java.time.ZoneOffset.UTC);
  }
  public UUID getId() { return id; }
  public UUID getTaskId() { return taskId; }
  public UUID getUserId() { return userId; }
  public LocalDate getWorkDate() { return workDate; }
  public int getMinutes() { return minutes; }
  public String getNote() { return note; }
  public void setNote(String v) { this.note = v; }
  public OffsetDateTime getLockedAt() { return lockedAt; }
  public void setLockedAt(OffsetDateTime v) { this.lockedAt = v; }
}
