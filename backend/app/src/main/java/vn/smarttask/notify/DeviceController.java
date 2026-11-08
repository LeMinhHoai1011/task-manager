package vn.smarttask.notify;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.smarttask.common.ApiResult;
import vn.smarttask.notify.service.DevicePushService;
import vn.smarttask.security.CurrentActor;
import vn.smarttask.security.Principal;
import vn.smarttask.security.RequiresPermission;

/**
 * F4 push-device endpoints (docs EP-176..178, additive).
 *
 * <p>Registrations bind to the caller's login session
 * ({@code ON DELETE CASCADE}); session revocation invalidates them with no
 * manual step. List responses carry a masked {@code token_hint} — the raw
 * token and its hash are never returned.
 */
@RestController
@RequestMapping("/api/v1/notifications/devices")
public class DeviceController {

  private final DevicePushService devices;

  public DeviceController(DevicePushService devices) {
    this.devices = devices;
  }

  public record DeviceDto(@JsonProperty("id") UUID id,
      @JsonProperty("device_label") String deviceLabel,
      @JsonProperty("platform") String platform,
      @JsonProperty("status") String status,
      @JsonProperty("token_hint") String tokenHint,
      @JsonProperty("endpoint") String endpoint,
      @JsonProperty("auth_key") String authKey,
      @JsonProperty("p256dh_key") String p256dhKey,
      @JsonProperty("session_id") UUID sessionId,
      @JsonProperty("registered_at") OffsetDateTime registeredAt,
      @JsonProperty("last_success_at") OffsetDateTime lastSuccessAt,
      @JsonProperty("failure_count") int failureCount) {}

  public record RegisterBody(@JsonProperty("platform") String platform,
      @JsonProperty("push_token") String pushToken,
      @JsonProperty("device_label") String deviceLabel,
      @JsonProperty("endpoint") String endpoint,
      @JsonProperty("auth_key") String authKey,
      @JsonProperty("p256dh_key") String p256dhKey) {}

  private static DeviceDto toDto(vn.smarttask.notify.entity.DevicePushRegistration d) {
    String hash = d.getPushTokenHash();
    String hint = hash == null || hash.length() < 6 ? "***" : hash.substring(0, 6) + "...";
    return new DeviceDto(d.getId(), d.getDeviceLabel(), d.getPlatform(), d.getStatus(), hint,
        d.getEndpoint(), d.getAuthKey(), d.getP256dhKey(),
        d.getSessionId(), d.getRegisteredAt(), d.getLastSuccessAt(), d.getFailureCount());
  }

  /** EP-176: own registrations, newest first. */
  @GetMapping
  @RequiresPermission("notification:read")
  public ResponseEntity<ApiResult<List<DeviceDto>>> list() {
    UUID actor = CurrentActor.userId();
    var items = devices.list(actor).stream().map(DeviceController::toDto).toList();
    return ResponseEntity.ok(ApiResult.ok(items, "notification.devices.success"));
  }

  /** EP-177: register the caller's session for push (provider stub: hash stored only). */
  @PostMapping
  @RequiresPermission("notification:write")
  public ResponseEntity<ApiResult<DeviceDto>> register(
      @RequestBody RegisterBody body) {
    Principal principal = CurrentActor.require();
    var d = devices.register(principal.userId(), principal.sessionId(), body.platform(),
        body.pushToken(), body.deviceLabel(), body.endpoint(), body.authKey(), body.p256dhKey());
    return ResponseEntity.ok(ApiResult.ok(toDto(d), "notification.device.register.success"));
  }

  /** EP-178: unregister (idempotent; foreign ids read as 404). */
  @DeleteMapping("/{id}")
  @RequiresPermission("notification:write")
  public ResponseEntity<ApiResult<Object>> unregister(@PathVariable UUID id) {
    UUID actor = CurrentActor.userId();
    devices.unregister(actor, id);
    return ResponseEntity.ok(ApiResult.ok(Map.of("id", id.toString()),
        "notification.device.unregister.success"));
  }
}
