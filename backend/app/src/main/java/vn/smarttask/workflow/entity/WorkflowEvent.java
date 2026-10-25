package vn.smarttask.workflow.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "workflow_event")
public class WorkflowEvent {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;
  @Column(name = "task_id", nullable = false)
  private UUID taskId;
  @Column(name = "from_state")
  private String fromState;
  @Column(name = "to_state", nullable = false)
  private String toState;
  @Column(name = "transition_id")
  private String transitionId;
  @Column(name = "actor_user_id")
  private UUID actorUserId;
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected WorkflowEvent() {}
  public WorkflowEvent(UUID id, UUID taskId, String from, String to, String tid, UUID actor) {
    this.id = id; this.taskId = taskId; this.fromState = from; this.toState = to;
    this.transitionId = tid; this.actorUserId = actor;
  }
  @PrePersist
  void prePersist() {
    if (createdAt == null) createdAt = OffsetDateTime.now(java.time.ZoneOffset.UTC);
  }
  public UUID getId() { return id; }
  public UUID getTaskId() { return taskId; }
  public String getFromState() { return fromState; }
  public String getToState() { return toState; }
  public String getTransitionId() { return transitionId; }
}
