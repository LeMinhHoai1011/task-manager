package vn.smarttask.task.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "task_dependency",
    uniqueConstraints = @UniqueConstraint(columnNames = {"task_id", "depends_on_task_id"}))
public class TaskDependency {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;
  @Column(name = "task_id", nullable = false)
  private UUID taskId;
  @Column(name = "depends_on_task_id", nullable = false)
  private UUID dependsOnTaskId;
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected TaskDependency() {}
  public TaskDependency(UUID id, UUID taskId, UUID dependsOnTaskId) {
    this.id = id; this.taskId = taskId; this.dependsOnTaskId = dependsOnTaskId;
  }
  @PrePersist
  void prePersist() {
    if (createdAt == null) createdAt = OffsetDateTime.now(java.time.ZoneOffset.UTC);
  }
  public UUID getId() { return id; }
  public UUID getTaskId() { return taskId; }
  public UUID getDependsOnTaskId() { return dependsOnTaskId; }
}
