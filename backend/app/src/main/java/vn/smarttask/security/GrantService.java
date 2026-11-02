package vn.smarttask.security;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.smarttask.common.AppException;
import vn.smarttask.common.UuidV7;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.security.entity.GlobalPermissionGrant;
import vn.smarttask.security.repo.GlobalPermissionGrantRepository;

/**
 * F1 grant resolution over the {@code global_permission_grant} store.
 *
 * <p>Deny-by-default: no row -&gt; no permission (403 + {@code MISSING_GRANT}
 * audit). There are no default grants. Client headers
 * ({@code X-Is-Admin}/{@code X-Has-Grant}) are never consulted.
 *
 * <p>Controls: self-grant is always 403 {@code SELF_ESCALATION} (even for
 * SA); grant/revoke require the actor to be SA (live {@code SYSTEM_ADMIN}
 * grant) or to already hold the parent grant; every grant/revoke writes an
 * audit row before the decision and after it. Grants never confer the right
 * to read private content — SA audit/content reads stay redacted at the
 * controller layer.
 */
@Service
public class GrantService {
  public static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";

  private final GlobalPermissionGrantRepository grants;
  private final AuditService audit;

  @org.springframework.beans.factory.annotation.Autowired
  public GrantService(GlobalPermissionGrantRepository grants, AuditService audit) {
    this.grants = grants;
    this.audit = audit;
  }

  /**
   * Legacy/F0 constructor (unit-test wiring without a store): with no grant
   * repository every check resolves to deny-by-default (403 +
   * {@code MISSING_GRANT} audit). Production wiring always uses the
   * two-arg constructor.
   */
  public GrantService(AuditService audit) {
    this.grants = null;
    this.audit = audit;
  }

  /** Live-grant check (expiry + revocation aware). Never throws. */
  @Transactional(readOnly = true)
  public boolean hasGrant(UUID userId, String grant) {
    if (userId == null || grant == null || grants == null) return false;
    return grants.findLive(userId,grant).stream().anyMatch(g->"GLOBAL".equals(g.getScopeType())&&g.getScopeId()==null);
  }

  /** Project custom roles reuse live grant rows; they never confer global authority. */
  @Transactional(readOnly=true)
  public boolean hasProjectPermission(UUID userId,UUID projectId,String code) {
    return grants!=null && grants.findLive(userId,code).stream().anyMatch(g->"PROJECT".equals(g.getScopeType())&&projectId.equals(g.getScopeId()));
  }

  @Transactional
  public void replaceProjectRole(UUID actor,UUID target,UUID project,String name,java.util.Set<String> permissions) {
    // Caller must establish project-manager authority and active non-Guest participation.
    for(String code:java.util.Set.of("TASK_METADATA_WRITE","TASK_ASSIGNMENT_WRITE")) {
      for(var grant:grants.findLive(target,code))if("PROJECT".equals(grant.getScopeType())&&project.equals(grant.getScopeId())) {
        grant.setRevokedAt(OffsetDateTime.now(ZoneOffset.UTC));grants.save(grant);
      }
    }
    grants.flush();
    for(String code:permissions)grants.save(new GlobalPermissionGrant(UuidV7.generate(),target,code,"PROJECT",project,actor,"CUSTOM_ROLE:"+name,null));
    audit.append(actor,"project.custom_role.set","project",project,"ALLOWED",null,"{}");
  }

  /** SA check: live {@code SYSTEM_ADMIN} grant. */
  @Transactional(readOnly = true)
  public boolean isSystemAdmin(UUID userId) {
    return hasGrant(userId, SYSTEM_ADMIN);
  }

  /** Throws 403 + audit when no live grant row exists. */
  public void requireGrant(UUID userId, String action, String entityType, UUID entityId,
      String grant) {
    if (!hasGrant(userId, grant)) {
      audit.appendDenied(userId, action, entityType, entityId, "MISSING_GRANT:" + grant);
      throw AppException.forbidden("Missing grant " + grant);
    }
    audit.append(userId, action, entityType, entityId, "ALLOWED", "GRANT:" + grant, "{}");
  }

