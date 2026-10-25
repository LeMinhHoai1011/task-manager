package vn.smarttask.workspace.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "workspace")
public class Workspace {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;
  @Column(name = "name", nullable = false)
  private String name;
  @Column(name = "owner_user_id", nullable = false)
  private UUID ownerUserId;
  @Column(name = "type", nullable = false)
  private String type = "TEAM";
  @Column(name = "status", nullable = false)
  private String status = "ACTIVE";
  @Version
  @Column(name = "version", nullable = false)
  private int version = 1;
  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;
  @Column(name = "deleted_by")
  private UUID deletedBy;
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected Workspace() {}
  public Workspace(UUID id, String name, UUID ownerUserId, String type) {
    this.id = id; this.name = name; this.ownerUserId = ownerUserId; this.type = type;
  }
  @PrePersist
  void prePersist() {
    OffsetDateTime now = OffsetDateTime.now(java.time.ZoneOffset.UTC);
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
  }
  @PreUpdate
  void preUpdate() { updatedAt = OffsetDateTime.now(java.time.ZoneOffset.UTC); }

  public UUID getId() { return id; }
  public String getName() { return name; }
  public void setName(String v) { this.name = v; }
  public UUID getOwnerUserId() { return ownerUserId; }
  public void setOwnerUserId(UUID v) { this.ownerUserId = v; }
  public String getType() { return type; }
  public String getStatus() { return status; }
  public void setStatus(String v) { this.status = v; }
  public int getVersion() { return version; }
  public OffsetDateTime getDeletedAt() { return deletedAt; }
  public void setDeletedAt(OffsetDateTime v) { this.deletedAt = v; }
  public UUID getDeletedBy() { return deletedBy; }
  public void setDeletedBy(UUID v) { this.deletedBy = v; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
}
