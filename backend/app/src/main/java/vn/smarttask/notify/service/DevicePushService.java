package vn.smarttask.notify.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.smarttask.common.AppException;
import vn.smarttask.common.UuidV7;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.common.service.TokenHash;
import vn.smarttask.identity.event.SessionRevokedEvent;
import vn.smarttask.notify.entity.DevicePushRegistration;
import vn.smarttask.notify.repo.DevicePushRegistrationRepository;

/**
 * F4 push-device registry over {@code device_push_registration} (EP-176..178).
 *
 * <p>Provider stub (DEC-46 open): rows record the SHA-256 of the push token
 * only; no raw token is stored and nothing is sent. Every registration is
 * bound to the login {@code session_id}; session revocation marks the row
 * UNREGISTERED in the same fan-out that closes realtime streams (no manual
 * step needed).
 */
@Service
public class DevicePushService {

  static final Set<String> PLATFORMS = Set.of("ANDROID_FCM", "IOS_APNS", "WEB_PUSH");
  /** Cap on ACTIVE registrations per user (409 beyond it). */
  static final long MAX_ACTIVE_PER_USER = 10L;

  private final DevicePushRegistrationRepository devices;
  private final AuditService audit;

  public DevicePushService(DevicePushRegistrationRepository devices, AuditService audit) {
    this.devices = devices; this.audit = audit;
  }

  @Transactional(readOnly = true)
  public List<DevicePushRegistration> list(UUID userId) {
    return devices.findByUserIdOrderByRegisteredAtDesc(userId);
  }

  @Transactional
  public DevicePushRegistration register(UUID userId, UUID sessionId, String platform,
      String pushToken, String deviceLabel) {
    return register(userId, sessionId, platform, pushToken, deviceLabel, null, null, null);
  }

  @Transactional
  public DevicePushRegistration register(UUID userId, UUID sessionId, String platform,
      String pushToken, String deviceLabel,
      String endpoint, String authKey, String p256dhKey) {
    if (platform == null || !PLATFORMS.contains(platform.trim().toUpperCase())) {
      audit.appendDenied(userId, "push.device.register", "device_push_registration", null,
          "PUSH_PLATFORM_UNSUPPORTED");
      throw AppException.badRequest("PUSH_PLATFORM_UNSUPPORTED", "Unsupported platform");
    }
    if (pushToken == null || pushToken.isBlank()) {
      audit.appendDenied(userId, "push.device.register", "device_push_registration", null,
          "PUSH_TOKEN_INVALID");
      throw AppException.badRequest("PUSH_TOKEN_INVALID", "Push token required");
    }
    String hash = TokenHash.sha256(pushToken.trim());
    var existing = devices.findByPushTokenHash(hash);
    if (existing.isPresent()) {
      if (!existing.get().getUserId().equals(userId)) {
        audit.appendDenied(userId, "push.device.register", "device_push_registration",
            existing.get().getId(), "PUSH_TOKEN_ALREADY_REGISTERED_OTHER_USER");
        throw AppException.stateConflict("PUSH_TOKEN_ALREADY_REGISTERED_OTHER_USER",
            "Token registered to another user");
      }
      if ("ACTIVE".equals(existing.get().getStatus())) {
        audit.appendDenied(userId, "push.device.register", "device_push_registration",
            existing.get().getId(), "PUSH_TOKEN_ALREADY_REGISTERED");
        throw AppException.stateConflict("PUSH_TOKEN_ALREADY_REGISTERED",
            "Token already registered");
      }
      // Reactivate existing and update web push fields
      DevicePushRegistration d = existing.get();
      d.setStatus("ACTIVE");
      d.setUnregisteredAt(null);
      if (endpoint != null) d.setEndpoint(endpoint);
      if (authKey != null) d.setAuthKey(authKey);
      if (p256dhKey != null) d.setP256dhKey(p256dhKey);
      devices.save(d);
      audit.append(userId, "push.device.register", "device_push_registration", d.getId(),
          "ALLOWED", null, "{}");
      return d;
    }
    if (devices.countByUserIdAndStatus(userId, "ACTIVE") >= MAX_ACTIVE_PER_USER) {
      audit.appendDenied(userId, "push.device.register", "device_push_registration", null,
          "DEVICE_REGISTRATION_LIMIT_EXCEEDED");
      throw AppException.stateConflict("DEVICE_REGISTRATION_LIMIT_EXCEEDED",
          "Too many active devices");
    }
    DevicePushRegistration d = new DevicePushRegistration(UuidV7.generate(), userId, sessionId,
        platform.trim().toUpperCase(), hash, deviceLabel);
    if (endpoint != null) d.setEndpoint(endpoint);
    if (authKey != null) d.setAuthKey(authKey);
    if (p256dhKey != null) d.setP256dhKey(p256dhKey);
    devices.save(d);
    audit.append(userId, "push.device.register", "device_push_registration", d.getId(),
        "ALLOWED", null, "{}");
    return d;
  }

  /** Idempotent unregister: unknown/foreign ids read as 404 (anti-oracle). */
  @Transactional
  public DevicePushRegistration unregister(UUID userId, UUID registrationId) {
    DevicePushRegistration d = devices.findById(registrationId)
        .filter(r -> r.getUserId().equals(userId))
        .orElseThrow(() -> {
          audit.appendDenied(userId, "push.device.unregister", "device_push_registration",
              registrationId, "DEVICE_REGISTRATION_NOT_OWNED");
          return AppException.notFound("Device registration not found");
        });
    if (!"ACTIVE".equals(d.getStatus())) {
      return d; // DELETE is idempotent: already-unregistered still answers success
    }
    d.setStatus("UNREGISTERED");
    d.setUnregisteredAt(OffsetDateTime.now(ZoneOffset.UTC));
    devices.save(d);
    audit.append(userId, "push.device.unregister", "device_push_registration", d.getId(),
        "ALLOWED", null, "{}");
    return d;
  }

  /** Session-revoke CASCADE: every ACTIVE registration of the session goes UNREGISTERED. */
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
}
