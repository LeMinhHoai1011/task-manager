package vn.smarttask.jobs;

import java.util.List;
import org.springframework.stereotype.Component;
import vn.smarttask.common.entity.OutboxEvent;
import vn.smarttask.common.service.AuditService;

/**
 * {@code ai.*} -> dispatch evidence only. AI execution is driven by the
 * JobRunner (AI_GENERATE background jobs) via AiJobRunner, never by outbox.
 */
@Component
public class AiOutboxHandler implements OutboxHandler {
  private final AuditService audit;

  public AiOutboxHandler(AuditService audit) {
    this.audit = audit;
  }

  @Override
  public List<String> prefixes() {
    return List.of("ai.");
  }

  @Override
  public int order() { return 40; }

  @Override
  public void handle(OutboxEvent event) {
    audit.append(null, "outbox.ai.dispatch", event.getAggregateType(),
        event.getAggregateId(), "ALLOWED", event.getType(), event.getPayload());
  }
}
