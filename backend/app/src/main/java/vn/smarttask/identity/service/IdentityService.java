package vn.smarttask.identity.service;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.smarttask.common.AppException;
import vn.smarttask.common.UuidV7;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.common.service.OutboxService;
import vn.smarttask.common.service.TokenHash;
import vn.smarttask.identity.entity.*;
import vn.smarttask.identity.event.SessionRevokedEvent;
import vn.smarttask.identity.event.UserAccessRevokedEvent;
import vn.smarttask.identity.repo.*;
import vn.smarttask.security.GrantService;
import vn.smarttask.workspace.entity.Workspace;
import vn.smarttask.workspace.entity.WorkspaceMembership;
import vn.smarttask.workspace.repo.WorkspaceMembershipRepository;
import vn.smarttask.workspace.repo.WorkspaceRepository;

/**
 * Identity PH-1 manual implementation: single-session, opaque UUIDv7-hex tokens.
 */
@Service
public class IdentityService {
  private final AppUserRepository users;
  private final UserSessionRepository sessions;
  private final UserRefreshTokenRepository refreshTokens;
  private final ReauthTicketRepository reauthTickets;
  private final RecoveryCodeRepository recoveryCodes;
  private final WorkspaceRepository workspaces;
  private final WorkspaceMembershipRepository wsMembers;
  private final AuditService audit;
  private final OutboxService outbox;
  private final PasswordEncoder encoder = new BCryptPasswordEncoder();
  private final SecureRandom random = new SecureRandom();
  /**
   * F4 revocation fan-out (realtime close + push-device invalidation).
   * Nullable: the legacy 10-arg constructor (used by offline unit tests)
   * wires no publisher and revocation events are skipped.
   */
  private final ApplicationEventPublisher events;

  private final boolean selfRegistration;
  private final GrantService grants;

  /** Legacy constructor (offline unit wiring): no revocation fan-out. */
  public IdentityService(AppUserRepository users, UserSessionRepository sessions,
      UserRefreshTokenRepository refreshTokens, ReauthTicketRepository reauthTickets,
      RecoveryCodeRepository recoveryCodes, WorkspaceRepository workspaces,
      WorkspaceMembershipRepository wsMembers, AuditService audit, OutboxService outbox,
      @Value("${smarttask.self-registration:true}") boolean selfRegistration) {
    this(users, sessions, refreshTokens, reauthTickets, recoveryCodes, workspaces, wsMembers,
        audit, outbox, selfRegistration, null, null);
  }

  @Autowired
  public IdentityService(AppUserRepository users, UserSessionRepository sessions,
      UserRefreshTokenRepository refreshTokens, ReauthTicketRepository reauthTickets,
      RecoveryCodeRepository recoveryCodes, WorkspaceRepository workspaces,
      WorkspaceMembershipRepository wsMembers, AuditService audit, OutboxService outbox,
      @Value("${smarttask.self-registration:true}") boolean selfRegistration,
      ApplicationEventPublisher events, GrantService grants) {
    this.users = users; this.sessions = sessions; this.refreshTokens = refreshTokens;
    this.reauthTickets = reauthTickets; this.recoveryCodes = recoveryCodes;
    this.workspaces = workspaces; this.wsMembers = wsMembers;
    this.audit = audit; this.outbox = outbox; this.selfRegistration = selfRegistration;
    this.events = events;
    this.grants = grants;
  }

  private void publish(Object event) {
    if (events != null) events.publishEvent(event);
  }

  public record LoginResult(String accessToken, String refreshToken, OffsetDateTime expiresAt,
      UUID userId, boolean isSystemAdmin, boolean mustChangePassword) {}

  private static String opaqueToken() {
    return UuidV7.generate().toString().replace("-", "");
  }

  private void ensurePersonalWorkspace(AppUser u) {
    long n = workspaces.countByOwnerUserIdAndTypeAndStatusNot(u.getId(), "PERSONAL", "DELETED");
    if (n == 0) {
      Workspace w = new Workspace(UuidV7.generate(), u.getUsername() + "-personal", u.getId(), "PERSONAL");
      workspaces.save(w);
      wsMembers.save(new WorkspaceMembership(UuidV7.generate(), w.getId(), u.getId(), "OWNER"));
    }
  }

