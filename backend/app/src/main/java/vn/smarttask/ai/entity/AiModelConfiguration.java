package vn.smarttask.ai.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "ai_model_configuration")
public class AiModelConfiguration {
  @Id
  @Column(name = "key", nullable = false)
  private String key;
  @Column(name = "value", nullable = false)
  private String value;
  @Column(name = "updated_by")
  private UUID updatedBy;
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected AiModelConfiguration() {}

  public AiModelConfiguration(String key, String value) {
    this.key = key;
    this.value = value;
  }

  @PrePersist
  void prePersist() {
    if (updatedAt == null) updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
  }

  @PreUpdate
  void preUpdate() { updatedAt = OffsetDateTime.now(ZoneOffset.UTC); }

  public String getKey() { return key; }
  public String getValue() { return value; }
  public void setValue(String v) { this.value = v; }
  public UUID getUpdatedBy() { return updatedBy; }
  public void setUpdatedBy(UUID v) { this.updatedBy = v; }
}
