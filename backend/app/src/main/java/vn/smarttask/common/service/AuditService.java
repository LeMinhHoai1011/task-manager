package vn.smarttask.common.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.smarttask.common.entity.AuditEvent;
import vn.smarttask.common.entity.OutboxEvent;
import vn.smarttask.common.repo.AuditEventRepository;
import vn.smarttask.common.UuidV7;

@Service
public class AuditService {
  private final AuditEventRepository repo;

  public AuditService(AuditEventRepository repo) { this.repo = repo; }

  /** Append-only. No updates/deletes exposed. */
  @Transactional
  public AuditEvent append(UUID actor, String action, String entityType, UUID entityId,
      String outcome, String reason, String payloadJson) {
    AuditEvent e = new AuditEvent(UuidV7.generate(), actor, action, entityType, entityId,
        outcome == null ? "ALLOWED" : outcome, reason);
    e.setPayload(payloadJson == null ? "{}" : payloadJson);
    return repo.save(e);
  }

  @Transactional
  public AuditEvent appendDenied(UUID actor, String action, String entityType, UUID entityId, String reason) {
    return append(actor, action, entityType, entityId, "DENIED", reason, "{}");
  }
}