  @Transactional
  public AppUser register(String username, String email, String password, String displayName) {
    if (!selfRegistration) {
      audit.appendDenied(null, "auth.register", "app_user", null, "SELF_REG_DISABLED");
      throw AppException.forbidden("Self registration disabled");
    }
    if (users.findByUsernameIgnoreCase(username).isPresent()
        || users.findByEmailIgnoreCase(email).isPresent()) {
      audit.appendDenied(null, "auth.register", "app_user", null, "DUPLICATE");
      throw AppException.stateConflict("DUPLICATE_USER", "User exists");
    }
    AppUser u = new AppUser(UuidV7.generate(), username, email,
        encoder.encode(password), displayName);
    u.setStatus("ACTIVE");
    u.setActive(true);
    users.save(u);
    ensurePersonalWorkspace(u);
    audit.append(u.getId(), "auth.register", "app_user", u.getId(), "ALLOWED", null, "{}");
    outbox.emit("app_user", u.getId(), "user.registered", "{}");
    return u;
  }

  /**
   * Local one-time bootstrap activation: the operator explicitly configured
   * this username as the first System Admin, so the account is activated here.
   * It cannot self-approve and no approver grant exists yet; this is the
   * documented local bootstrap path, not an API bypass. Idempotent.
   */
  @Transactional
  public void activateBootstrapAdmin(UUID userId) {
    AppUser u = users.findById(userId).orElseThrow(() -> AppException.notFound("User not found"));
    if (u.isActive() && "ACTIVE".equals(u.getStatus())) return;
    u.setStatus("ACTIVE");
    u.setActive(true);
    users.save(u);
    ensurePersonalWorkspace(u);
    audit.append(userId, "auth.bootstrap.activate", "app_user", userId, "ALLOWED",
        "BOOTSTRAP", "{}");
    outbox.emit("app_user", userId, "user.approved", "{}");
  }

  /** Grant ACCOUNT_APPROVE_GLOBAL approves account; does not pull read grants. */
  @Transactional
  public AppUser approve(UUID approverId, UUID targetId, boolean approve, boolean approverHasApproveGrant) {
    if (!approverHasApproveGrant) {
      audit.appendDenied(approverId, "auth.approve", "app_user", targetId, "MISSING_GRANT");
      throw AppException.forbidden("Missing ACCOUNT_APPROVE_GLOBAL");
    }
    AppUser u = users.findById(targetId).orElseThrow(() -> AppException.notFound("User not found"));
    if (approve) {
      u.setStatus("ACTIVE");
      u.setActive(true);
      ensurePersonalWorkspace(u);
      audit.append(approverId, "auth.approve", "app_user", targetId, "ALLOWED", null, "{}");
      outbox.emit("app_user", targetId, "user.approved", "{}");
    } else {
      u.setStatus("REJECTED");
      audit.append(approverId, "auth.reject", "app_user", targetId, "ALLOWED", null, "{}");
      // F4: rejected accounts lose every realtime stream immediately.
      publish(new UserAccessRevokedEvent(targetId, "REJECTED"));
    }
    return users.save(u);
  }

  @Transactional
  public AppUser createByAdmin(UUID adminId, String username, String email, String password, String displayName) {
    AppUser u = new AppUser(UuidV7.generate(), username, email, encoder.encode(password), displayName);
    u.setStatus("ACTIVE");
    u.setActive(true);
    users.save(u);
    ensurePersonalWorkspace(u);
    audit.append(adminId, "auth.createByAdmin", "app_user", u.getId(), "ALLOWED", null, "{}");
    outbox.emit("app_user", u.getId(), "user.created", "{}");
    return u;
  }

