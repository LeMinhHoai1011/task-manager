package vn.smarttask.jobs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.smarttask.common.entity.OutboxEvent;
import vn.smarttask.common.service.AuditService;

/** {@code audit.*} -> append the audit row carried in the payload. */
@Component
public class AuditOutboxHandler implements OutboxHandler {
  private final AuditService audit;
  private final ObjectMapper om;

  public AuditOutboxHandler(AuditService audit, ObjectMapper om) {
    this.audit = audit; this.om = om;
  }

  @Override
  public List<String> prefixes() {
    return List.of("audit.");
  }

  @Override
  public int order() { return 20; }

  @Override
  public void handle(OutboxEvent event) {
    try {
      JsonNode n = om.readTree(event.getPayload() == null ? "{}" : event.getPayload());
      UUID actor = n.hasNonNull("actor") ? UUID.fromString(n.get("actor").asText()) : null;
      UUID entityId = n.hasNonNull("entity_id") ? UUID.fromString(n.get("entity_id").asText()) : event.getAggregateId();
      audit.append(actor, n.path("action").asText(event.getType()),
          n.path("entity_type").asText(event.getAggregateType()), entityId,
          n.path("outcome").asText("ALLOWED"),
          n.hasNonNull("reason") ? n.get("reason").asText() : null,
          event.getPayload());
    } catch (Exception e) {
      audit.append(null, event.getType(), event.getAggregateType(), event.getAggregateId(),
          "ALLOWED", null, event.getPayload());
    }
  }
}
