package vn.smarttask.notify;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import vn.smarttask.common.AppException;
import vn.smarttask.common.UuidV7;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.common.service.TokenHash;
import vn.smarttask.identity.event.SessionRevokedEvent;
import vn.smarttask.notify.entity.DevicePushRegistration;
import vn.smarttask.notify.repo.DevicePushRegistrationRepository;
import vn.smarttask.notify.service.DevicePushService;

/**
 * F4 push devices EP-176..178 (offline, Mockito): platform/token validation,
 * cross-user token conflict, per-user cap, idempotent unregister with
 * anti-oracle 404, and session-revoke cascade.
 */
@ExtendWith(MockitoExtension.class)
class DevicePushServiceTest {

  @Mock DevicePushRegistrationRepository devices;
  @Mock AuditService audit;

  private DevicePushService service() {
    return new DevicePushService(devices, audit);
  }

  private static DevicePushRegistration row(UUID id, UUID user, UUID session, String status) {
    DevicePushRegistration d = new DevicePushRegistration(id, user, session, "ANDROID_FCM",
        TokenHash.sha256("tok-" + id), "pixel");
    d.setStatus(status);
    return d;
  }

  @Test
  void registerStoresHashOnly() {
    UUID user = UUID.randomUUID(), session = UuidV7.generate();
    when(devices.findByPushTokenHash(any())).thenReturn(Optional.empty());
    when(devices.countByUserIdAndStatus(user, "ACTIVE")).thenReturn(0L);
    when(devices.save(any())).thenAnswer(i -> i.getArgument(0));

    var d = service().register(user, session, "android_fcm", "  raw-token  ", "pixel");

    assertEquals("ANDROID_FCM", d.getPlatform());
    assertEquals(TokenHash.sha256("raw-token"), d.getPushTokenHash());
    assertEquals(session, d.getSessionId());
    assertEquals("ACTIVE", d.getStatus());
    verify(audit).append(eq(user), eq("push.device.register"),
        eq("device_push_registration"), any(), eq("ALLOWED"), any(), any());
  }

  @Test
  void unsupportedPlatformIs400() {
    AppException ex = assertThrows(AppException.class,
        () -> service().register(UUID.randomUUID(), UuidV7.generate(), "WEB_PUSH", "t", null));
    assertEquals(400, ex.getHttpStatus().value());
    assertEquals("PUSH_PLATFORM_UNSUPPORTED", ex.getReasonCode());
  }

  @Test
  void blankTokenIs400() {
    AppException ex = assertThrows(AppException.class,
        () -> service().register(UUID.randomUUID(), UuidV7.generate(), "IOS_APNS", "  ", null));
    assertEquals(400, ex.getHttpStatus().value());
    assertEquals("PUSH_TOKEN_INVALID", ex.getReasonCode());
  }

  @Test
  void tokenOfAnotherUserIs409() {
    UUID user = UUID.randomUUID(), other = UUID.randomUUID();
    DevicePushRegistration mine = row(UuidV7.generate(), other, UuidV7.generate(), "ACTIVE");
    when(devices.findByPushTokenHash(any())).thenReturn(Optional.of(mine));

    AppException ex = assertThrows(AppException.class,
        () -> service().register(user, UuidV7.generate(), "ANDROID_FCM", "tok", null));
    assertEquals(409, ex.getHttpStatus().value());
    assertEquals("PUSH_TOKEN_ALREADY_REGISTERED_OTHER_USER", ex.getReasonCode());
  }

  @Test
  void activeCapIs409() {
    UUID user = UUID.randomUUID();
    when(devices.findByPushTokenHash(any())).thenReturn(Optional.empty());
    when(devices.countByUserIdAndStatus(user, "ACTIVE")).thenReturn(10L);

    AppException ex = assertThrows(AppException.class,
        () -> service().register(user, UuidV7.generate(), "ANDROID_FCM", "tok", null));
    assertEquals(409, ex.getHttpStatus().value());
    assertEquals("DEVICE_REGISTRATION_LIMIT_EXCEEDED", ex.getReasonCode());
  }

  @Test
  void unregisterIsIdempotent() {
    UUID user = UUID.randomUUID(), id = UuidV7.generate();
    DevicePushRegistration d = row(id, user, UuidV7.generate(), "UNREGISTERED");
    when(devices.findById(id)).thenReturn(Optional.of(d));

    var out = service().unregister(user, id);

    assertEquals("UNREGISTERED", out.getStatus());
  }

  @Test
  void unregisterForeignReads404() {
    UUID user = UUID.randomUUID(), id = UuidV7.generate();
    DevicePushRegistration d = row(id, UUID.randomUUID(), UuidV7.generate(), "ACTIVE");
    when(devices.findById(id)).thenReturn(Optional.of(d));

    AppException ex = assertThrows(AppException.class,
        () -> service().unregister(user, id));
    assertEquals(404, ex.getHttpStatus().value());
  }

  @Test
  void sessionRevokeCascadesToActiveRows() {
    UUID user = UUID.randomUUID(), session = UuidV7.generate();
    DevicePushRegistration active = row(UuidV7.generate(), user, session, "ACTIVE");
    when(devices.findBySessionIdAndStatus(session, "ACTIVE")).thenReturn(List.of(active));
    when(devices.save(any())).thenAnswer(i -> i.getArgument(0));

    service().onSessionRevoked(new SessionRevokedEvent(session, user, "REVOKED_LOGOUT"));

    assertEquals("UNREGISTERED", active.getStatus());
    assertNotNull(active.getUnregisteredAt());
  }
}
