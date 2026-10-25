package vn.smarttask.task.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "task_tag")
public class TaskTag {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;
  @Column(name = "task_id", nullable = false)
  private UUID taskId;
  @Column(name = "tag", nullable = false)
  private String tag;

  protected TaskTag() {}
  public TaskTag(UUID id, UUID taskId, String tag) {
    this.id = id; this.taskId = taskId; this.tag = tag;
  }
  public UUID getId() { return id; }
  public UUID getTaskId() { return taskId; }
  public String getTag() { return tag; }
}
