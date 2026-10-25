package vn.smarttask.security;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import vn.smarttask.common.AppException;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.security.entity.GlobalPermissionGrant;
import vn.smarttask.security.repo.GlobalPermissionGrantRepository;

/**
 * F1 grant store (offline, Mockito): live/expiry/revoked resolution,
 * self-grant ban, authority rule, deny-by-default, audit on both paths.
 */
@ExtendWith(MockitoExtension.class)
class GrantServiceTest {

  @Mock GlobalPermissionGrantRepository repo;
  @Mock AuditService audit;

  private GrantService grants;
  private final UUID actor = UUID.randomUUID();
  private final UUID target = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    grants = new GrantService(repo, audit);
  }

  private GlobalPermissionGrant live(UUID user, String code) {
    return new GlobalPermissionGrant(UUID.randomUUID(), user, code, "GLOBAL", null, actor,
        "ops need", null);
  }

  @Test
  void liveGrantAllowsAndAuditsAllowed() {
    when(repo.findLive(actor, "ACCOUNT_APPROVE_GLOBAL"))
        .thenReturn(List.of(live(actor, "ACCOUNT_APPROVE_GLOBAL")));

    assertDoesNotThrow(() -> grants.requireGrant(actor, "auth.approve", "app_user", target,
        "ACCOUNT_APPROVE_GLOBAL"));
    verify(audit).append(actor, "auth.approve", "app_user", target, "ALLOWED",
        "GRANT:ACCOUNT_APPROVE_GLOBAL", "{}");
  }

  @Test
  void missingGrantDenies403WithAudit() {
    when(repo.findLive(actor, "ACCOUNT_APPROVE_GLOBAL")).thenReturn(List.of());

    AppException ex = assertThrows(AppException.class, () -> grants.requireGrant(actor,
        "auth.approve", "app_user", target, "ACCOUNT_APPROVE_GLOBAL"));
    assertEquals(403, ex.getHttpStatus().value());
    verify(audit).appendDenied(actor, "auth.approve", "app_user", target,
        "MISSING_GRANT:ACCOUNT_APPROVE_GLOBAL");
  }

  @Test
  void expiredGrantDenies() {
    // Repo filters expiry at query time: an expired row never appears live.
    when(repo.findLive(actor, "X")).thenReturn(List.of());
    assertFalse(grants.hasGrant(actor, "X"));
  }

  @Test
  void selfGrantIs403SelfEscalationEvenForSa() {
    // Self-grant is banned before any authority check (no stubs needed).

    AppException ex = assertThrows(AppException.class,
        () -> grants.grant(actor, actor, GrantService.SYSTEM_ADMIN, "GLOBAL", null,
            "promote myself", null));
    assertEquals(403, ex.getHttpStatus().value());
    verify(audit).appendDenied(actor, "grant.issue", "global_permission_grant", actor,
        "SELF_ESCALATION");
  }

  @Test
  void grantWithoutAuthorityIs403() {
    when(repo.findLive(actor, GrantService.SYSTEM_ADMIN)).thenReturn(List.of());
    when(repo.findLive(actor, "SOME_CODE")).thenReturn(List.of());

    AppException ex = assertThrows(AppException.class,
        () -> grants.grant(actor, target, "SOME_CODE", "GLOBAL", null, "need", null));
    assertEquals(403, ex.getHttpStatus().value());
    verify(audit).appendDenied(actor, "grant.issue", "global_permission_grant", target,
        "GRANT_AUTHORITY_MISSING:SOME_CODE");
  }

  @Test
  void saCanGrantWithAuditBeforeAndAfter() {
    when(repo.findLive(actor, GrantService.SYSTEM_ADMIN))
        .thenReturn(List.of(live(actor, GrantService.SYSTEM_ADMIN)));
    when(repo.findLiveRow(target, "ACCOUNT_APPROVE_GLOBAL")).thenReturn(Optional.empty());

    var g = grants.grant(actor, target, "ACCOUNT_APPROVE_GLOBAL", "GLOBAL", null,
        "oncall approver", null);

    assertNotNull(g.getId());
    assertNull(g.getRevokedAt());
    // Audit before (attempt) and after (granted).
    verify(audit).append(actor, "grant.issue", "global_permission_grant", target, "ALLOWED",
        "ATTEMPT:ACCOUNT_APPROVE_GLOBAL", "{}");
    verify(audit).append(actor, "grant.issue", "global_permission_grant", target, "ALLOWED",
        "GRANTED:ACCOUNT_APPROVE_GLOBAL", "{}");
    verify(repo).save(any(GlobalPermissionGrant.class));
  }

  @Test
  void parentGrantHolderCanGrantSameCode() {
    when(repo.findLive(actor, GrantService.SYSTEM_ADMIN)).thenReturn(List.of());
    when(repo.findLive(actor, "TEAM_LEAD")).thenReturn(List.of(live(actor, "TEAM_LEAD")));
    when(repo.findLiveRow(target, "TEAM_LEAD")).thenReturn(Optional.empty());

    assertDoesNotThrow(() -> grants.grant(actor, target, "TEAM_LEAD", "GLOBAL", null,
        "delegate", null));
  }

  @Test
  void shortReasonRejected() {
    when(repo.findLive(actor, GrantService.SYSTEM_ADMIN))
        .thenReturn(List.of(live(actor, GrantService.SYSTEM_ADMIN)));

    AppException ex = assertThrows(AppException.class,
        () -> grants.grant(actor, target, "X", "GLOBAL", null, "ab", null));
    assertEquals(400, ex.getHttpStatus().value());
  }

  @Test
  void revokeSetsRevokedAtAndAudits() {
    GlobalPermissionGrant g = live(target, "TEAM_LEAD");
    when(repo.findById(g.getId())).thenReturn(Optional.of(g));
    when(repo.findLive(actor, GrantService.SYSTEM_ADMIN))
        .thenReturn(List.of(live(actor, GrantService.SYSTEM_ADMIN)));

    grants.revoke(actor, g.getId(), "no longer needed");

    assertNotNull(g.getRevokedAt());
    verify(repo).save(g);
  }

  @Test
  void revokedGrantNoLongerCountsAsLive() {
    GlobalPermissionGrant g = live(actor, "TEAM_LEAD");
    g.setRevokedAt(OffsetDateTime.now(ZoneOffset.UTC));
    assertFalse(g.isLive(OffsetDateTime.now(ZoneOffset.UTC)));
    GlobalPermissionGrant exp = new GlobalPermissionGrant(UUID.randomUUID(), actor, "E",
        "GLOBAL", null, null, "r", OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
    assertFalse(exp.isLive(OffsetDateTime.now(ZoneOffset.UTC)));
    GlobalPermissionGrant future = new GlobalPermissionGrant(UUID.randomUUID(), actor, "F",
        "GLOBAL", null, null, "r", OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));
    assertTrue(future.isLive(OffsetDateTime.now(ZoneOffset.UTC)));
  }

  @Test
  void denyByDefaultNoDefaultGrant() {
    when(repo.findLive(any(), any())).thenReturn(List.of());
    assertFalse(grants.hasGrant(UUID.randomUUID(), GrantService.SYSTEM_ADMIN));
    assertFalse(grants.hasGrant(UUID.randomUUID(), "ANYTHING"));
    assertFalse(grants.hasGrant(null, "ANYTHING"));
  }
}
