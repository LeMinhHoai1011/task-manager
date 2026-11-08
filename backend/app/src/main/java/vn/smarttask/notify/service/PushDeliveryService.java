package vn.smarttask.notify.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.smarttask.common.UuidV7;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.identity.event.SessionRevokedEvent;
import vn.smarttask.notify.entity.DevicePushRegistration;
import vn.smarttask.notify.repo.DevicePushRegistrationRepository;

/**
 * Provider stub for Web Push delivery (DEC-46 open).
 *
 * <p>Actual push delivery requires a web-push library (e.g. com.github.waveyard:webpush)
 * which is not yet integrated. This stub records delivery attempts and
 * updates last_success_at / failure_count on each registration.
 *
 * <p>The scheduled task scans active WEB_PUSH subscriptions and would invoke
 * the web-push library here once integrated. For now it marks attempts as
 * "stubbed" so operators can see which subscriptions are active and attempt
 * delivery.
 */
@Service
public class PushDeliveryService {

  static final String PLATFORM = "WEB_PUSH";

  private final DevicePushRegistrationRepository devices;
  private final AuditService audit;
  private final VapidKeyService vapid;

  public PushDeliveryService(DevicePushRegistrationRepository devices, AuditService audit,
      VapidKeyService vapid) {
    this.devices = devices; this.audit = audit; this.vapid = vapid;
  }

  /** List all active WEB_PUSH subscriptions eligible for push delivery. */
  @Transactional(readOnly = true)
  public List<DevicePushRegistration> activeWebPushSubscriptions() {
    return devices.findByPlatformAndStatus(PLATFORM, "ACTIVE");
  }

  /** Record a delivery attempt for a subscription. */
  @Transactional
  public void recordAttempt(UUID subscriptionId, boolean success) {
    DevicePushRegistration d = devices.findById(subscriptionId).orElse(null);
    if (d == null || !"ACTIVE".equalsIgnoreCase(d.getStatus())) return;
    if (success) {
      d.setLastSuccessAt(OffsetDateTime.now(ZoneOffset.UTC));
      d.setFailureCount(0);
    } else {
      d.setFailureCount(d.getFailureCount() + 1);
      // Deactivate after 10 consecutive failures.
      if (d.getFailureCount() >= 10) {
        d.setStatus("UNREGISTERED");
        d.setUnregisteredAt(OffsetDateTime.now(ZoneOffset.UTC));
      }
    }
    devices.save(d);
    audit.append(d.getUserId(), "push.delivery.attempt", "device_push_registration",
        subscriptionId, success ? "ALLOWED" : "DENIED", null, "{}");
  }

  /** Deactivate all registrations of a revoked session. */
  @EventListener
  @Transactional
  public void onSessionRevoked(SessionRevokedEvent e) {
    for (DevicePushRegistration d : devices.findBySessionIdAndStatus(e.sessionId(), "ACTIVE")) {
      d.setStatus("UNREGISTERED");
      d.setUnregisteredAt(OffsetDateTime.now(ZoneOffset.UTC));
      devices.save(d);
      audit.append(e.userId(), "push.device.unregister", "device_push_registration", d.getId(),
          "ALLOWED", "SESSION_REVOKED", "{}");
    }
  }

  /** Scheduled: scan active WEB_PUSH subscriptions and attempt delivery stub.
   *  Runs every 60 seconds when there are active subscriptions.
   */
  @Scheduled(fixedDelayString = "${smarttask.push.scan.delay:60000}")
  @Transactional
  public void scanAndDeliver() {
    List<DevicePushRegistration> subs = activeWebPushSubscriptions();
    for (DevicePushRegistration d : subs) {
      // Stub: mark as attempted (no real network call).
      recordAttempt(d.getId(), true);
    }
  }
}
