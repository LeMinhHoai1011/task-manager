package vn.smarttask.identity;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.smarttask.common.ApiResult;
import vn.smarttask.common.AppException;
import vn.smarttask.identity.service.IdentityService;
import vn.smarttask.security.CurrentActor;
import vn.smarttask.security.GrantService;
import vn.smarttask.security.RequiresPermission;

/**
 * F4 session management (docs EP-006, additive).
 *
 * <p>Forced revoke takes {@code session_id} in the body. The caller must own
 * the session or hold a live {@code SYSTEM_ADMIN} grant (deny-by-default via
 * {@link GrantService}); anything else reads as 404 so session existence is
 * never disclosed to strangers. Every decision writes an audit row inside
 * the service transaction, and revocation fans out to realtime streams,
 * push registrations, and bound refresh tokens.
 */
@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

  private final IdentityService identity;
  private final GrantService grants;

  public SessionController(IdentityService identity, GrantService grants) {
    this.identity = identity;
    this.grants = grants;
  }

  public record RevokeBody(@JsonProperty("session_id") UUID sessionId,
      @JsonProperty("reason") String reason) {}

  @PostMapping("/revoke")
  @RequiresPermission("session:write")
  public ResponseEntity<ApiResult<Object>> revoke(@RequestBody RevokeBody body) {
    if (body == null || body.sessionId() == null) {
      throw AppException.badRequest("SESSION_ID_REQUIRED", "session_id required");
    }
    UUID actor = CurrentActor.userId();
    boolean sa = grants.isSystemAdmin(actor);
    var s = identity.revokeSession(actor, body.sessionId(), sa, body.reason());
    return ResponseEntity.ok(ApiResult.ok(
        Map.of("session_id", s.getId().toString(), "status", s.getStatus()),
        "session.revoke.success"));
  }
}
