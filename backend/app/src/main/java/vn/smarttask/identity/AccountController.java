package vn.smarttask.identity;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.smarttask.common.ApiResult;
import vn.smarttask.common.AppException;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.identity.entity.AppUser;
import vn.smarttask.identity.repo.AppUserRepository;
import vn.smarttask.identity.service.IdentityService;
import vn.smarttask.security.CurrentActor;
import vn.smarttask.security.GrantService;
import vn.smarttask.security.RequiresPermission;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * §7 accounts alias (docs EP-021..023): approve-pending queue + approve /
 * reject over {@link IdentityService} + {@link GrantService}.
 * Legacy {@code POST /auth/approve} and {@code POST /auth/users} are kept
 * (not breaking).
 */
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {
  private final IdentityService identity;
  private final GrantService grants;
  private final AppUserRepository users;
  private final AuditService audit;

  public AccountController(IdentityService identity, GrantService grants,
      AppUserRepository users, AuditService audit) {
    this.identity = identity; this.grants = grants; this.users = users; this.audit = audit;
  }

  private void requireApproveGrant(UUID approver, String action, UUID target) {
    grants.requireGrant(approver, action, "app_user", target, "ACCOUNT_APPROVE_GLOBAL");
  }

  /** EP-021: pending-approval accounts, oldest first (grant-gated). */
  @GetMapping
  @RequiresPermission("account:read")
  public ResponseEntity<ApiResult<List<Object>>> pending() {
    UUID actor = CurrentActor.userId();
    requireApproveGrant(actor, "account.list", null);
    List<Object> items = users.findByStatusOrderByCreatedAtAsc("PENDING").stream()
        .map(u -> (Object) Map.of(
            "user_id", u.getId().toString(),
            "username", u.getUsername() == null ? "" : u.getUsername(),
            "email", u.getEmail() == null ? "" : u.getEmail(),
            "status", u.getStatus() == null ? "" : u.getStatus(),
            "locked", u.isLocked(),
            "locked_until", u.getLockedUntil() == null ? null : u.getLockedUntil().toString()))
        .toList();
    return ResponseEntity.ok(ApiResult.ok(items, "account.list.success"));
  }

  /** EP-022: approve a pending account. */
  @PostMapping("/{id}/approve")
  @RequiresPermission("account:write")
  public ResponseEntity<ApiResult<Object>> approve(@PathVariable UUID id) {
    UUID approver = CurrentActor.userId();
    requireApproveGrant(approver, "account.approve", id);
    var u = identity.approve(approver, id, true, true);
    return ResponseEntity.ok(ApiResult.ok(Map.of("user_id", u.getId().toString()),
        "account.approve.success"));
  }

  /** EP-023: reject a pending account. */
  @PostMapping("/{id}/reject")
  @RequiresPermission("account:write")
  public ResponseEntity<ApiResult<Object>> reject(@PathVariable UUID id) {
    UUID approver = CurrentActor.userId();
    requireApproveGrant(approver, "account.reject", id);
    var u = identity.approve(approver, id, false, true);
    return ResponseEntity.ok(ApiResult.ok(Map.of("user_id", u.getId().toString()),
        "account.reject.success"));
  }

  /** EP-026: lock an account (admin only). Khóa tài khoản và thu hồi mọi phiên đang hoạt động. */
  public record LockRequest(@JsonProperty("locked_until_minutes") Long lockedUntilMinutes) {}

  @PostMapping("/{id}/lock")
  @RequiresPermission("account:write")
  public ResponseEntity<ApiResult<Object>> lock(@PathVariable UUID id,
      @RequestBody(required = false) LockRequest req) {
    UUID actor = CurrentActor.userId();
    requireApproveGrant(actor, "account.lock", id);
    AppUser u = users.findById(id)
        .orElseThrow(() -> AppException.notFound("User not found"));
    u.setLocked(true);
    if (req != null && req.lockedUntilMinutes != null && req.lockedUntilMinutes > 0) {
      u.setLockedUntil(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
          .plusMinutes(req.lockedUntilMinutes));
    } else {
      u.setLockedUntil(null);
    }
    users.save(u);
    // Thu hồi mọi session của user bị khóa: session authentication filter sẽ từ chối
    // vì user không còn active hoặc locked. Revoke session active duy nhất nếu có.
    identity.revokeAllSessions(id);
    auditAppend(actor, "account.lock", id, "ALLOWED");
    return ResponseEntity.ok(ApiResult.ok(Map.of("user_id", u.getId().toString(),
        "locked", u.isLocked(), "locked_until", u.getLockedUntil()),
        "account.lock.success"));
  }

  /** EP-027: unlock an account (admin only). */
  @PostMapping("/{id}/unlock")
  @RequiresPermission("account:write")
  public ResponseEntity<ApiResult<Object>> unlock(@PathVariable UUID id) {
    UUID actor = CurrentActor.userId();
    requireApproveGrant(actor, "account.unlock", id);
    AppUser u = users.findById(id)
        .orElseThrow(() -> AppException.notFound("User not found"));
    u.setLocked(false);
    u.setLockedUntil(null);
    users.save(u);
    auditAppend(actor, "account.unlock", id, "ALLOWED");
    return ResponseEntity.ok(ApiResult.ok(Map.of("user_id", u.getId().toString(),
        "locked", u.isLocked()), "account.unlock.success"));
  }

  /** EP-028: admin resets password to a temporary one.
   *  Temporary password is generated server-side and must be delivered through a
   *  separate out-of-band channel; it is NOT returned in the response to avoid
   *  being logged by HTTP access logs. User is forced to change it at next login.
   */
  @PostMapping("/{id}/reset-password")
  @RequiresPermission("account:write")
  public ResponseEntity<ApiResult<Object>> resetPassword(@PathVariable UUID id) {
    UUID actor = CurrentActor.userId();
    requireApproveGrant(actor, "account.reset_password", id);
    identity.resetPasswordTemp(actor, id);
    auditAppend(actor, "account.reset_password", id, "ALLOWED");
    return ResponseEntity.ok(ApiResult.ok(Map.of("user_id", id.toString(),
        "must_change_password", true), "account.reset_password.success"));
  }

  private void auditAppend(UUID actor, String action, UUID target, String outcome) {
    audit.append(actor, action, "app_user", target, outcome, null, "{}");
  }
}