  /**
   * Issue a grant. Self-grant (actor == target) is always 403
   * {@code SELF_ESCALATION}. Otherwise the actor must be SA or already hold
   * the same grant code (parent grant).
   */
  @Transactional
  public GlobalPermissionGrant grant(UUID actor, UUID targetUserId, String code,
      String scopeType, UUID scopeId, String reason, OffsetDateTime expiresAt) {
    if (actor != null && actor.equals(targetUserId)) {
      audit.appendDenied(actor, "grant.issue", "global_permission_grant", targetUserId,
          "SELF_ESCALATION");
      throw AppException.forbidden("Self-grant forbidden");
    }
    audit.append(actor, "grant.issue", "global_permission_grant", targetUserId,
        "ALLOWED", "ATTEMPT:" + code, "{}");
    requireGrantAuthority(actor, code, "grant.issue", targetUserId);
    if (reason == null || reason.trim().length() < 3) {
      audit.appendDenied(actor, "grant.issue", "global_permission_grant", targetUserId,
          "REASON_SHORT");
      throw AppException.badRequest("REASON_SHORT", "Reason >= 3 chars required");
    }
    var existing = grants.findLiveRow(targetUserId, code);
    if (existing.isPresent()) {
      audit.appendDenied(actor, "grant.issue", "global_permission_grant", targetUserId,
          "GRANT_ALREADY_LIVE");
      throw AppException.stateConflict("GRANT_ALREADY_LIVE", "Live grant already exists");
    }
    GlobalPermissionGrant g = new GlobalPermissionGrant(UuidV7.generate(), targetUserId, code,
        scopeType, scopeId, actor, reason.trim(), expiresAt);
    grants.save(g);
    audit.append(actor, "grant.issue", "global_permission_grant", targetUserId,
        "ALLOWED", "GRANTED:" + code, "{}");
    return g;
  }

  /** Revoke a live grant. Same authority rule as {@link #grant}. */
  @Transactional
  public GlobalPermissionGrant revoke(UUID actor, UUID grantId, String reason) {
    GlobalPermissionGrant g = grants.findById(grantId)
        .orElseThrow(() -> AppException.notFound("Grant not found"));
    audit.append(actor, "grant.revoke", "global_permission_grant", g.getUserId(),
        "ALLOWED", "ATTEMPT:" + g.getCode(), "{}");
    requireGrantAuthority(actor, g.getCode(), "grant.revoke", g.getUserId());
    if (g.getRevokedAt() != null) {
      throw AppException.stateConflict("GRANT_ALREADY_REVOKED", "Grant already revoked");
    }
    g.setRevokedAt(OffsetDateTime.now(ZoneOffset.UTC));
    if (reason != null && !reason.isBlank()) g.setReason(reason);
    grants.save(g);
    audit.append(actor, "grant.revoke", "global_permission_grant", g.getUserId(),
        "ALLOWED", "REVOKED:" + g.getCode(), "{}");
    return g;
  }

  /** Bootstrap-only issue (granted_by = null, reason BOOTSTRAP). Not exposed via API. */
  @Transactional
  public GlobalPermissionGrant grantBootstrap(UUID targetUserId, String code) {
    var existing = grants.findLiveRow(targetUserId, code);
    if (existing.isPresent()) return existing.get();
    GlobalPermissionGrant g = new GlobalPermissionGrant(UuidV7.generate(), targetUserId, code,
        "GLOBAL", null, null, "BOOTSTRAP", null);
    grants.save(g);
    audit.append(null, "grant.bootstrap", "global_permission_grant", targetUserId,
        "ALLOWED", "GRANTED:" + code, "{}");
    return g;
  }

  @Transactional(readOnly = true)
  public List<GlobalPermissionGrant> listLive(UUID userId, String code) {
    return grants.findLive(userId, code);
  }

  private void requireGrantAuthority(UUID actor, String code, String action, UUID target) {
    if (actor != null && isSystemAdmin(actor)) return;
    if (actor != null && hasGrant(actor, code)) return;
    audit.appendDenied(actor, action, "global_permission_grant", target,
        "GRANT_AUTHORITY_MISSING:" + code);
    throw AppException.forbidden("Grant authority required for " + code);
  }
}
