package vn.smarttask.jobs;

import java.util.List;
import org.springframework.stereotype.Component;
import vn.smarttask.common.entity.OutboxEvent;
import vn.smarttask.notify.service.NotificationProcessor;

/** {@code notification.*} -> persist via NotificationProcessor (no WS transport). */
@Component
public class NotificationOutboxHandler implements OutboxHandler {
  private final NotificationProcessor processor;

  public NotificationOutboxHandler(NotificationProcessor processor) {
    this.processor = processor;
  }

  @Override
  public List<String> prefixes() {
    return List.of("notification.", "notify.");
  }

  @Override
  public int order() { return 10; }

  @Override
  public void handle(OutboxEvent event) {
    processor.deliver(processor.parse(event.getPayload()));
  }
}
