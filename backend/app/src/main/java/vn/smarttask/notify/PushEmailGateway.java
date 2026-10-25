package vn.smarttask.notify;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * PH-4 push/email provider stub. V1 has no real FCM/APNs/SMTP wired:
 * deliveries are recorded as stub attempts and the gap is stated explicitly
 * (no end-to-end delivery claimed).
 */
@Component
public class PushEmailGateway {
  private static final Logger log = LoggerFactory.getLogger(PushEmailGateway.class);

  public enum Channel { PUSH, EMAIL }

  public record StubDelivery(Channel channel, String recipientRef, String titleKey,
      String status, String note) {}

  private final List<StubDelivery> deliveries = new ArrayList<>();
  private final boolean pushConfigured;
  private final boolean emailConfigured;

  public PushEmailGateway() {
    this(false, false);
  }

  public PushEmailGateway(boolean pushConfigured, boolean emailConfigured) {
    this.pushConfigured = pushConfigured;
    this.emailConfigured = emailConfigured;
  }

  /** Stub: logs intent only, never real provider payload. Returns QUEUED stub. */
  public StubDelivery send(Channel channel, String recipientRef, String titleKey, String correlationId) {
    boolean configured = channel == Channel.PUSH ? pushConfigured : emailConfigured;
    String status = configured ? "QUEUED" : "QUEUED_STUB_NO_PROVIDER";
    String note = configured ? "queued"
        : "STUB: no real FCM/APNs/SMTP wired — end-to-end delivery NOT provided in V1";
    // Never log token or private content: only refs + keys.
    log.info("notify.stub channel={} recipient_ref={} title_key={} status={} correlation_id={}",
        channel, recipientRef, titleKey, status, correlationId);
    StubDelivery d = new StubDelivery(channel, recipientRef, titleKey, status, note);
    synchronized (deliveries) {
      deliveries.add(d);
    }
    return d;
  }

  public List<StubDelivery> sent() {
    synchronized (deliveries) {
      return List.copyOf(deliveries);
    }
  }
}
