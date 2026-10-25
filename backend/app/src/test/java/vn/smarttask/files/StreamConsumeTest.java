package vn.smarttask.files;

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
import org.springframework.http.ResponseEntity;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.common.service.TokenHash;
import vn.smarttask.files.entity.Attachment;
import vn.smarttask.files.entity.FileVersion;
import vn.smarttask.files.entity.StreamTicket;
import vn.smarttask.files.repo.AttachmentRepository;
import vn.smarttask.files.repo.FileAccessEventRepository;
import vn.smarttask.files.repo.FileVersionRepository;
import vn.smarttask.files.repo.StreamTicketRepository;
import vn.smarttask.files.repo.UploadSessionRepository;
import vn.smarttask.files.service.FileContentService;
import vn.smarttask.files.service.FileService;
import vn.smarttask.files.service.StreamConsumeService;
import vn.smarttask.identity.entity.AppUser;
import vn.smarttask.identity.entity.UserSession;
import vn.smarttask.identity.repo.AppUserRepository;
import vn.smarttask.identity.repo.UserSessionRepository;
import vn.smarttask.project.entity.ProjectMembership;
import vn.smarttask.project.repo.ProjectMembershipRepository;
import vn.smarttask.security.PolicyEnforcer;
import vn.smarttask.task.entity.Task;
import vn.smarttask.task.repo.TaskRepository;

/**
 * GATE FILE-01 (offline unit, F1.6): revoked/suspended ticket actor consumes
 * the ticket and hides as 404; live actor streams real bytes once; consume
 * uses the locked row read. Concurrency across threads needs a real DB
 * (NOT_TESTED here — see disabled IT).
 *
 * <p>F1.6: consume runs in {@link StreamConsumeService} (short tx), bytes
 * come from {@link FileContentService} (mocked here), access events are
 * written outside the transaction.
 */
@ExtendWith(MockitoExtension.class)
class StreamConsumeTest {

  @Mock FileService files;
  @Mock AttachmentRepository attachments;
  @Mock TaskRepository tasks;
  @Mock ProjectMembershipRepository projectMembers;
  @Mock FileAccessEventRepository accessEvents;
  @Mock StreamTicketRepository tickets;
  @Mock FileVersionRepository versions;
  @Mock UploadSessionRepository uploadSessions;
  @Mock AuditService audit;
  @Mock PolicyEnforcer policy;
  @Mock UserSessionRepository sessions;
  @Mock AppUserRepository users;
  @Mock FileContentService content;

  private StreamConsumeService consume;
  private FileController controller;
  private final UUID actor = UUID.randomUUID();
  private final UUID attId = UUID.randomUUID();
  private final UUID taskId = UUID.randomUUID();
  private final UUID projectId = UUID.randomUUID();
  private static final String RAW = "raw-stream-ticket";
  private static final byte[] BYTES = "fake-pdf-bytes".getBytes();

  @BeforeEach
  void setUp() {
    consume = new StreamConsumeService(tickets, attachments, versions, tasks, projectMembers,
        sessions, users);
    controller = new FileController(files, attachments, tasks, projectMembers, accessEvents,
        tickets, uploadSessions, audit, policy, sessions, users, consume, content);
  }

  private StreamTicket liveTicket() {
    return new StreamTicket(UUID.randomUUID(), attId, TokenHash.sha256(RAW), actor,
        OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(30));
  }

  private void stubAttachmentChain() {
    when(attachments.findById(attId)).thenReturn(Optional.of(
        new Attachment(attId, taskId, "f.pdf", "application/pdf", 10L, actor)));
    when(tasks.findById(taskId)).thenReturn(
        Optional.of(new Task(taskId, projectId, "t", actor)));
    when(projectMembers.findMembership(projectId, actor)).thenReturn(
        Optional.of(new ProjectMembership(UUID.randomUUID(), projectId, actor, "MEMBER")));
    FileVersion v = new FileVersion(UUID.randomUUID(), attId, 1, "att/" + attId + "/v1", 10L);
    v.setMimeType("application/pdf");
    when(versions.findByAttachmentIdOrderByVersionNoAsc(attId)).thenReturn(List.of(v));
  }

