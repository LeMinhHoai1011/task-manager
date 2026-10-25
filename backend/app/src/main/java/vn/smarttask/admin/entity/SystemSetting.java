package vn.smarttask.admin.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "system_setting")
public class SystemSetting {
  @Id private String key;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "value", nullable = false, columnDefinition = "jsonb") private String value = "{}";
  @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;

  protected SystemSetting() {}
  public SystemSetting(String key, String value) { this.key = key; this.value = value; }
  @PrePersist void prePersist() {
    if (updatedAt == null) updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
  }
  @PreUpdate void preUpdate() { updatedAt = OffsetDateTime.now(ZoneOffset.UTC); }
  public String getKey() { return key; }
  public String getValue() { return value; }
  public void setValue(String v) { this.value = v; }
}
