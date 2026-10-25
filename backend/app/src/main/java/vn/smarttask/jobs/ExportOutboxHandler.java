package vn.smarttask.jobs;

import java.util.List;
import org.springframework.stereotype.Component;
import vn.smarttask.common.entity.OutboxEvent;
import vn.smarttask.common.service.AuditService;

/**
 * {@code export.*} -> dispatch evidence only. Export execution itself is
 * driven by the JobRunner (REPORT_EXPORT background jobs), not by outbox.
 */
@Component
public class ExportOutboxHandler implements OutboxHandler {
  private final AuditService audit;

  public ExportOutboxHandler(AuditService audit) {
    this.audit = audit;
  }

  @Override
  public List<String> prefixes() {
    return List.of("export.");
  }

  @Override
  public int order() { return 30; }

  @Override
  public void handle(OutboxEvent event) {
    audit.append(null, "outbox.export.dispatch", event.getAggregateType(),
        event.getAggregateId(), "ALLOWED", event.getType(), event.getPayload());
  }
}