  @Transactional
  public LoginResult login(String usernameOrEmail, String password, String deviceLabel, String clientType) {
    AppUser u = users.findByUsernameIgnoreCase(usernameOrEmail)
        .or(() -> users.findByEmailIgnoreCase(usernameOrEmail))
        .orElseThrow(() -> {
          audit.appendDenied(null, "auth.login", "app_user", null, "BAD_CREDENTIALS");
          return AppException.invalidSession("Bad credentials");
        });
    if (u.getLockedUntil() != null && u.getLockedUntil().isAfter(OffsetDateTime.now(ZoneOffset.UTC))) {
      audit.appendDenied(u.getId(), "auth.login", "app_user", u.getId(), "ACCOUNT_LOCKED");
      throw AppException.accountLocked("Account locked");
    }
    if (u.isLocked()) {
      audit.appendDenied(u.getId(), "auth.login", "app_user", u.getId(), "ACCOUNT_LOCKED");
      throw AppException.accountLocked("Account locked");
    }
    if (!u.isActive() || !"ACTIVE".equals(u.getStatus())) {
      audit.appendDenied(u.getId(), "auth.login", "app_user", u.getId(), "ACCOUNT_NOT_ACTIVE");
      throw AppException.accountNotActive("Account not active");
    }
    if (!encoder.matches(password, u.getPasswordHash())) {
      audit.appendDenied(u.getId(), "auth.login", "app_user", u.getId(), "BAD_CREDENTIALS");
      throw AppException.invalidSession("Bad credentials");
    }
    // Single-session: SELECT ... FOR UPDATE active session, revoke old.
    sessions.findActiveSessionByUserForUpdate(u.getId()).ifPresent(old -> {
      old.setActive(false);
      old.setStatus("REVOKED_LOGIN_ELSEWHERE");
      sessions.save(old);
      pushUnregisterStub(old.getId());
      audit.append(u.getId(), "auth.session.revoked", "user_session", old.getId(),
          "ALLOWED", "REVOKED_LOGIN_ELSEWHERE", "{}");
      // F4: stale realtime streams + push registrations die with the session.
      publish(new SessionRevokedEvent(old.getId(), u.getId(), "REVOKED_LOGIN_ELSEWHERE"));
    });
    // Flush the revoke before inserting the replacement: Hibernate flushes
    // inserts before updates, which would otherwise trip the
    // uq_single_active_session partial unique index.
    sessions.flush();
    String access = opaqueToken();
    String refresh = opaqueToken();
    OffsetDateTime exp = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1);
    UserSession s = new UserSession(UuidV7.generate(), u.getId(), TokenHash.sha256(access), exp);
    s.setDeviceLabel(deviceLabel);
    s.setClientType(clientType);
    sessions.save(s);
    UserRefreshToken rt = new UserRefreshToken(UuidV7.generate(), u.getId(), s.getId(),
        TokenHash.sha256(refresh), OffsetDateTime.now(ZoneOffset.UTC).plusDays(7));
    refreshTokens.save(rt);
    audit.append(u.getId(), "auth.login", "user_session", s.getId(), "ALLOWED", null, "{}");
    outbox.emit("user_session", s.getId(), "security_event.login", "{}");
    boolean isSystemAdmin = grants != null && grants.isSystemAdmin(u.getId());
    return new LoginResult(access, refresh, exp, u.getId(), isSystemAdmin, u.isMustChangePassword());
  }

  protected void pushUnregisterStub(UUID sessionId) {
    // stub: unregister push token for revoked session
  }

  @Transactional
  public void logout(String accessToken) {
    UserSession s = sessions.findBySessionTokenHash(TokenHash.sha256(accessToken))
        .orElseThrow(() -> AppException.invalidSession("Invalid session"));
    s.setActive(false);
    s.setStatus("REVOKED_LOGOUT");
    sessions.save(s);
    // Security: a logout must also burn every refresh token bound to the
    // session. Otherwise a stolen refresh token could mint a new session
    // after the user explicitly signed out.
    for (UserRefreshToken rt : refreshTokens.findBySessionId(s.getId())) {
      if (!rt.isRevoked()) {
        rt.setRevoked(true);
        refreshTokens.save(rt);
      }
    }
    audit.append(s.getUserId(), "auth.logout", "user_session", s.getId(), "ALLOWED", null, "{}");
    // F4: logout kills realtime streams + push registrations of the session.
    publish(new SessionRevokedEvent(s.getId(), s.getUserId(), "REVOKED_LOGOUT"));
  }

  /**
   * Revoke every active session of a user (used when account is locked).
   * Bound refresh tokens are also revoked so they cannot mint new sessions.
   */
  @Transactional
  public void revokeAllSessions(UUID userId) {
    List<UserSession> activeSessions =
        sessions.findByUserIdAndActiveTrue(userId);
    for (UserSession s : activeSessions) {
      s.setActive(false);
      s.setStatus("REVOKED_ADMIN");
      sessions.save(s);
      for (UserRefreshToken rt : refreshTokens.findBySessionId(s.getId())) {
        if (!rt.isRevoked()) {
          rt.setRevoked(true);
          refreshTokens.save(rt);
        }
      }
      audit.append(userId, "auth.session.revoked", "user_session", s.getId(),
          "ALLOWED", "ACCOUNT_LOCKED", "{}");
      publish(new SessionRevokedEvent(s.getId(), userId, "REVOKED_ADMIN"));
    }
  }

  /**
   * F4 EP-006 forced session revoke: the session owner, or a holder of the
   * live {@code SYSTEM_ADMIN} grant (resolved by the caller), revokes any
   * session. Non-owners get 404 (anti-oracle: session existence is not
   * disclosed); revoking a non-ACTIVE session is 409 SESSION_NOT_ACTIVE.
   * Bound refresh tokens are revoked in the same transaction and a
   * {@link SessionRevokedEvent} closes realtime streams + push registrations.
   */
  @Transactional
  public UserSession revokeSession(UUID actorId, UUID sessionId, boolean callerIsSystemAdmin,
      String reason) {
    UserSession s = sessions.findById(sessionId)
        .orElseThrow(() -> AppException.notFound("Session not found"));
    if (!s.getUserId().equals(actorId) && !callerIsSystemAdmin) {
      audit.appendDenied(actorId, "session.revoke", "user_session", sessionId, "NOT_OWNER");
      throw AppException.notFound("Session not found");
    }
    if (!s.isActive() || !"ACTIVE".equals(s.getStatus())) {
      audit.appendDenied(actorId, "session.revoke", "user_session", sessionId,
          "SESSION_NOT_ACTIVE");
      throw AppException.stateConflict("SESSION_NOT_ACTIVE", "Session not active");
    }
    s.setActive(false);
    s.setStatus("REVOKED_ADMIN");
    sessions.save(s);
    for (UserRefreshToken rt : refreshTokens.findBySessionId(sessionId)) {
      if (!rt.isRevoked()) {
        rt.setRevoked(true);
        refreshTokens.save(rt);
      }
    }
    String why = reason == null || reason.isBlank() ? "REVOKED_ADMIN" : reason.trim();
    audit.append(actorId, "session.revoke", "user_session", sessionId, "ALLOWED", why, "{}");
    publish(new SessionRevokedEvent(s.getId(), s.getUserId(), why));
    return s;
  }

  @Transactional
  public LoginResult refresh(String refreshToken) {
    UserRefreshToken rt = refreshTokens.findByTokenHash(TokenHash.sha256(refreshToken))
        .orElseThrow(() -> AppException.invalidSession("Invalid refresh"));
    if (rt.isRevoked() || rt.getExpiresAt().isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
      audit.appendDenied(rt.getUserId(), "auth.refresh", "user_refresh_token", rt.getId(), "REVOKED");
      throw AppException.invalidSession("Refresh revoked");
    }
    AppUser u = users.findById(rt.getUserId()).orElseThrow(() -> AppException.notFound("User gone"));
    if (!u.isActive() || !"ACTIVE".equals(u.getStatus())) {
      audit.appendDenied(u.getId(), "auth.refresh", "user_refresh_token", rt.getId(), "USER_INACTIVE");
      throw AppException.invalidSession("User not active");
    }
    // Refresh is bound to the session that issued it. Logout, admin revoke or
    // a login elsewhere deactivates that session; the refresh token must die
    // with it instead of silently minting a new session.
    if (rt.getSessionId() != null) {
      UserSession bound = sessions.findById(rt.getSessionId()).orElse(null);
      if (bound == null || !bound.isActive() || !"ACTIVE".equals(bound.getStatus())) {
        audit.appendDenied(u.getId(), "auth.refresh", "user_refresh_token", rt.getId(),
            "SESSION_REVOKED");
        throw AppException.invalidSession("Refresh session revoked");
      }
    }
    rt.setRevoked(true);
    refreshTokens.save(rt);
    final UUID rotatedUserId = u.getId();
    final java.util.concurrent.atomic.AtomicReference<UUID> revokedOld = new java.util.concurrent.atomic.AtomicReference<>();
    sessions.findActiveSessionByUserForUpdate(u.getId()).ifPresent(old -> {
      revokedOld.set(old.getId());
      old.setActive(false);
      old.setStatus("REVOKED_LOGIN_ELSEWHERE");
      sessions.save(old);
    });
    if (revokedOld.get() != null) {
      // F4: rotated-out sessions lose realtime streams + push registrations.
      publish(new SessionRevokedEvent(revokedOld.get(), rotatedUserId, "REVOKED_REFRESH"));
    }
    // Flush the revoke before inserting the rotated session (same
    // insert-before-update flush ordering hazard as login, see above).
    sessions.flush();
    String access = opaqueToken();
    String refresh2 = opaqueToken();
    OffsetDateTime exp = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1);
    UserSession s = new UserSession(UuidV7.generate(), u.getId(), TokenHash.sha256(access), exp);
    sessions.save(s);
    UserRefreshToken rt2 = new UserRefreshToken(UuidV7.generate(), u.getId(), s.getId(),
        TokenHash.sha256(refresh2), OffsetDateTime.now(ZoneOffset.UTC).plusDays(7));
    rt2.setRotatedFromId(rt.getId());
    refreshTokens.save(rt2);
    audit.append(u.getId(), "auth.refresh", "user_session", s.getId(), "ALLOWED", null, "{}");
    boolean isSystemAdmin = grants != null && grants.isSystemAdmin(u.getId());
    return new LoginResult(access, refresh2, exp, u.getId(), isSystemAdmin, u.isMustChangePassword());
  }

  @Transactional
  public void changePassword(UUID userId, String oldPassword, String newPassword) {
    AppUser u = users.findById(userId).orElseThrow(() -> AppException.notFound("User not found"));
    if (!encoder.matches(oldPassword, u.getPasswordHash())) {
      audit.appendDenied(userId, "auth.password.change", "app_user", userId, "BAD_PASSWORD");
      throw AppException.forbidden("Bad password");
    }
    u.setPasswordHash(encoder.encode(newPassword));
    u.setMustChangePassword(false);
    users.save(u);
    audit.append(userId, "auth.password.change", "app_user", userId, "ALLOWED", null, "{}");
  }

  @Transactional
  public String resetPasswordTemp(UUID adminId, UUID targetId) {
    AppUser u = users.findById(targetId).orElseThrow(() -> AppException.notFound("User not found"));
    byte[] b = new byte[12];
    random.nextBytes(b);
    String temp = HexFormat.of().formatHex(b);
    u.setPasswordHash(encoder.encode(temp));
    u.setMustChangePassword(true);
    users.save(u);
    RecoveryCode rc = new RecoveryCode(UuidV7.generate(), targetId, TokenHash.sha256(temp),
        OffsetDateTime.now(ZoneOffset.UTC).plusHours(24));
    recoveryCodes.save(rc);
    audit.append(adminId, "auth.password.reset", "app_user", targetId, "ALLOWED", "TEMP_24H", "{}");
    return temp;
  }

  @Transactional
  public void consumeRecoveryCode(UUID userId, String code, String newPassword) {
    List<RecoveryCode> codes = recoveryCodes.findByUserIdAndUsedFalse(userId);
    RecoveryCode match = codes.stream()
        .filter(c -> c.getCodeHash().equals(TokenHash.sha256(code)))
        .findFirst()
        .orElseThrow(() -> AppException.invalidSession("Bad recovery code"));
    match.setUsed(true); // one-time
    recoveryCodes.save(match);
    AppUser u = users.findById(userId).orElseThrow(() -> AppException.notFound("User not found"));
    u.setPasswordHash(encoder.encode(newPassword));
    u.setMustChangePassword(false);
    users.save(u);
    audit.append(userId, "auth.recovery.consume", "app_user", userId, "ALLOWED", null, "{}");
  }

  @Transactional
  public ReauthTicket issueReauthTicket(UUID userId, String action, UUID targetId) {
    ReauthTicket t = new ReauthTicket(UuidV7.generate(), userId, action, targetId,
        OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5));
    return reauthTickets.save(t);
  }

  /**
   * §7 EP-008 verify: password must match BEFORE the ticket is consumed
   * (wrong password -> 401, ticket stays live). Then the ticket rules apply:
   * single-use, 5-minute expiry, bound to (user, action, target).
   */
  @Transactional
  public void verifyReauthTicket(UUID userId, UUID ticketId, String password, String action,
      UUID targetId) {
    AppUser u = users.findById(userId).orElseThrow(() -> AppException.notFound("User not found"));
    if (password == null || !encoder.matches(password, u.getPasswordHash())) {
      audit.appendDenied(userId, action == null ? "reauth.verify" : action,
          "reauth_ticket", ticketId, "BAD_PASSWORD");
      throw AppException.invalidSession("Bad credentials");
    }
    consumeReauthTicket(ticketId, userId, action, targetId);
    audit.append(userId, "auth.reauth.verify", "reauth_ticket", ticketId, "ALLOWED", null, "{}");
  }

  @Transactional
  public void consumeReauthTicket(UUID ticketId, UUID userId, String action, UUID targetId) {
    ReauthTicket t = reauthTickets.findById(ticketId)
        .orElseThrow(() -> AppException.reauthRequired("Missing reauth ticket"));
    if (t.isUsed() || t.getExpiresAt().isBefore(OffsetDateTime.now(ZoneOffset.UTC))
        || !t.getUserId().equals(userId) || !t.getAction().equals(action)
        || (targetId != null && !targetId.equals(t.getTargetId()))) {
      audit.appendDenied(userId, action, "reauth_ticket", ticketId, "REAUTH_INVALID");
      throw AppException.reauthRequired("Invalid/expired reauth ticket");
    }
    t.setUsed(true); // single-use
    reauthTickets.save(t);
  }
}
