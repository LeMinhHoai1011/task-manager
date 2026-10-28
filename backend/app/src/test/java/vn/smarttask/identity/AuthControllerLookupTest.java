package vn.smarttask.identity;

import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.smarttask.common.AppException;
import vn.smarttask.common.UuidV7;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.identity.entity.AppUser;
import vn.smarttask.identity.repo.AppUserRepository;
import vn.smarttask.identity.service.IdentityService;
import vn.smarttask.security.CurrentActor;
import vn.smarttask.security.GrantService;
import vn.smarttask.security.Principal;

/**
 * Unit tests for AuthController.lookup (offline, Mockito).
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerLookupTest {

  @Mock IdentityService identity;
  @Mock GrantService grants;
  @Mock AppUserRepository users;
  @Mock AuditService audit;

  private AuthController controller;

  @BeforeEach
  void setUp() {
    controller = new AuthController(identity, grants, users, audit);
    // Set authenticated actor
    UUID actor = UUID.randomUUID();
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(
            new Principal(actor, UuidV7.generate()), null, java.util.List.of()));
  }

  @Test
  void lookupActiveUserSucceeds() {
    UUID id = UUID.randomUUID();
    AppUser u = new AppUser(id, "alice", "alice@example.com", "hash", "Alice");
    u.setActive(true);
    u.setStatus("ACTIVE");
    when(users.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(u));

    var res = controller.lookup("alice");

    assertEquals(200, res.getStatusCode().value());
    var body = res.getBody();
    assertNotNull(body);
    assertEquals(id, body.data().id());
    assertEquals("alice", body.data().username());
    assertEquals("Alice", body.data().displayName());
    verify(audit).append(eq(actorId()), eq("auth.lookup"), eq("app_user"), eq(id),
        eq("ALLOWED"), isNull(), any());
  }

  @Test
  void lookupPendingUserReturns404() {
    AppUser u = new AppUser(UUID.randomUUID(), "bob", "bob@example.com", "hash", "Bob");
    u.setActive(false);
    u.setStatus("PENDING");
    when(users.findByUsernameIgnoreCase("bob")).thenReturn(Optional.of(u));

    AppException ex = assertThrows(AppException.class, () -> controller.lookup("bob"));
    assertEquals(404, ex.getHttpStatus().value());
  }

  @Test
  void lookupMissingUsernameReturns400() {
    AppException ex = assertThrows(AppException.class, () -> controller.lookup(""));
    assertEquals(400, ex.getHttpStatus().value());
    assertEquals("BAD_REQUEST", ex.getCode());
  }

  @Test
  void lookupUnknownUserReturns404() {
    when(users.findByUsernameIgnoreCase("nobody")).thenReturn(Optional.empty());
    AppException ex = assertThrows(AppException.class, () -> controller.lookup("nobody"));
    assertEquals(404, ex.getHttpStatus().value());
  }

  private UUID actorId() {
    return ((Principal) SecurityContextHolder.getContext()
        .getAuthentication().getPrincipal()).userId();
  }
}
