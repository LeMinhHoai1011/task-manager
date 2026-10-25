package vn.smarttask.identity.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class AppUser {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;
  @Column(name = "username", nullable = false, columnDefinition = "citext")
  private String username;
  @Column(name = "email", nullable = false, columnDefinition = "citext")
  private String email;
  @Column(name = "password_hash", nullable = false)
  private String passwordHash;
  @Column(name = "display_name", nullable = false)
  private String displayName;
  @Column(name = "active", nullable = false)
  private boolean active = true;
  @Column(name = "locked", nullable = false)
  private boolean locked = false;
  // V2 additions
  @Column(name = "status", nullable = false)
  private String status = "ACTIVE";
  @Version
  @Column(name = "version", nullable = false)
  private int version = 1;
  @Column(name = "locked_until")
  private OffsetDateTime lockedUntil;
  @Column(name = "must_change_password", nullable = false)
  private boolean mustChangePassword = false;
  @Column(name = "protected_flag", nullable = false)
  private boolean protectedFlag = false;
  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;
  @Column(name = "deleted_by")
  private UUID deletedBy;
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
  // V5 notification profile columns (quiet hours live on app_user).
  @Column(name = "timezone", nullable = false)
  private String timezone = "UTC";
  @Column(name = "locale", nullable = false)
  private String locale = "vi-VN";
  @Column(name = "quiet_hours_enabled", nullable = false)
  private boolean quietHoursEnabled = false;
  @Column(name = "quiet_start")
  private java.time.LocalTime quietStart;
  @Column(name = "quiet_end")
  private java.time.LocalTime quietEnd;

  protected AppUser() {}
  public AppUser(UUID id, String username, String email, String passwordHash, String displayName) {
    this.id = id; this.username = username; this.email = email;
    this.passwordHash = passwordHash; this.displayName = displayName;
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
  public void setId(UUID id) { this.id = id; }
  public String getUsername() { return username; }
  public void setUsername(String v) { this.username = v; }
  public String getEmail() { return email; }
  public void setEmail(String v) { this.email = v; }
  public String getPasswordHash() { return passwordHash; }
  public void setPasswordHash(String v) { this.passwordHash = v; }
  public String getDisplayName() { return displayName; }
  public void setDisplayName(String v) { this.displayName = v; }
  public boolean isActive() { return active; }
  public void setActive(boolean v) { this.active = v; }
  public boolean isLocked() { return locked; }
  public void setLocked(boolean v) { this.locked = v; }
  public String getStatus() { return status; }
  public void setStatus(String v) { this.status = v; }
  public int getVersion() { return version; }
  public OffsetDateTime getLockedUntil() { return lockedUntil; }
  public void setLockedUntil(OffsetDateTime v) { this.lockedUntil = v; }
  public boolean isMustChangePassword() { return mustChangePassword; }
  public void setMustChangePassword(boolean v) { this.mustChangePassword = v; }
  public boolean isProtectedFlag() { return protectedFlag; }
  public void setProtectedFlag(boolean v) { this.protectedFlag = v; }
  public OffsetDateTime getDeletedAt() { return deletedAt; }
  public void setDeletedAt(OffsetDateTime v) { this.deletedAt = v; }
  public UUID getDeletedBy() { return deletedBy; }
  public void setDeletedBy(UUID v) { this.deletedBy = v; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public OffsetDateTime getUpdatedAt() { return updatedAt; }
  public String getTimezone() { return timezone; }
  public void setTimezone(String v) { this.timezone = v; }
  public String getLocale() { return locale; }
  public void setLocale(String v) { this.locale = v; }
  public boolean isQuietHoursEnabled() { return quietHoursEnabled; }
  public void setQuietHoursEnabled(boolean v) { this.quietHoursEnabled = v; }
  public java.time.LocalTime getQuietStart() { return quietStart; }
  // PG TIME holds microsecond precision: truncate sub-micro nanos on write
  // (the PG driver mis-rounds nanosecond fractions on read).
  public void setQuietStart(java.time.LocalTime v) {
    this.quietStart = v == null ? null : v.withNano((v.getNano() / 1000) * 1000);
  }
  public java.time.LocalTime getQuietEnd() { return quietEnd; }
  public void setQuietEnd(java.time.LocalTime v) {
    this.quietEnd = v == null ? null : v.withNano((v.getNano() / 1000) * 1000);
  }
}