  private void stubLiveActor() {
    AppUser u = new AppUser(actor, "u", "u@x.io", "hash", "U");
    u.setActive(true);
    u.setStatus("ACTIVE");
    u.setLocked(false);
    when(users.findById(actor)).thenReturn(Optional.of(u));
    UserSession s = new UserSession(UUID.randomUUID(), actor, "h",
        OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));
    s.setActive(true);
    s.setStatus("ACTIVE");
    when(sessions.findActiveSessionByUserForUpdate(actor)).thenReturn(Optional.of(s));
  }

  @Test
  void revokedSessionConsumesTicketAndHides404() {
    StreamTicket t = liveTicket();
    when(tickets.findByTicketHashForUpdate(TokenHash.sha256(RAW))).thenReturn(Optional.of(t));
    stubLiveActor();
    when(sessions.findActiveSessionByUserForUpdate(actor)).thenReturn(Optional.empty());

    ResponseEntity<byte[]> res = controller.stream(RAW);

    assertEquals(404, res.getStatusCode().value());
    assertTrue(t.isUsed(), "revoked ticket must be consumed");
    verify(audit).appendDenied(actor, "file.stream", "attachment", attId, "ACTOR_REVOKED");
  }

  @Test
  void suspendedActorConsumesTicketAndHides404() {
    StreamTicket t = liveTicket();
    when(tickets.findByTicketHashForUpdate(TokenHash.sha256(RAW))).thenReturn(Optional.of(t));
    AppUser u = new AppUser(actor, "u", "u@x.io", "hash", "U");
    u.setActive(false);
    u.setStatus("SUSPENDED");
    when(users.findById(actor)).thenReturn(Optional.of(u));

    ResponseEntity<byte[]> res = controller.stream(RAW);

    assertEquals(404, res.getStatusCode().value());
    assertTrue(t.isUsed());
  }

  @Test
  void liveActorStreamsRealBytesOnce() {
    StreamTicket t = liveTicket();
    when(tickets.findByTicketHashForUpdate(TokenHash.sha256(RAW))).thenReturn(Optional.of(t));
    stubLiveActor();
    stubAttachmentChain();
    when(content.read(any(), any(), any()))
        .thenReturn(new FileContentService.Content(BYTES, "application/pdf"));

    ResponseEntity<byte[]> res = controller.stream(RAW);

    assertEquals(200, res.getStatusCode().value());
    assertTrue(t.isUsed(), "ticket must be single-use");
    assertArrayEquals(BYTES, res.getBody());
    assertEquals("application/pdf", res.getHeaders().getContentType().toString());
    verify(accessEvents).save(any());
  }

  @Test
  void missingObjectIs409() {
    StreamTicket t = liveTicket();
    when(tickets.findByTicketHashForUpdate(TokenHash.sha256(RAW))).thenReturn(Optional.of(t));
    stubLiveActor();
    when(attachments.findById(attId)).thenReturn(Optional.of(
        new Attachment(attId, taskId, "f.pdf", "application/pdf", 10L, actor)));
    when(tasks.findById(taskId)).thenReturn(
        Optional.of(new Task(taskId, projectId, "t", actor)));
    when(projectMembers.findMembership(projectId, actor)).thenReturn(
        Optional.of(new ProjectMembership(UUID.randomUUID(), projectId, actor, "MEMBER")));
    when(versions.findByAttachmentIdOrderByVersionNoAsc(attId)).thenReturn(List.of());

    var ex = assertThrows(vn.smarttask.common.AppException.class,
        () -> controller.stream(RAW));
    assertEquals("FILE_OBJECT_MISSING", ex.getReasonCode());
    assertTrue(t.isUsed());
  }

  @Test
  void consumeUsesLockedRowRead() {
    StreamTicket t = liveTicket();
    when(tickets.findByTicketHashForUpdate(TokenHash.sha256(RAW))).thenReturn(Optional.of(t));
    stubLiveActor();
    stubAttachmentChain();
    when(content.read(any(), any(), any()))
        .thenReturn(new FileContentService.Content(BYTES, "application/pdf"));

    controller.stream(RAW);

    verify(tickets).findByTicketHashForUpdate(TokenHash.sha256(RAW));
  }

  @Test
  void reusedTicketIs410() {
    StreamTicket t = liveTicket();
    t.setUsed(true);
    when(tickets.findByTicketHashForUpdate(TokenHash.sha256(RAW))).thenReturn(Optional.of(t));

    ResponseEntity<byte[]> res = controller.stream(RAW);

    assertEquals(410, res.getStatusCode().value());
  }
}
