package vn.smarttask.ai.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "ai_job")
public class AiJob {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;
  @Column(name = "principal_user_id", nullable = false)
  private UUID principalUserId;
  @Column(name = "feature", nullable = false)
  private String feature;
  @Column(name = "status", nullable = false)
  private String status = "QUEUED";
  @Column(name = "idempotency_key", nullable = false, unique = true)
  private String idempotencyKey;
  @Column(name = "input_hint")
  private String inputHint;
  @Column(name = "context_snapshot_ref")
  private String contextSnapshotRef;
  @Column(name = "output_ref")
  private String outputRef;
  @Column(name = "project_id")
  private UUID projectId;
  @Column(name = "attempt", nullable = false)
  private int attempt = 1;
  @Column(name = "max_attempts", nullable = false)
  private int maxAttempts = 3;
  @Column(name = "timeout_ms", nullable = false)
  private long timeoutMs = 30000L;
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected AiJob() {}

  public AiJob(UUID id, UUID principalUserId, String feature, String idempotencyKey) {
    this.id = id;
    this.principalUserId = principalUserId;
    this.feature = feature;
    this.idempotencyKey = idempotencyKey;
  }

  @PrePersist
  void prePersist() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
  }

  @PreUpdate
  void preUpdate() { updatedAt = OffsetDateTime.now(ZoneOffset.UTC); }

  public UUID getId() { return id; }
  public UUID getPrincipalUserId() { return principalUserId; }
  public String getFeature() { return feature; }
  public String getStatus() { return status; }
  public void setStatus(String v) { this.status = v; }
  public String getIdempotencyKey() { return idempotencyKey; }
  public String getInputHint() { return inputHint; }
  public void setInputHint(String v) { this.inputHint = v; }
  public String getContextSnapshotRef() { return contextSnapshotRef; }
  public void setContextSnapshotRef(String v) { this.contextSnapshotRef = v; }
  public String getOutputRef() { return outputRef; }
  public void setOutputRef(String v) { this.outputRef = v; }
  public UUID getProjectId() { return projectId; }
  public void setProjectId(UUID v) { this.projectId = v; }
  public int getAttempt() { return attempt; }
  public void setAttempt(int v) { this.attempt = v; }
  public int getMaxAttempts() { return maxAttempts; }
  public void setMaxAttempts(int v) { this.maxAttempts = v; }
  public long getTimeoutMs() { return timeoutMs; }
  public void setTimeoutMs(long v) { this.timeoutMs = v; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
