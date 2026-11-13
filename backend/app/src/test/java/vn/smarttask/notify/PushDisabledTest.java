package vn.smarttask.notify;

import org.junit.jupiter.api.Test;
import vn.smarttask.notify.service.PushDeliveryService;
import static org.junit.jupiter.api.Assertions.*;

class PushDisabledTest {
  @Test void configuredFlagCannotClaimDeliveryWithoutProvider() {
    var gateway = new PushEmailGateway(true, true);
    for (var channel : PushEmailGateway.Channel.values()) {
      assertEquals("DISABLED", gateway.send(channel, "test", "title", null).status());
    }
  }
  @Test void diagnosticHistoryIsBounded() {
    var gateway = new PushEmailGateway();
    for (int i = 0; i < 150; i++) gateway.send(PushEmailGateway.Channel.EMAIL, "test", "title", null);
    assertEquals(100, gateway.sent().size());
  }
  @Test void schedulerDoesNotTouchSubscriptionsWithoutProvider() {
    new PushDeliveryService(null, null, null).scanAndDeliver();
  }
}
