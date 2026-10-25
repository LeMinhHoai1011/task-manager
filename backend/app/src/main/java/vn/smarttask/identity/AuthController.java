package vn.smarttask.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.smarttask.common.ApiResult;
import vn.smarttask.common.AppException;
import vn.smarttask.identity.service.IdentityService;
import vn.smarttask.security.CurrentActor;
import vn.smarttask.security.GrantService;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final IdentityService identity;
  private final GrantService grants;

  public AuthController(IdentityService identity, GrantService grants) {
    this.identity = identity;
    this.grants = grants;
  }

  public record LoginRequest(@JsonProperty("username") @NotBlank String username,
      @JsonProperty("password") @NotBlank String password,
      @JsonProperty("device_label") String deviceLabel,
      @JsonProperty("client_type") String clientType) {}
  public record RegisterRequest(@JsonProperty("username") @NotBlank String username,
      @JsonProperty("email") @NotBlank String email,
      @JsonProperty("password") @NotBlank String password,
      @JsonProperty("display_name") @NotBlank String displayName) {}
  public record TokenData(@JsonProperty("access_token") String accessToken,
      @JsonProperty("refresh_token") String refreshToken,
      @JsonProperty("expires_at") OffsetDateTime expiresAt,
      @JsonProperty("user_id") UUID userId) {}
  public record ApproveRequest(@JsonProperty("target_id") UUID targetId,
      @JsonProperty("approve") boolean approve) {}
  public record CreateUserRequest(@JsonProperty("username") @NotBlank String username,
      @JsonProperty("email") @NotBlank String email,
      @JsonProperty("password") @NotBlank String password,
      @JsonProperty("display_name") @NotBlank String displayName) {}

  @PostMapping("/register")
  public ResponseEntity<ApiResult<Object>> register(@Valid @RequestBody RegisterRequest req) {
    var u = identity.register(req.username(), req.email(), req.password(), req.displayName());
    return ResponseEntity.ok(ApiResult.ok(Map.of("user_id", u.getId()), "auth.register.success"));
  }

  @PostMapping("/login")
  public ResponseEntity<ApiResult<TokenData>> login(@Valid @RequestBody LoginRequest req) {
    var r = identity.login(req.username(), req.password(), req.deviceLabel(), req.clientType());
    return ResponseEntity.ok(ApiResult.ok(
        new TokenData(r.accessToken(), r.refreshToken(), r.expiresAt(), r.userId()),
        "auth.login.success"));
  }

  @PostMapping("/logout")
  public ResponseEntity<ApiResult<Object>> logout(@RequestHeader("Authorization") String authz) {
    identity.logout(authz.replace("Bearer ", ""));
    return ResponseEntity.ok(ApiResult.ok(null, "auth.logout.success"));
  }

  @PostMapping("/refresh")
  public ResponseEntity<ApiResult<TokenData>> refresh(@RequestBody Map<String, String> body) {
    var r = identity.refresh(body.get("refresh_token"));
    return ResponseEntity.ok(ApiResult.ok(
        new TokenData(r.accessToken(), r.refreshToken(), r.expiresAt(), r.userId()),
        "auth.refresh.success"));
  }

  /** Reauth ticket is always issued for the authenticated principal, never X-User-Id. */
  @PostMapping("/reauth")
  public ResponseEntity<ApiResult<Object>> reauth(@RequestBody Map<String, String> body) {
    // Legacy alias of /reauth/challenge (kept for current web clients, not breaking).
    return challenge(body);
  }

  /**
   * §7 EP-007 reauth challenge: issues a 5-minute single-use ticket bound to
   * (principal, action, target).
   */
  @PostMapping("/reauth/challenge")
  public ResponseEntity<ApiResult<Object>> challenge(@RequestBody Map<String, String> body) {
    UUID userId = CurrentActor.userId();
    var t = identity.issueReauthTicket(userId, body.getOrDefault("action", "sensitive"),
        body.get("target_id") == null ? null : UUID.fromString(body.get("target_id")));
    return ResponseEntity.ok(ApiResult.ok(
        Map.of("ticket_id", t.getId().toString(), "expires_at", t.getExpiresAt().toString()),
        "auth.reauth.success"));
  }

  /**
   * §7 EP-008 reauth verify: proves the password against the ticket, then
   * consumes it (single-use). Wrong password -> 401, no ticket consumed.
   */
  @PostMapping("/reauth/verify")
  public ResponseEntity<ApiResult<Object>> verify(@RequestBody Map<String, String> body) {
    UUID userId = CurrentActor.userId();
    String ticket = body.get("ticket_id");
    if (ticket == null || ticket.isBlank()) {
      throw AppException.reauthRequired("Missing reauth ticket");
    }
    identity.verifyReauthTicket(userId, UUID.fromString(ticket),
        body.get("password"), body.getOrDefault("action", "sensitive"),
        body.get("target_id") == null ? null : UUID.fromString(body.get("target_id")));
    return ResponseEntity.ok(ApiResult.ok(Map.of("verified", true),
        "auth.reauth.verify.success"));
  }

  /**
   * Account approval: approver is the authenticated principal; the
   * ACCOUNT_APPROVE_GLOBAL grant resolves via {@link GrantService} (deny 403
   * when the grant store has no grant - client headers are never trusted).
   */
  @PostMapping("/approve")
  public ResponseEntity<ApiResult<Object>> approve(@Valid @RequestBody ApproveRequest req) {
    UUID approver = CurrentActor.userId();
    grants.requireGrant(approver, "auth.approve", "app_user", req.targetId(),
        "ACCOUNT_APPROVE_GLOBAL");
    var u = identity.approve(approver, req.targetId(), req.approve(), true);
    return ResponseEntity.ok(ApiResult.ok(Map.of("user_id", u.getId()), "auth.approve.success"));
  }

  /** Admin user creation: creator is the authenticated principal (grant-gated). */
  @PostMapping("/users")
  public ResponseEntity<ApiResult<Object>> createByAdmin(
      @Valid @RequestBody CreateUserRequest req) {
    UUID admin = CurrentActor.userId();
    grants.requireGrant(admin, "auth.createByAdmin", "app_user", null, "ACCOUNT_APPROVE_GLOBAL");
    var u = identity.createByAdmin(admin, req.username(), req.email(), req.password(),
        req.displayName());
    return ResponseEntity.ok(ApiResult.ok(Map.of("user_id", u.getId()), "auth.create.success"));
  }
}
