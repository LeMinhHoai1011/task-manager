package vn.smarttask.files.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.smarttask.common.service.TokenHash;
import vn.smarttask.files.entity.StreamTicket;
import vn.smarttask.files.repo.AttachmentRepository;
import vn.smarttask.files.repo.FileVersionRepository;
import vn.smarttask.files.repo.StreamTicketRepository;
import vn.smarttask.identity.repo.AppUserRepository;
import vn.smarttask.identity.repo.UserSessionRepository;
import vn.smarttask.project.repo.ProjectMembershipRepository;
import vn.smarttask.task.repo.TaskRepository;

/**
 * F1.6 short-transaction ticket consume. The ticket row is locked
 * (PESSIMISTIC_WRITE) and, in ONE short transaction: TTL/single-use is
 * validated, the ticket actor's account liveness (ACTIVE, unlocked, live
 * session) is rechecked, an optional session principal is matched
 * (anti-swapping), and project participation is rechecked (revoke-safe).
 * The ticket is then marked used and the transaction commits — byte
 * transfer and {@code FileAccessEvent} writes happen OUTSIDE this
 * transaction (caller responsibility).
 */
@Service
public class StreamConsumeService {

  public enum Outcome { OK, EXPIRED, REVOKED, PRINCIPAL_MISMATCH, NOT_FOUND, OBJECT_MISSING }

  public record Consumed(Outcome outcome, UUID attachmentId, UUID actorUserId,
      String objectKey, String bucket, String contentType, String reason) {}

  private final StreamTicketRepository tickets;
  private final AttachmentRepository attachments;
  private final FileVersionRepository versions;
  private final TaskRepository tasks;
  private final ProjectMembershipRepository projectMembers;
  private final UserSessionRepository sessions;
  private final AppUserRepository users;

  public StreamConsumeService(StreamTicketRepository tickets,
      AttachmentRepository attachments, FileVersionRepository versions,
      TaskRepository tasks, ProjectMembershipRepository projectMembers,
      UserSessionRepository sessions, AppUserRepository users) {
    this.tickets = tickets;
    this.attachments = attachments;
    this.versions = versions;
    this.tasks = tasks;
    this.projectMembers = projectMembers;
    this.sessions = sessions;
    this.users = users;
  }

  @Transactional
  public Consumed consume(String rawTicket, UUID callerUserIdOrNull) {
    String hash = TokenHash.sha256(rawTicket);
    var opt = tickets.findByTicketHashForUpdate(hash);
    if (opt.isEmpty() || opt.get().isUsed()
        || OffsetDateTime.now(ZoneOffset.UTC).isAfter(opt.get().getExpiresAt())) {
      return new Consumed(Outcome.EXPIRED, null, null, null, null, null, "STREAM_TICKET_EXPIRED");
    }
    StreamTicket t = opt.get();
    if (!isTicketActorLive(t.getActorUserId())) {
      t.setUsed(true);
      tickets.save(t);
      return new Consumed(Outcome.REVOKED, t.getAttachmentId(), t.getActorUserId(),
          null, null, null, "ACTOR_REVOKED");
    }
    if (callerUserIdOrNull != null && !callerUserIdOrNull.equals(t.getActorUserId())) {
      return new Consumed(Outcome.PRINCIPAL_MISMATCH, t.getAttachmentId(), t.getActorUserId(),
          null, null, null, "PRINCIPAL_MISMATCH");
    }
    var att = attachments.findById(t.getAttachmentId());
    boolean hasRead = false;
    UUID projectId = null;
    if (att.isPresent() && att.get().getDeletedAt() == null) {
      var task = tasks.findById(att.get().getTaskId());
      if (task.isPresent() && task.get().getDeletedAt() == null) {
        projectId = task.get().getProjectId();
        hasRead = projectMembers.findMembership(projectId, t.getActorUserId()).isPresent();
      }
    }
    if (!hasRead) {
      t.setUsed(true);
      tickets.save(t);
      return new Consumed(Outcome.REVOKED, t.getAttachmentId(), t.getActorUserId(),
          null, null, null, "REVOKED");
    }
    var vers = versions.findByAttachmentIdOrderByVersionNoAsc(t.getAttachmentId());
    if (vers.isEmpty()) {
      t.setUsed(true);
      tickets.save(t);
      return new Consumed(Outcome.OBJECT_MISSING, t.getAttachmentId(), t.getActorUserId(),
          null, null, null, "FILE_OBJECT_MISSING");
    }
    var current = vers.get(vers.size() - 1);
    t.setUsed(true);
    if (t.getFileVersionId() == null) t.setFileVersionId(current.getId());
    tickets.save(t);
    return new Consumed(Outcome.OK, t.getAttachmentId(), t.getActorUserId(),
        current.getObjectKey(), null, current.getMimeType(), null);
  }

  private boolean isTicketActorLive(UUID actorUserId) {
    if (actorUserId == null) return false;
    var u = users.findById(actorUserId);
    if (u.isEmpty() || !u.get().isActive() || !"ACTIVE".equals(u.get().getStatus())) return false;
    if (u.get().isLocked()) return false;
    OffsetDateTime lockedUntil = u.get().getLockedUntil();
    if (lockedUntil != null && lockedUntil.isAfter(OffsetDateTime.now(ZoneOffset.UTC))) return false;
    var s = sessions.findActiveSessionByUserForUpdate(actorUserId);
    return s.isPresent() && OffsetDateTime.now(ZoneOffset.UTC).isBefore(s.get().getExpiresAt());
  }
}
