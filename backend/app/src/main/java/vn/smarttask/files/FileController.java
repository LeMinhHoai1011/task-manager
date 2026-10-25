package vn.smarttask.files;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.smarttask.common.ApiResult;
import vn.smarttask.common.AppException;
import vn.smarttask.common.UuidV7;
import vn.smarttask.files.entity.FileAccessEvent;
import vn.smarttask.files.entity.StreamTicket;
import vn.smarttask.files.repo.AttachmentRepository;
import vn.smarttask.files.repo.FileAccessEventRepository;
import vn.smarttask.files.repo.UploadSessionRepository;
import vn.smarttask.files.service.FileContentService;
import vn.smarttask.files.service.FileService;
import vn.smarttask.files.service.StreamConsumeService;
import vn.smarttask.identity.repo.AppUserRepository;
import vn.smarttask.identity.repo.UserSessionRepository;
import vn.smarttask.project.repo.ProjectMembershipRepository;
import vn.smarttask.security.CurrentActor;
import vn.smarttask.security.PolicyEnforcer;
import vn.smarttask.security.Principal;
import vn.smarttask.security.RequiresPermission;
import vn.smarttask.task.repo.TaskRepository;

/**
 * PH-3 file gateway: backend-only streaming (no direct object-storage URLs).
 * Preview/download re-check auth per request; StreamTicket single-use TTL 30s.
 * F0: actor is the authenticated principal; controller-entry policy enforced.
 */
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

  public record StreamTicketDto(String ticket, String expiresAt) {}

  private final FileService files;
  private final AttachmentRepository attachments;
  private final TaskRepository tasks;
  private final ProjectMembershipRepository projectMembers;
  private final FileAccessEventRepository accessEvents;
  private final vn.smarttask.files.repo.StreamTicketRepository tickets;
  private final UploadSessionRepository uploadSessions;
  private final vn.smarttask.common.service.AuditService audit;
  private final PolicyEnforcer policy;
  private final UserSessionRepository sessions;
  private final AppUserRepository users;
  private final StreamConsumeService consume;
  private final FileContentService content;

  public FileController(FileService files, AttachmentRepository attachments, TaskRepository tasks,
      ProjectMembershipRepository projectMembers, FileAccessEventRepository accessEvents,
      vn.smarttask.files.repo.StreamTicketRepository tickets,
      UploadSessionRepository uploadSessions,
      vn.smarttask.common.service.AuditService audit, PolicyEnforcer policy,
      UserSessionRepository sessions, AppUserRepository users,
      StreamConsumeService consume, FileContentService content) {
    this.files = files; this.attachments = attachments; this.tasks = tasks;
    this.projectMembers = projectMembers; this.accessEvents = accessEvents;
    this.tickets = tickets; this.uploadSessions = uploadSessions;
    this.audit = audit; this.policy = policy;
    this.sessions = sessions; this.users = users;
    this.consume = consume; this.content = content;
  }

  private static void requireIdem(String k) {
    if (k == null || k.isBlank()) throw AppException.badRequest("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key required");
  }

  /**
   * Returns projectId or throws 404 (hide existence when no participation).
   * Delegates to PolicyEnforcer; keeps the FileAccessEvent DENIED row.
   */
  private UUID requireRead(UUID taskId, UUID actor, String action) {
    try {
      policy.requireTaskRead(actor, taskId);
    } catch (AppException ex) {
      accessEvents.save(new FileAccessEvent(UuidV7.generate(), null, actor, action, "DENIED", "NO_READ_OR_MISSING"));
      audit.appendDenied(actor, action, "task", taskId, "NO_READ_OR_MISSING");
      throw ex;
    }
    var t = tasks.findById(taskId).orElseThrow(() -> AppException.notFound("Task not found"));
    return t.getProjectId();
  }

  /** Resolve an upload session to its task and enforce task write. */
  private UUID requireUploadSessionWrite(UUID sessionId, UUID actor, String action) {
    var s = uploadSessions.findById(sessionId)
        .orElseThrow(() -> AppException.notFound("Upload session not found"));
    UUID taskId = s.getTaskId();
    if (taskId == null && s.getAttachmentId() != null) {
      taskId = attachments.findById(s.getAttachmentId()).map(a -> a.getTaskId()).orElse(null);
    }
    if (taskId == null) {
      throw AppException.notFound("Upload session not found");
    }
    policy.requireTaskWrite(actor, taskId, action, "file:write");
    return taskId;
  }

  /** Resolve an attachment to its task and enforce task write. */
  private void requireAttachmentWrite(UUID attachmentId, UUID actor, String action) {
    var att = attachments.findById(attachmentId)
        .orElseThrow(() -> AppException.notFound("Attachment not found"));
    if (att.getDeletedAt() != null) throw AppException.notFound("Attachment not found");
    policy.requireTaskWrite(actor, att.getTaskId(), action, "file:write");
  }

  @PostMapping("/upload")
  @RequiresPermission("file:write")
  public ResponseEntity<ApiResult<Object>> initUpload(@RequestBody Map<String, Object> body,
      @RequestHeader(value = "Idempotency-Key", required = false) String idem) {
    requireIdem(idem);
    UUID actor = CurrentActor.userId();
    UUID taskId = UUID.fromString((String) body.get("task_id"));
    policy.requireTaskWrite(actor, taskId, "file.upload.init", "file:write");
    var s = files.initUpload(actor, taskId, (String) body.get("file_name"),
        (String) body.get("mime_type"), ((Number) body.get("declared_size")).longValue(), idem);
    return ResponseEntity.ok(ApiResult.ok(
        Map.of("upload_session_id", s.getId().toString(), "staging_key", s.getStagingKey()),
        "file.upload.init.success"));
  }

  /**
   * Web upload path: browser -> authenticated API -> private MinIO staging.
   * The bytes are validated against the session's declared size before they
   * are written, so a caller cannot smuggle a different-sized object.
   */
  @PostMapping(value = "/upload/{sessionId}/content", consumes = "multipart/form-data")
  @RequiresPermission("file:write")
  public ResponseEntity<ApiResult<Object>> stageContent(@PathVariable UUID sessionId,
      @RequestParam("file") MultipartFile file,
      @RequestHeader(value = "Idempotency-Key", required = false) String idem)
      throws IOException {
    requireIdem(idem);
    UUID actor = CurrentActor.userId();
    requireUploadSessionWrite(sessionId, actor, "file.upload.content");
    if (file == null || file.isEmpty()) {
      throw AppException.badRequest("EMPTY_FILE", "File required");
    }
    var s = files.stageBytes(actor, sessionId, file.getBytes());
    return ResponseEntity.ok(ApiResult.ok(
        Map.of("status", s.getStatus(), "bytes", file.getSize()),
        "file.upload.content.success"));
  }

  @PostMapping("/upload/{sessionId}/complete")
  @RequiresPermission("file:write")
  public ResponseEntity<ApiResult<Object>> stageComplete(@PathVariable UUID sessionId,
      @RequestBody Map<String, Object> body,
      @RequestHeader(value = "Idempotency-Key", required = false) String idem) {
    requireIdem(idem);
    UUID actor = CurrentActor.userId();
    requireUploadSessionWrite(sessionId, actor, "file.upload.staged");
    var s = files.stageComplete(actor, sessionId,
        ((Number) body.get("actual_bytes")).longValue(), (String) body.get("checksum_sha256"));
    return ResponseEntity.ok(ApiResult.ok(Map.of("status", s.getStatus()), "file.upload.staged.success"));
  }

  @PostMapping("/upload/{sessionId}/commit")
  @RequiresPermission("file:write")
  public ResponseEntity<ApiResult<Object>> commit(@PathVariable UUID sessionId,
      @RequestBody Map<String, Object> body,
      @RequestHeader(value = "Idempotency-Key", required = false) String idem) {
    requireIdem(idem);
    UUID actor = CurrentActor.userId();
    requireUploadSessionWrite(sessionId, actor, "file.upload.commit");
    UUID attId = body.get("attachment_id") == null ? null : UUID.fromString((String) body.get("attachment_id"));
    var att = files.commit(actor, sessionId, attId, (String) body.get("change_note"));
    return ResponseEntity.ok(ApiResult.ok(
        Map.of("attachment_id", att.getId().toString(), "version", att.getCurrentVersion()),
        "file.upload.commit.success"));
  }

  @PostMapping("/{id}/restore")
  @RequiresPermission("file:write")
  public ResponseEntity<ApiResult<Object>> restoreVersion(@PathVariable UUID id,
      @RequestBody Map<String, Object> body,
      @RequestHeader(value = "Idempotency-Key", required = false) String idem) {
    requireIdem(idem);
    UUID actor = CurrentActor.userId();
    requireAttachmentWrite(id, actor, "file.restore");
    var att = files.restoreAsNewVersion(actor, id,
        ((Number) body.get("source_version")).intValue(), (String) body.get("change_note"));
    return ResponseEntity.ok(ApiResult.ok(
        Map.of("attachment_id", att.getId().toString(), "version", att.getCurrentVersion()),
        "file.restore.success"));
  }

  @PostMapping("/{id}/delete")
  @RequiresPermission("file:write")
  public ResponseEntity<ApiResult<Object>> softDelete(@PathVariable UUID id,
      @RequestHeader(value = "Idempotency-Key", required = false) String idem) {
    requireIdem(idem);
    UUID actor = CurrentActor.userId();
    requireAttachmentWrite(id, actor, "file.delete");
    files.softDelete(actor, id);
    return ResponseEntity.ok(ApiResult.ok(null, "file.delete.success"));
  }

  @PostMapping("/{id}/restore-attachment")
  @RequiresPermission("file:write")
  public ResponseEntity<ApiResult<Object>> restoreAttachment(@PathVariable UUID id,
      @RequestHeader(value = "Idempotency-Key", required = false) String idem) {
    requireIdem(idem);
    UUID actor = CurrentActor.userId();
    requireAttachmentWrite(id, actor, "file.restore");
    files.restore(actor, id);
    return ResponseEntity.ok(ApiResult.ok(null, "file.restore.success"));
  }

  @GetMapping("/tasks/{taskId}/attachments")
  @RequiresPermission("file:read")
  @org.springframework.transaction.annotation.Transactional(readOnly = true, timeout = 15)
  public ResponseEntity<ApiResult<Object>> listByTask(@PathVariable UUID taskId) {
    UUID actor = CurrentActor.userId();
    requireRead(taskId, actor, "file.list");
    var items = attachments.findByTaskIdAndDeletedAtIsNull(taskId).stream()
        .map(a -> Map.of("id", a.getId().toString(), "file_name", a.getFileName(),
            "status", a.getStatus(), "version", a.getCurrentVersion()))
        .toList();
    return ResponseEntity.ok(ApiResult.ok(Map.of("items", items), "file.list.success"));
  }

  private StreamTicketDto mintTicket(UUID attachmentId, UUID actor, String action) {    var att = attachments.findById(attachmentId)
        .orElseThrow(() -> AppException.notFound("Attachment not found"));
    if (att.getDeletedAt() != null) throw AppException.notFound("Attachment not found");
    requireRead(att.getTaskId(), actor, action);
    if (!"AVAILABLE".equals(att.getStatus())) {
      accessEvents.save(new FileAccessEvent(UuidV7.generate(), attachmentId, actor, action, "DENIED",
          "ATTACHMENT_NOT_AVAILABLE"));
      throw AppException.stateConflict("ATTACHMENT_NOT_AVAILABLE", "Attachment not available");
    }
    String raw = UUID.randomUUID().toString();
    String hash = vn.smarttask.common.service.TokenHash.sha256(raw);
    StreamTicket st = new StreamTicket(UuidV7.generate(), attachmentId, hash, actor,
        OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(30));
    tickets.save(st);
    accessEvents.save(new FileAccessEvent(UuidV7.generate(), attachmentId, actor, action, "ALLOWED", null));
    audit.append(actor, action, "attachment", attachmentId, "ALLOWED", null, "{}");
    return new StreamTicketDto(raw, st.getExpiresAt().toString());
  }

  @GetMapping("/{id}/preview")
  @RequiresPermission("file:read")
  public ResponseEntity<ApiResult<StreamTicketDto>> preview(@PathVariable UUID id) {
    UUID actor = CurrentActor.userId();
    return ResponseEntity.ok(ApiResult.ok(mintTicket(id, actor, "PREVIEW"), "file.preview.success"));
  }

  @GetMapping("/{id}/download")
  @RequiresPermission("file:read")
  public ResponseEntity<ApiResult<StreamTicketDto>> download(@PathVariable UUID id) {
    UUID actor = CurrentActor.userId();
    return ResponseEntity.ok(ApiResult.ok(mintTicket(id, actor, "DOWNLOAD"), "file.download.success"));
  }

  /**
   * Single-use stream consume with per-request re-auth. Public ticket bearer
   * path; when a session principal is also present it must match the ticket
   * actor (prevents ticket swapping across sessions). The ticket row is
   * locked (PESSIMISTIC_WRITE) inside {@link StreamConsumeService} so
   * concurrent double-use serializes: exactly one request wins, the loser
   * sees used=true. The ticket actor's account must still be ACTIVE/unlocked
   * with a live session, otherwise the ticket is consumed and the request is
   * hidden as 404 (revocation-safe). Returns real bytes (never a stub,
   * never a direct storage URL).
   */
  @GetMapping("/stream/{ticket}")
  public ResponseEntity<byte[]> stream(@PathVariable String ticket) {
    Principal caller = CurrentActor.current().orElse(null);
    var c = consume.consume(ticket, caller == null ? null : caller.userId());
    // F1.6: consume ran in ONE short transaction (lock -> validate/TTL/
    // single-use -> liveness + membership recheck -> mark used -> commit).
    // Byte transfer + FileAccessEvent evidence below run AFTER commit,
    // outside any transaction. No direct storage URLs are ever exposed.
    return switch (c.outcome()) {
      case EXPIRED -> ResponseEntity.status(410).body("STREAM_TICKET_EXPIRED".getBytes());
      case REVOKED -> {
        accessEvents.save(new FileAccessEvent(UuidV7.generate(), c.attachmentId(),
            c.actorUserId(), "STREAM", "DENIED", c.reason()));
        audit.appendDenied(c.actorUserId(), "file.stream", "attachment", c.attachmentId(),
            c.reason());
        yield ResponseEntity.status(404).body("RESOURCE_NOT_FOUND".getBytes());
      }
      case PRINCIPAL_MISMATCH -> {
        UUID who = caller == null ? null : caller.userId();
        accessEvents.save(new FileAccessEvent(UuidV7.generate(), c.attachmentId(),
            who, "STREAM", "DENIED", "PRINCIPAL_MISMATCH"));
        audit.appendDenied(who, "file.stream", "attachment", c.attachmentId(),
            "PRINCIPAL_MISMATCH");
        yield ResponseEntity.status(403).body("FORBIDDEN".getBytes());
      }
      case NOT_FOUND -> ResponseEntity.status(404).body("RESOURCE_NOT_FOUND".getBytes());
      case OBJECT_MISSING -> {
        accessEvents.save(new FileAccessEvent(UuidV7.generate(), c.attachmentId(),
            c.actorUserId(), "STREAM", "DENIED", "FILE_OBJECT_MISSING"));
        audit.appendDenied(c.actorUserId(), "file.stream", "attachment", c.attachmentId(),
            "FILE_OBJECT_MISSING");
        throw AppException.stateConflict("FILE_OBJECT_MISSING", "Stored object missing");
      }
      case OK -> {
        FileContentService.Content data;
        try {
          data = content.read(c.objectKey(), c.bucket(), c.contentType());
        } catch (AppException ex) {
          // F2.3: object wiped from storage (409 FILE_OBJECT_MISSING) or
          // storage down (503) leaves DENIED evidence, then the clean
          // envelope propagates (no 500 stack).
          accessEvents.save(new FileAccessEvent(UuidV7.generate(), c.attachmentId(),
              c.actorUserId(), "STREAM", "DENIED", ex.getReasonCode()));
          audit.appendDenied(c.actorUserId(), "file.stream", "attachment", c.attachmentId(),
              ex.getReasonCode());
          throw ex;
        }
        accessEvents.save(new FileAccessEvent(UuidV7.generate(), c.attachmentId(),
            c.actorUserId(), "STREAM", "ALLOWED", null));
        audit.append(c.actorUserId(), "file.stream", "attachment", c.attachmentId(),
            "ALLOWED", null, "{}");
        var builder = ResponseEntity.ok();
        if (c.contentType() != null) {
          try {
            builder.contentType(org.springframework.http.MediaType.parseMediaType(c.contentType()));
          } catch (Exception ignored) {
            builder.contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM);
          }
        } else {
          builder.contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM);
        }
        yield builder.body(data.bytes());
      }
    };
  }
}
