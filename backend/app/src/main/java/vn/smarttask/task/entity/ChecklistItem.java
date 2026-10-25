package vn.smarttask.task.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "checklist_item",
    uniqueConstraints = @UniqueConstraint(columnNames = {"task_id", "position"}))
public class ChecklistItem {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;
  @Column(name = "task_id", nullable = false)
  private UUID taskId;
  @Column(name = "title", nullable = false)
  private String title;
  @Column(name = "done", nullable = false)
  private boolean done = false;
  @Column(name = "position", nullable = false)
  private int position;
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected ChecklistItem() {}
  public ChecklistItem(UUID id, UUID taskId, String title, int position) {
    this.id = id; this.taskId = taskId; this.title = title; this.position = position;
  }
  @PrePersist
  void prePersist() {
    if (createdAt == null) createdAt = OffsetDateTime.now(java.time.ZoneOffset.UTC);
  }
  public UUID getId() { return id; }
  public UUID getTaskId() { return taskId; }
  public String getTitle() { return title; }
  public boolean isDone() { return done; }
  public void setDone(boolean v) { this.done = v; }
  public int getPosition() { return position; }
}
