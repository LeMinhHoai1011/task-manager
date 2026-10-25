package vn.smarttask.project.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "milestone")
public class Milestone {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;
  @Column(name = "project_id", nullable = false)
  private UUID projectId;
  @Column(name = "title", nullable = false)
  private String title;
  @Column(name = "due_at")
  private OffsetDateTime dueAt;
  @Column(name = "status", nullable = false)
  private String status = "OPEN";
  @Column(name = "cancelled_at")
  private OffsetDateTime cancelledAt;
  @Column(name = "cancelled_reason")
  private String cancelledReason;
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected Milestone() {}
  public Milestone(UUID id, UUID projectId, String title, OffsetDateTime dueAt) {
    this.id = id; this.projectId = projectId; this.title = title; this.dueAt = dueAt;
  }
  @PrePersist
  void prePersist() {
    if (createdAt == null) createdAt = OffsetDateTime.now(java.time.ZoneOffset.UTC);
  }
  public UUID getId() { return id; }
  public UUID getProjectId() { return projectId; }
  public String getTitle() { return title; }
  public void setTitle(String v) { this.title = v; }
  public OffsetDateTime getDueAt() { return dueAt; }
  public void setDueAt(OffsetDateTime v) { this.dueAt = v; }
  public String getStatus() { return status; }
  public void setStatus(String v) { this.status = v; }
  public OffsetDateTime getCancelledAt() { return cancelledAt; }
  public void setCancelledAt(OffsetDateTime v) { this.cancelledAt = v; }
  public String getCancelledReason() { return cancelledReason; }
  public void setCancelledReason(String v) { this.cancelledReason = v; }
}
