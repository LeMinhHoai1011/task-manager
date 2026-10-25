package vn.smarttask.identity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import vn.smarttask.common.AppException;
import vn.smarttask.common.UuidV7;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.common.service.OutboxService;
import vn.smarttask.identity.entity.ReauthTicket;
import vn.smarttask.identity.repo.AppUserRepository;
import vn.smarttask.identity.repo.ReauthTicketRepository;
import vn.smarttask.identity.repo.RecoveryCodeRepository;
import vn.smarttask.identity.repo.UserRefreshTokenRepository;
import vn.smarttask.identity.repo.UserSessionRepository;
import vn.smarttask.identity.service.IdentityService;
import vn.smarttask.workspace.repo.WorkspaceMembershipRepository;
import vn.smarttask.workspace.repo.WorkspaceRepository;

/**
 * F0 reauth binding (offline, Mockito): a ticket is single-use, 5-minute,
 * and bound to (userId, action, targetId). Mismatches fail closed with 428.
 */
@ExtendWith(MockitoExtension.class)
class ReauthBindingTest {

  @Mock AppUserRepository users;
  @Mock UserSessionRepository sessions;
  @Mock UserRefreshTokenRepository refreshTokens;
  @Mock ReauthTicketRepository reauthTickets;
  @Mock RecoveryCodeRepository recoveryCodes;
  @Mock WorkspaceRepository workspaces;
  @Mock WorkspaceMembershipRepository wsMembers;
  @Mock AuditService audit;
  @Mock OutboxService outbox;

  private IdentityService identity() {
    return new IdentityService(users, sessions, refreshTokens, reauthTickets, recoveryCodes,
        workspaces, wsMembers, audit, outbox, true);
  }

  private ReauthTicket ticket(UUID user, String action, UUID target) {
    return new ReauthTicket(UuidV7.generate(), user, action, target,
        OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5));
  }

  @Test
  void wrongUserIsRejected() {
    UUID owner = UUID.randomUUID(), other = UUID.randomUUID(), tid = UuidV7.generate();
    ReauthTicket t = ticket(owner, "ai.config.update", null);
    when(reauthTickets.findById(tid)).thenReturn(Optional.of(t));

    AppException ex = assertThrows(AppException.class,
        () -> identity().consumeReauthTicket(tid, other, "ai.config.update", null));
    assertEquals(428, ex.getHttpStatus().value());
    assertEquals("REAUTH_REQUIRED", ex.getCode());
    verify(audit).appendDenied(other, "ai.config.update", "reauth_ticket", tid, "REAUTH_INVALID");
    assertFalse(t.isUsed(), "failed consume must not burn the ticket");
  }

  @Test
  void wrongActionIsRejected() {
    UUID user = UUID.randomUUID(), tid = UuidV7.generate();
    when(reauthTickets.findById(tid))
        .thenReturn(Optional.of(ticket(user, "ai.config.update", null)));

    AppException ex = assertThrows(AppException.class,
        () -> identity().consumeReauthTicket(tid, user, "workspace.owner.transfer", null));
    assertEquals(428, ex.getHttpStatus().value());
  }

  @Test
  void wrongTargetIsRejected() {
    UUID user = UUID.randomUUID(), tid = UuidV7.generate();
    when(reauthTickets.findById(tid))
        .thenReturn(Optional.of(ticket(user, "workspace.owner.transfer", UUID.randomUUID())));

    AppException ex = assertThrows(AppException.class,
        () -> identity().consumeReauthTicket(tid, user, "workspace.owner.transfer",
            UUID.randomUUID()));
    assertEquals(428, ex.getHttpStatus().value());
  }

  @Test
  void reuseIsRejected() {
    UUID user = UUID.randomUUID(), tid = UuidV7.generate();
    ReauthTicket t = ticket(user, "ai.config.update", null);
    t.setUsed(true);
    when(reauthTickets.findById(tid)).thenReturn(Optional.of(t));

    assertThrows(AppException.class,
        () -> identity().consumeReauthTicket(tid, user, "ai.config.update", null));
  }

  @Test
  void expiredIsRejected() {
    UUID user = UUID.randomUUID(), tid = UuidV7.generate();
    ReauthTicket t = new ReauthTicket(tid, user, "ai.config.update", null,
        OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
    when(reauthTickets.findById(tid)).thenReturn(Optional.of(t));

    assertThrows(AppException.class,
        () -> identity().consumeReauthTicket(tid, user, "ai.config.update", null));
  }

  @Test
  void missingTicketIsRejected() {
    UUID tid = UuidV7.generate();
    when(reauthTickets.findById(tid)).thenReturn(Optional.empty());
    assertThrows(AppException.class,
        () -> identity().consumeReauthTicket(tid, UUID.randomUUID(), "ai.config.update", null));
  }

  @Test
  void correctBindingConsumesOnce() {
    UUID user = UUID.randomUUID(), tid = UuidV7.generate(), target = UUID.randomUUID();
    ReauthTicket t = ticket(user, "workspace.owner.transfer", target);
    when(reauthTickets.findById(tid)).thenReturn(Optional.of(t));

    assertDoesNotThrow(
        () -> identity().consumeReauthTicket(tid, user, "workspace.owner.transfer", target));
    assertTrue(t.isUsed());
    verify(reauthTickets).save(t);
  }
}
