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
 * which is not yet integrated. Delivery is DISABLED: the scheduler never
 * sends or records success. Registration/session revocation remains active.
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

  /** Disabled provider hook; no delivery or success bookkeeping is performed. */
  @Scheduled(fixedDelayString = "${smarttask.push.scan.delay:60000}")
  @Transactional
  public void scanAndDeliver() {
    // DISABLED: there is no provider. Do not query every subscription, create
    // audit noise, or update last_success_at without a provider acknowledgement.
  }
}
