package vn.smarttask.jobs;

import java.util.List;
import org.springframework.stereotype.Component;
import vn.smarttask.common.entity.OutboxEvent;
import vn.smarttask.notify.service.NotificationProcessor;

/** {@code notification.*} -> persist via NotificationProcessor (no WS transport). */
@Component
public class NotificationOutboxHandler implements OutboxHandler {
  @org.springframework.beans.factory.annotation.Autowired(required=false) private vn.smarttask.realtime.RealtimePublisher realtime;
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
    var notification=processor.deliver(processor.parse(event.getPayload()));
    if(notification!=null&&notification.getDeferredUntil()==null&&realtime!=null)realtime.user(notification.getUserId(),"NOTIFICATION_CREATED","USER",notification.getUserId(),notification.getId());
  }
}
