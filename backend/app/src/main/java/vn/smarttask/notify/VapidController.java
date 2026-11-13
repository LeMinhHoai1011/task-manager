package vn.smarttask.notify;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.smarttask.common.ApiResult;
import vn.smarttask.notify.entity.DevicePushRegistration;
import vn.smarttask.notify.repo.DevicePushRegistrationRepository;
import vn.smarttask.notify.service.VapidKeyService;
import vn.smarttask.security.CurrentActor;
import vn.smarttask.security.RequiresPermission;

/** VAPID public key endpoint and push delivery for Web Push. */
@RestController
@RequestMapping("/api/v1/notifications/vapid")
public class VapidController {

  private final VapidKeyService vapid;
  private final DevicePushRegistrationRepository devices;

  public VapidController(VapidKeyService vapid, DevicePushRegistrationRepository devices) {
    this.vapid = vapid; this.devices = devices;
  }

  /** EP-179: public key for client PushManager.subscribe. No auth required. */
  @GetMapping("/public-key")
  public ResponseEntity<Map<String, String>> publicKey() {
    return ResponseEntity.ok(Map.of(
        "publicKey", vapid.getPublicKey(),
        "subject", "mailto:smarttask@localhost"
    ));
  }

  /** EP-180: current user's VAPID subscription status (has active WEB_PUSH registration). */
  @GetMapping("/subscription-status")
  @RequiresPermission("notification:read")
  public ResponseEntity<Map<String, Object>> subscriptionStatus() {
    UUID userId = CurrentActor.userId();
    boolean hasSubscription = devices.findByUserIdOrderByRegisteredAtDesc(userId).stream()
        .anyMatch(d -> "WEB_PUSH".equalsIgnoreCase(d.getPlatform())
            && "ACTIVE".equalsIgnoreCase(d.getStatus())
            && d.getEndpoint() != null);
    return ResponseEntity.ok(Map.of("subscribed", hasSubscription, "delivery_status", "DISABLED"));
  }

  /** EP-181: list active WEB_PUSH subscriptions for the current user. */
  @GetMapping("/subscriptions")
  @RequiresPermission("notification:read")
  public ResponseEntity<Map<String, Object>> subscriptions() {
    UUID userId = CurrentActor.userId();
    List<Map<String, String>> list = devices.findByUserIdOrderByRegisteredAtDesc(userId).stream()
        .filter(d -> "WEB_PUSH".equalsIgnoreCase(d.getPlatform())
            && "ACTIVE".equalsIgnoreCase(d.getStatus())
            && d.getEndpoint() != null)
        .map(d -> Map.of(
            "id", d.getId().toString(),
            "endpoint", d.getEndpoint(),
            "auth_key", d.getAuthKey(),
            "p256dh_key", d.getP256dhKey(),
            "device_label", d.getDeviceLabel()))
        .toList();
    return ResponseEntity.ok(Map.of("subscriptions", list));
  }

  /** EP-182: provider stub - record that a push was attempted for a subscription.
   *  Actual delivery requires a web-push library (not yet integrated). */
  @PostMapping("/push/send")
  @RequiresPermission("notification:write")
  public ResponseEntity<Map<String, Object>> sendPush(@RequestBody Map<String, String> body) {
    UUID userId = CurrentActor.userId();
    String subscriptionId = body.get("subscription_id");
    String title = body.get("title");
    String bodyText = body.get("body");
    if (subscriptionId == null || title == null || bodyText == null) {
      throw vn.smarttask.common.AppException.badRequest("MISSING_FIELDS",
          "subscription_id, title, body are required");
    }
    DevicePushRegistration d = devices.findById(UUID.fromString(subscriptionId))
        .filter(r -> r.getUserId().equals(userId))
        .orElseThrow(() -> vn.smarttask.common.AppException.notFound("Subscription not found"));
    throw vn.smarttask.common.AppException.storageUnavailable("PUSH_PROVIDER_DISABLED",
        "Web Push delivery is disabled: no provider is implemented");
  }
}
