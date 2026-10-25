package vn.smarttask.common.service;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.smarttask.common.entity.OutboxEvent;
import vn.smarttask.common.repo.OutboxEventRepository;
import vn.smarttask.common.UuidV7;

@Service
public class OutboxService {
  private final OutboxEventRepository repo;

  public OutboxService(OutboxEventRepository repo) { this.repo = repo; }

  /** Must be called in the same tx as the domain write. Never dispatched before commit. */
  @Transactional
  public OutboxEvent emit(String aggregateType, UUID aggregateId, String type, String payloadJson) {
    return emit(aggregateType, aggregateId, type, type, payloadJson, null, null);
  }

  /**
   * Full emit: explicit handler dispatch key + idempotency key + correlation.
   * The row is PENDING; only the relay (post-commit) dispatches it.
   */
  @Transactional
  public OutboxEvent emit(String aggregateType, UUID aggregateId, String type, String handler,
      String payloadJson, String idempotencyKey, UUID correlationId) {
    OutboxEvent e = new OutboxEvent(UuidV7.generate(), aggregateType, aggregateId, type,
        payloadJson == null ? "{}" : payloadJson);
    e.setHandler(handler == null || handler.isBlank() ? type : handler);
    e.setIdempotencyKey(idempotencyKey);
    e.setCorrelationId(correlationId);
    e.setStatus("PENDING");
    return repo.save(e);
  }
}
