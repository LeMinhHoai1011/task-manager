package vn.smarttask.ai.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "ai_suggestion")
public class AiSuggestion {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;
  @Column(name = "job_id", nullable = false, unique = true)
  private UUID jobId;
  @Column(name = "payload", nullable = false)
  private String payload = "{}";
  @Column(name = "status", nullable = false)
  private String status = "DRAFT";
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected AiSuggestion() {}

  public AiSuggestion(UUID id, UUID jobId, String payload) {
    this.id = id;
    this.jobId = jobId;
    this.payload = payload == null ? "{}" : payload;
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
  public UUID getJobId() { return jobId; }
  public String getPayload() { return payload; }
  public void setPayload(String v) { this.payload = v; }
  public String getStatus() { return status; }
  public void setStatus(String v) { this.status = v; }
}
