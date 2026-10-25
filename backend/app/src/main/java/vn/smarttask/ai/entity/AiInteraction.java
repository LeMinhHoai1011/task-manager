package vn.smarttask.ai.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "ai_interaction")
public class AiInteraction {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;
  @Column(name = "job_id", nullable = false)
  private UUID jobId;
  /** Private Object Storage reference for the prompt (never inline PII in DB). */
  @Column(name = "prompt_ref", nullable = false)
  private String promptRef;
  @Column(name = "retention_days", nullable = false)
  private int retentionDays = 30;
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected AiInteraction() {}

  public AiInteraction(UUID id, UUID jobId, String promptRef) {
    this.id = id;
    this.jobId = jobId;
    this.promptRef = promptRef;
  }

  @PrePersist
  void prePersist() {
    if (createdAt == null) createdAt = OffsetDateTime.now(ZoneOffset.UTC);
  }

  public UUID getId() { return id; }
  public UUID getJobId() { return jobId; }
  public String getPromptRef() { return promptRef; }
  public int getRetentionDays() { return retentionDays; }
  public void setRetentionDays(int v) { this.retentionDays = v; }
}
