package vn.smarttask.jobs;

import java.util.List;
import org.springframework.stereotype.Component;
import vn.smarttask.common.entity.OutboxEvent;
import vn.smarttask.common.service.AuditService;

/**
 * Fallback: every other event family (user.*, security_event.*, task.*,
 * file.*, ...) is recorded as dispatched with audit evidence. The relay
 * therefore never DEADs on an unknown type; new families add handlers later.
 */
@Component
public class FallbackOutboxHandler implements OutboxHandler {
  private final AuditService audit;

  public FallbackOutboxHandler(AuditService audit) {
    this.audit = audit;
  }

  @Override
  public List<String> prefixes() {
    return List.of("");
  }

  @Override
  public int order() { return 1000; }

  @Override
  public void handle(OutboxEvent event) {
    audit.append(null, "outbox.dispatch", event.getAggregateType(),
        event.getAggregateId(), "ALLOWED", event.getType(), event.getPayload());
  }
}
