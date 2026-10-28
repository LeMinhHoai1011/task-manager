package vn.smarttask.identity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.smarttask.common.AppException;
import vn.smarttask.common.UuidV7;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.identity.entity.ReauthTicket;
import vn.smarttask.identity.repo.AppUserRepository;
import vn.smarttask.identity.service.IdentityService;
import vn.smarttask.security.GrantService;
import vn.smarttask.security.Principal;

/**
 * F0 AuthController binding (offline, Mockito): reauth/approve/createByAdmin
 * use the SecurityContext principal; approve grant resolves via GrantService
 * (deny 403 without a real grant - never a client header).
 */
@ExtendWith(MockitoExtension.class)
class AuthPrincipalBindingTest {

  @Mock IdentityService identity;
  @Mock AuditService audit;
  @Mock AppUserRepository users;

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private void loginAs(UUID user) {
    var auth = new UsernamePasswordAuthenticationToken(
        new Principal(user, UuidV7.generate()), null, java.util.List.of());
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  @Test
  void reauthUsesPrincipalNotHeader() {
    UUID user = UUID.randomUUID();
    loginAs(user);
    ReauthTicket t = new ReauthTicket(UuidV7.generate(), user, "sensitive", null,
        OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5));
    when(identity.issueReauthTicket(eq(user), eq("sensitive"), any())).thenReturn(t);

    var res = new AuthController(identity, new GrantService(audit), users)
        .reauth(Map.of("action", "sensitive"));
    assertEquals(200, res.getStatusCode().value());
    verify(identity).issueReauthTicket(eq(user), eq("sensitive"), any());
  }

  @Test
  void reauthWithoutAuthenticationIs401() {
    assertThrows(AppException.class, () -> new AuthController(identity, new GrantService(audit), users)
        .reauth(Map.of("action", "sensitive")));
  }

  @Test
  void approveWithoutGrantIs403() {
    UUID approver = UUID.randomUUID(), target = UUID.randomUUID();
    loginAs(approver);

    AppException ex = assertThrows(AppException.class,
        () -> new AuthController(identity, new GrantService(audit), users)
            .approve(new AuthController.ApproveRequest(target, true)));
    assertEquals(403, ex.getHttpStatus().value());
    assertEquals("FORBIDDEN", ex.getCode());
    verify(audit).appendDenied(eq(approver), eq("auth.approve"), eq("app_user"), eq(target),
        eq("MISSING_GRANT:ACCOUNT_APPROVE_GLOBAL"));
  }

  @Test
  void createByAdminWithoutGrantIs403() {
    UUID admin = UUID.randomUUID();
    loginAs(admin);

    AppException ex = assertThrows(AppException.class,
        () -> new AuthController(identity, new GrantService(audit), users)
            .createByAdmin(new AuthController.CreateUserRequest("u", "u@x.io", "pw", "U")));
    assertEquals(403, ex.getHttpStatus().value());
  }
}
