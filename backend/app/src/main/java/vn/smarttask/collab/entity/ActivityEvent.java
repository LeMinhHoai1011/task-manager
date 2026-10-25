package vn.smarttask.collab.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "activity_event")
public class ActivityEvent {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;
  @Column(name = "task_id")
  private UUID taskId;
  @Column(name = "project_id")
  private UUID projectId;
  @Column(name = "actor_user_id")
  private UUID actorUserId;
  @Column(name = "kind", nullable = false)
  private String kind;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
  private String payload = "{}";
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected ActivityEvent() {}
  public ActivityEvent(UUID id, UUID taskId, UUID projectId, UUID actor, String kind, String payload) {
    this.id = id; this.taskId = taskId; this.projectId = projectId;
    this.actorUserId = actor; this.kind = kind; this.payload = payload;
  }
  @PrePersist
  void prePersist() {
    if (createdAt == null) createdAt = OffsetDateTime.now(java.time.ZoneOffset.UTC);
  }
  public UUID getId() { return id; }
  public String getKind() { return kind; }
  public UUID getTaskId() { return taskId; }
  public UUID getProjectId() { return projectId; }
  public UUID getActorUserId() { return actorUserId; }
  public String getPayload() { return payload; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
}
