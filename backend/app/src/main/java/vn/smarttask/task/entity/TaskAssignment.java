package vn.smarttask.task.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "task_assignment",
    uniqueConstraints = @UniqueConstraint(columnNames = {"task_id", "user_id"}))
public class TaskAssignment {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;
  @Column(name = "task_id", nullable = false)
  private UUID taskId;
  @Column(name = "user_id", nullable = false)
  private UUID userId;
  @Column(name = "status", nullable = false)
  private String status = "ACTIVE";
  @Column(name = "removal_reason")
  private String removalReason;
  @Column(name = "assigned_at", nullable = false)
  private OffsetDateTime assignedAt;

  protected TaskAssignment() {}
  public TaskAssignment(UUID id, UUID taskId, UUID userId) {
    this.id = id; this.taskId = taskId; this.userId = userId;
  }
  @PrePersist
  void prePersist() {
    if (assignedAt == null) assignedAt = OffsetDateTime.now(java.time.ZoneOffset.UTC);
  }
  public UUID getId() { return id; }
  public UUID getTaskId() { return taskId; }
  public UUID getUserId() { return userId; }
  public String getStatus() { return status; }
  public void setStatus(String v) { this.status = v; }
  public String getRemovalReason() { return removalReason; }
  public void setRemovalReason(String v) { this.removalReason = v; }
}
