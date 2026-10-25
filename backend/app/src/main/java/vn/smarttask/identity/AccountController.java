package vn.smarttask.identity;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.smarttask.common.ApiResult;
import vn.smarttask.identity.repo.AppUserRepository;
import vn.smarttask.identity.service.IdentityService;
import vn.smarttask.security.CurrentActor;
import vn.smarttask.security.GrantService;
import vn.smarttask.security.RequiresPermission;

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

  public AccountController(IdentityService identity, GrantService grants,
      AppUserRepository users) {
    this.identity = identity; this.grants = grants; this.users = users;
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
            "status", u.getStatus() == null ? "" : u.getStatus()))
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
}
