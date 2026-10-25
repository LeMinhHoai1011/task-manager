package vn.smarttask.common.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "idempotency_record")
public class IdempotencyRecord {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;
  @Column(name = "key", nullable = false, unique = true)
  private String key;
  @Column(name = "user_id")
  private UUID userId;
  @Column(name = "status_code")
  private Integer statusCode;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "response_body", columnDefinition = "jsonb")
  private String responseBody;
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected IdempotencyRecord() {}
  public IdempotencyRecord(UUID id, String key, UUID userId) {
    this.id = id; this.key = key; this.userId = userId;
  }
  @PrePersist
  void prePersist() {
    if (createdAt == null) createdAt = OffsetDateTime.now(java.time.ZoneOffset.UTC);
  }
  public UUID getId() { return id; }
  public String getKey() { return key; }
  public UUID getUserId() { return userId; }
  public Integer getStatusCode() { return statusCode; }
  public void setStatusCode(Integer v) { this.statusCode = v; }
  public String getResponseBody() { return responseBody; }
  public void setResponseBody(String v) { this.responseBody = v; }
}
