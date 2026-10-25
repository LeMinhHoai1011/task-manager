package vn.smarttask.ai.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "ai_context_manifest")
public class AiContextManifest {
  @Id
  @Column(name = "code", nullable = false)
  private String code;
  @Column(name = "allowed_fields", nullable = false)
  private String allowedFields = "[]";
  @Column(name = "forbidden_patterns", nullable = false)
  private String forbiddenPatterns = "[]";
  @Column(name = "scope_rules", nullable = false)
  private String scopeRules = "{}";
  @Column(name = "enabled", nullable = false)
  private boolean enabled = true;
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected AiContextManifest() {}

  public AiContextManifest(String code) {
    this.code = code;
  }

  @PrePersist
  void prePersist() {
    if (updatedAt == null) updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
  }

  @PreUpdate
  void preUpdate() { updatedAt = OffsetDateTime.now(ZoneOffset.UTC); }

  public String getCode() { return code; }
  public String getAllowedFields() { return allowedFields; }
  public void setAllowedFields(String v) { this.allowedFields = v; }
  public String getForbiddenPatterns() { return forbiddenPatterns; }
  public void setForbiddenPatterns(String v) { this.forbiddenPatterns = v; }
  public String getScopeRules() { return scopeRules; }
  public void setScopeRules(String v) { this.scopeRules = v; }
  public boolean isEnabled() { return enabled; }
  public void setEnabled(boolean v) { this.enabled = v; }
}
