package vn.smarttask.itg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.test.web.servlet.MvcResult;
import vn.smarttask.common.AppException;
import vn.smarttask.identity.entity.AppUser;
import vn.smarttask.security.BootstrapRunner;
import vn.smarttask.security.GrantService;
import vn.smarttask.security.entity.GlobalPermissionGrant;

/**
 * F2.2/7 GrantIT: bootstrap grants exactly the configured user once;
 * self-grant is 403 even for SA; expired/revoked grants are denied (service
 * level plus the HTTP approve gate).
 */
class GrantIT extends ItgBase {

  @Test
  void bootstrapGrantsOnlyConfiguredUserOnce() {
    // Hermetic bootstrap: scratch DB, wipe grant state + flag first.
    jdbc.update("delete from global_permission_grant");
    jdbc.update("delete from system_setting where key='bootstrap_completed'");

    var candidate = activeUser("itgbs", "Secret123!");
    var other = activeUser("itgbo", "Secret123!");

    BootstrapRunner runner = new BootstrapRunner(
        settingRepo(), grantsRepo(), users, grants, candidate.getUsername());
    runner.run(new DefaultApplicationArguments());

    assertTrue(grants.hasGrant(candidate.getId(), GrantService.SYSTEM_ADMIN));
    assertFalse(grants.hasGrant(other.getId(), GrantService.SYSTEM_ADMIN),
        "only the configured user must be bootstrapped");
    assertEquals(1, grants.listLive(candidate.getId(), GrantService.SYSTEM_ADMIN).size());

    // second run is idempotent: still exactly one live SA grant
    runner.run(new DefaultApplicationArguments());
    assertEquals(1, grants.listLive(candidate.getId(), GrantService.SYSTEM_ADMIN).size());
    assertEquals("true", jdbc.queryForObject(
        "select value from system_setting where key='bootstrap_completed'", String.class));
  }

  @Test
  void bootstrapActivatesConfiguredPendingUser() {
    jdbc.update("delete from global_permission_grant");
    jdbc.update("delete from system_setting where key='bootstrap_completed'");

    // Self-registration leaves the account PENDING: before the first SA there is
    // no approver grant, so the documented local bootstrap must activate it.
    var pending = identity.register("itgbp_" + tag(), "itgbp_" + tag() + "@itg.io",
        "Secret123!", "Bootstrap Pending");
    assertEquals("PENDING", pending.getStatus());

    BootstrapRunner runner = new BootstrapRunner(
        settingRepo(), grantsRepo(), users, grants, pending.getUsername(), identity);
    runner.run(new DefaultApplicationArguments());

    AppUser fresh = users.findById(pending.getId()).orElseThrow();
    assertEquals("ACTIVE", fresh.getStatus());
    assertTrue(fresh.isActive());
    assertTrue(grants.hasGrant(pending.getId(), GrantService.SYSTEM_ADMIN));
    assertFalse(loginToken(pending.getUsername(), "Secret123!").isBlank(),
        "bootstrapped admin must be able to log in");
    assertEquals(1, jdbc.queryForObject(
        "select count(*) from workspace where owner_user_id=? and type='PERSONAL'",
        Integer.class, pending.getId()));
  }

  @Test
  void selfGrantIs403EvenForSystemAdmin() {
    var sa = activeUser("itggs", "Secret123!");
    grants.grantBootstrap(sa.getId(), GrantService.SYSTEM_ADMIN);

    AppException ex = org.junit.jupiter.api.Assertions.assertThrows(AppException.class,
        () -> grants.grant(sa.getId(), sa.getId(), GrantService.SYSTEM_ADMIN,
            "GLOBAL", null, "promote myself", null));
    assertEquals(403, ex.getHttpStatus().value());
  }

  @Test
  void expiredAndRevokedGrantsAreDenied() throws Exception {
    var sa = activeUser("itgge", "Secret123!");
    grants.grantBootstrap(sa.getId(), GrantService.SYSTEM_ADMIN);
    var target = activeUser("itggt", "Secret123!");

    // expired grant -> not live
    GlobalPermissionGrant expired = grants.grant(sa.getId(), target.getId(),
        "ACCOUNT_APPROVE_GLOBAL", "GLOBAL", null, "short stint",
        OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));
    assertTrue(expired.getExpiresAt() != null);
    assertFalse(grants.hasGrant(target.getId(), "ACCOUNT_APPROVE_GLOBAL"));

    // live grant then revoked -> not live
    var fresh = activeUser("itggf", "Secret123!");
    GlobalPermissionGrant g = grants.grant(sa.getId(), fresh.getId(),
        "ACCOUNT_APPROVE_GLOBAL", "GLOBAL", null, "oncall approver", null);
    assertTrue(grants.hasGrant(fresh.getId(), "ACCOUNT_APPROVE_GLOBAL"));
    grants.revoke(sa.getId(), g.getId(), "no longer needed");
    assertFalse(grants.hasGrant(fresh.getId(), "ACCOUNT_APPROVE_GLOBAL"));

    // HTTP gate: approver whose grant expired cannot approve accounts
    String expiredToken = loginToken(target.getUsername(), "Secret123!");
    var pending = identity.register("itggp_" + tag(), "itggp_" + tag() + "@itg.io",
        "Secret123!", "Pending");
    MvcResult r = doPost("/api/v1/auth/approve", expiredToken,
        Map.of("target_id", pending.getId().toString(), "approve", true));
    assertEquals(403, r.getResponse().getStatus());
  }

  @Autowired vn.smarttask.admin.repo.SystemSettingRepository settings;
  @Autowired vn.smarttask.security.repo.GlobalPermissionGrantRepository grantRows;

  private vn.smarttask.admin.repo.SystemSettingRepository settingRepo() {
    return settings;
  }

  private vn.smarttask.security.repo.GlobalPermissionGrantRepository grantsRepo() {
    return grantRows;
  }
}
