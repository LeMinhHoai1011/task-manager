package vn.smarttask.project.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "progress_log")
public class ProgressLog {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "update_date", nullable = false)
  private LocalDate updateDate;

  @Column(name = "progress_percent", nullable = false)
  private int progressPercent;

  @Column(name = "summary", nullable = false, columnDefinition = "TEXT")
  private String summary;

  @Column(name = "completed_work", columnDefinition = "TEXT")
  private String completedWork;

  @Column(name = "blockers", columnDefinition = "TEXT")
  private String blockers;

  @Column(name = "next_plan", columnDefinition = "TEXT")
  private String nextPlan;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Version
  @Column(name = "version", nullable = false)
  private long version = 1;

  protected ProgressLog() {}

  public ProgressLog(UUID id, UUID projectId, LocalDate updateDate, int progressPercent, String summary, UUID createdBy) {
    this.id = id;
    this.projectId = projectId;
    this.updateDate = updateDate != null ? updateDate : LocalDate.now();
    this.progressPercent = Math.max(0, Math.min(100, progressPercent));
    this.summary = summary;
    this.createdBy = createdBy;
  }

  @PrePersist
  void prePersist() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
  }

  public UUID getId() { return id; }
  public UUID getProjectId() { return projectId; }
  public LocalDate getUpdateDate() { return updateDate; }
  public void setUpdateDate(LocalDate updateDate) { this.updateDate = updateDate; }
  public int getProgressPercent() { return progressPercent; }
  public void setProgressPercent(int progressPercent) { this.progressPercent = Math.max(0, Math.min(100, progressPercent)); }
  public String getSummary() { return summary; }
  public void setSummary(String summary) { this.summary = summary; }
  public String getCompletedWork() { return completedWork; }
  public void setCompletedWork(String completedWork) { this.completedWork = completedWork; }
  public String getBlockers() { return blockers; }
  public void setBlockers(String blockers) { this.blockers = blockers; }
  public String getNextPlan() { return nextPlan; }
  public void setNextPlan(String nextPlan) { this.nextPlan = nextPlan; }
  public UUID getCreatedBy() { return createdBy; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public OffsetDateTime getUpdatedAt() { return updatedAt; }
  public long getVersion() { return version; }
}
