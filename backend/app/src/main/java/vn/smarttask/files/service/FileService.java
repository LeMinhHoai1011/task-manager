package vn.smarttask.files.service;

import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.smarttask.admin.repo.SystemSettingRepository;
import vn.smarttask.common.AppException;
import vn.smarttask.common.UuidV7;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.common.service.JobService;
import vn.smarttask.common.service.OutboxService;
import vn.smarttask.files.FilePolicy;
import vn.smarttask.files.FileVersionChain;
import vn.smarttask.files.StorageErrors;
import vn.smarttask.files.entity.*;
import vn.smarttask.files.repo.*;
import vn.smarttask.task.repo.TaskRepository;

/**
 * PH-3 FileService (manual, no-AI): upload_session -> staging -> scan(UNSUPPORTED when no
 * engine, explicitly NOT malware-protected) -> commit (monotonic version, never overwrite)
 * -> restore-as-new-version, soft-delete/restore by delete_group_id.
 * Mutations blocked when Task is PENDING_APPROVAL (via any API path calling here).
 */
@Service
public class FileService {
  private final AttachmentRepository attachments;
  private final FileVersionRepository versions;
  private final FileObjectRepository objects;
  private final UploadSessionRepository sessions;
  private final FileAccessEventRepository accessEvents;
  private final TaskRepository tasks;
  private final SystemSettingRepository settings;
  private final AuditService audit;
  private final OutboxService outbox;
  private final JobService jobs;
  private final MinioClient minio;
  private final String bucket;
  @Value("${file.gateway.mode:local}")
  private String storageMode = "s3";
  @Value("${file.gateway.local-root:./var/files}")
  private String localRoot = "./var/files";

  private java.nio.file.Path localPath(String key) {
    var root = java.nio.file.Path.of(localRoot).toAbsolutePath().normalize();
    var path = root.resolve(key).normalize();
    if (!path.startsWith(root)) throw AppException.badRequest("FILE_PATH_INVALID", "Invalid storage path");
    return path;
  }

  private void writeLocal(String key, byte[] bytes) throws java.io.IOException {
    var path = localPath(key);
    java.nio.file.Files.createDirectories(path.getParent());
    java.nio.file.Files.write(path, bytes, java.nio.file.StandardOpenOption.CREATE_NEW);
  }

  public FileService(AttachmentRepository attachments, FileVersionRepository versions,
      FileObjectRepository objects, UploadSessionRepository sessions,
      FileAccessEventRepository accessEvents, TaskRepository tasks,
      SystemSettingRepository settings, AuditService audit, OutboxService outbox, JobService jobs,
      MinioClient minio,
      @Value("${file.gateway.minio-bucket:smarttask}") String bucket) {
    this.attachments = attachments; this.versions = versions; this.objects = objects;
    this.sessions = sessions; this.accessEvents = accessEvents; this.tasks = tasks;
    this.settings = settings; this.audit = audit; this.outbox = outbox; this.jobs = jobs;
    this.minio = minio; this.bucket = bucket;
  }

  private vn.smarttask.task.entity.Task requireTask(UUID taskId) {
    return tasks.findById(taskId).orElseThrow(() -> AppException.notFound("Task not found"));
  }

  private void requireNotPendingApproval(vn.smarttask.task.entity.Task t, String action, UUID actor) {
    if ("PENDING_APPROVAL".equals(t.getState())) {
      audit.appendDenied(actor, action, "task", t.getId(), "EXECUTION_LOCKED");
      accessEvents.save(new FileAccessEvent(UuidV7.generate(), null, actor, action, "DENIED",
          "EXECUTION_LOCKED_PENDING_APPROVAL"));
      throw AppException.executionLocked("EXECUTION_LOCKED", "Task locked pending approval");
    }
  }

  private Set<String> allowedTypes() {
    return settings.findById("file.allowed_content_types")
        .map(s -> parseJsonArray(s.getValue()))
        .orElse(Set.of("application/pdf", "image/png", "image/jpeg"));
  }

  private long maxBytes() {
    long mb = settings.findById("file.max_upload_mb")
        .map(s -> { try { return Long.parseLong(s.getValue().replaceAll("[^0-9]", "")); }
          catch (NumberFormatException e) { return 50L; } })
        .orElse(50L);
    return FilePolicy.maxBytesFromMb(mb);
  }

  private static Set<String> parseJsonArray(String json) {
    Set<String> out = new HashSet<>();
    if (json == null) return out;
    for (String part : json.replace("[", "").replace("]", "").replace("\"", "").split(",")) {
      String t = part.trim().toLowerCase(java.util.Locale.ROOT);
      if (!t.isEmpty()) out.add(t);
    }
    return out;
  }

  @Transactional
  public UploadSession initUpload(UUID actor, UUID taskId, String fileName, String mimeType,
      long declaredSize, String idempotencyKey) {
    var t = requireTask(taskId);
    requireNotPendingApproval(t, "file.upload.init", actor);
    if (idempotencyKey != null) {
      var existing = sessions.findByIdempotencyKey(idempotencyKey);
      if (existing.isPresent()) return existing.get();
    }
    var check = FilePolicy.checkUpload(mimeType, declaredSize, allowedTypes(), maxBytes(), t.getState());
    accessEvents.save(new FileAccessEvent(UuidV7.generate(), null, actor, "UPLOAD",
        check.allowed() ? "ALLOWED" : "DENIED", check.allowed() ? null : check.reasonCode()));
    if (!check.allowed()) {
      audit.appendDenied(actor, "file.upload.init", "task", taskId, check.reasonCode());
      if ("FILE_TYPE_NOT_ALLOWED".equals(check.reasonCode())) {
        throw AppException.unprocessable("FILE_TYPE_NOT_ALLOWED", "Content type not allowed");
      }
      if ("FILE_TOO_LARGE".equals(check.reasonCode())) {
        throw new AppException("FILE_TOO_LARGE", "file.too_large", "FILE_TOO_LARGE",
            org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE, "File too large");
      }
      throw AppException.executionLocked("EXECUTION_LOCKED", "Task locked pending approval");
    }
    UploadSession s = new UploadSession(UuidV7.generate(), actor, taskId, fileName, mimeType,
        declaredSize, OffsetDateTime.now(ZoneOffset.UTC).plusHours(2));
    s.setStatus("UPLOADING");
    s.setIdempotencyKey(idempotencyKey);
    s.setStagingKey("staging/" + s.getId() + "/" + fileName);
    sessions.save(s);
    audit.append(actor, "file.upload.init", "task", taskId, "ALLOWED", null, "{}");
    return s;
  }

  /**
   * Server-side staging upload used by the web client. The browser sends the
   * bytes to the authenticated API (same-origin), the backend writes them to
   * the private staging key. This avoids exposing MinIO to the browser and
   * keeps the existing staging -> scan -> commit flow intact.
   */
  @Transactional
  public UploadSession stageBytes(UUID actor, UUID sessionId, byte[] bytes) {
    UploadSession s = sessions.findById(sessionId)
        .orElseThrow(() -> AppException.notFound("Upload session not found"));
    if (s.getExpiresAt().isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
      s.setStatus("EXPIRED");
      sessions.save(s);
      throw AppException.stateConflict("UPLOAD_SESSION_EXPIRED", "Upload session expired");
    }
    if (!"UPLOADING".equals(s.getStatus()) && !"PENDING".equals(s.getStatus())) {
      throw AppException.stateConflict("UPLOAD_SESSION_STATE_INVALID", "Bad session state");
    }
    if (bytes == null || bytes.length != s.getDeclaredSize()) {
      throw AppException.badRequest("DECLARED_SIZE_MISMATCH", "Size mismatch");
    }
    try (InputStream in = new ByteArrayInputStream(bytes)) {
      if ("local".equalsIgnoreCase(storageMode)) {
        writeLocal(s.getStagingKey(), bytes);
      } else {
      minio.putObject(PutObjectArgs.builder()
          .bucket(bucket)
          .object(s.getStagingKey())
          .stream(in, bytes.length, -1)
          .contentType(s.getMimeType())
          .build());
      }
    } catch (Exception e) {
      throw mapStagingError(e);
    }
    audit.append(actor, "file.upload.stage", "upload_session", sessionId, "ALLOWED", null, "{}");
    return s;
  }

  /** Staging bytes received -> scan. No engine in V1: scan_status=UNSUPPORTED (NOT protected). */
  @Transactional
  public UploadSession stageComplete(UUID actor, UUID sessionId, long actualBytes, String checksum) {
    UploadSession s = sessions.findById(sessionId)
        .orElseThrow(() -> AppException.notFound("Upload session not found"));
    if (s.getExpiresAt().isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
      s.setStatus("EXPIRED");
      sessions.save(s);
      throw AppException.stateConflict("UPLOAD_SESSION_EXPIRED", "Upload session expired");
    }
    if (!"UPLOADING".equals(s.getStatus()) && !"PENDING".equals(s.getStatus())) {
      throw AppException.stateConflict("UPLOAD_SESSION_STATE_INVALID", "Bad session state");
    }
    if (actualBytes != s.getDeclaredSize()) {
      throw AppException.badRequest("DECLARED_SIZE_MISMATCH", "Size mismatch");
    }
    // F2.3: staging bytes must REALLY exist in object storage (PUT by the
    // client to staging_key before this call). Missing staging -> 409, storage
    // down -> 503; never a silent READY with no bytes behind it.
    byte[] staged = readStagingBytes(s);
    if (staged.length != actualBytes || staged.length != s.getDeclaredSize()) {
      throw AppException.badRequest("DECLARED_SIZE_MISMATCH", "Staged bytes differ from declared size");
    }
    if (checksum != null && !checksum.isBlank()
        && !sha256Hex(staged).equalsIgnoreCase(checksum.trim())) {
      throw AppException.badRequest("CHECKSUM_MISMATCH", "Staged bytes fail checksum");
    }
    s.setStatus("SCAN_PENDING");
    sessions.save(s);
    // scan stub: no engine -> UNSUPPORTED; enqueue FILE_SCAN for future engine
    jobs.enqueue("FILE_SCAN", "{\"upload_session_id\":\"" + sessionId + "\"}", null, "scan-" + sessionId);
    s.setStatus("READY");
    sessions.save(s);
    return s;
  }

  /** Commit staging -> new monotonic version (never overwrite). */
  @Transactional
  public Attachment commit(UUID actor, UUID sessionId, UUID attachmentIdOrNull, String changeNote) {
    UploadSession s = sessions.findById(sessionId)
        .orElseThrow(() -> AppException.notFound("Upload session not found"));
    if (!"READY".equals(s.getStatus()) && !"SCAN_PENDING".equals(s.getStatus())) {
      throw AppException.stateConflict("UPLOAD_SESSION_STATE_INVALID", "Session not ready");
    }
    var t = requireTask(s.getTaskId());
    requireNotPendingApproval(t, "file.upload.commit", actor);
    Attachment att;
    if (attachmentIdOrNull == null) {
      // NB: save() returns the managed copy (assigned-UUID entities take the
      // merge path); later mutations must target it, otherwise a second save
      // of the detached original would overwrite created_at with null.
      att = attachments.save(new Attachment(UuidV7.generate(), s.getTaskId(), s.getFileName(),
          s.getMimeType(), s.getDeclaredSize(), actor));
    } else {
      att = attachments.findById(attachmentIdOrNull)
          .orElseThrow(() -> AppException.notFound("Attachment not found"));
      if (att.getDeletedAt() != null) throw AppException.notFound("Attachment not found");
    }
    List<Integer> existing = versions.findByAttachmentIdOrderByVersionNoAsc(att.getId())
        .stream().map(FileVersion::getVersionNo).toList();
    int next = FileVersionChain.nextVersion(existing);
    // F2.3: materialize the versioned object from staging (server-side copy,
    // never a client-supplied key). Staging vanished meanwhile -> 409,
    // storage down -> 503.
    byte[] staged = readStagingBytes(s);
    if (staged.length != s.getDeclaredSize()) {
      throw AppException.badRequest("DECLARED_SIZE_MISMATCH", "Staged bytes differ from declared size");
    }
    String sha = sha256Hex(staged);
    String objectKey = "att/" + att.getId() + "/v" + next;
    try {
      if ("local".equalsIgnoreCase(storageMode)) {
        writeLocal(objectKey, staged);
      } else {
      minio.copyObject(CopyObjectArgs.builder().bucket(bucket).object(objectKey)
          .source(CopySource.builder().bucket(bucket).object(s.getStagingKey()).build())
          .build());
      }
    } catch (Exception e) {
      throw mapStagingError(e);
    }
    // Best-effort staging delete; leftovers stay private under staging/ and
    // are swept by cleanupExpiredSessions().
    try {
      if ("local".equalsIgnoreCase(storageMode)) {
        java.nio.file.Files.deleteIfExists(localPath(s.getStagingKey()));
      } else {
      minio.removeObject(RemoveObjectArgs.builder().bucket(bucket)
          .object(s.getStagingKey()).build());
      }
    } catch (Exception ignored) {
      // swept later by cleanupExpiredSessions()
    }
    FileObject obj = new FileObject(UuidV7.generate(), objectKey, bucket, s.getDeclaredSize());
    obj.setContentType(s.getMimeType());
    obj.setChecksumSha256(sha);
    obj.setScanStatus(FilePolicy.scanStub(false)); // V1: no engine -> UNSUPPORTED
    objects.save(obj);
    FileVersion v = new FileVersion(UuidV7.generate(), att.getId(), next, objectKey, s.getDeclaredSize());
    v.setChangeNote(changeNote);
    v.setCreatedBy(actor);
    v.setChecksumSha256(sha);
    v.setMimeType(s.getMimeType());
    versions.save(v);
    att.setCurrentVersion(next);
    att.setSizeBytes(s.getDeclaredSize());
    att.setVersion(att.getVersion() + 1);
    attachments.save(att);
    s.setStatus("COMMITTED");
    s.setAttachmentId(att.getId());
    sessions.save(s);
    accessEvents.save(new FileAccessEvent(UuidV7.generate(), att.getId(), actor, "COMMIT", "ALLOWED", null));
    audit.append(actor, "file.upload.commit", "attachment", att.getId(), "ALLOWED", null, "{}");
    outbox.emit("attachment", att.getId(), "file.version.added", "{}");
    return att;
  }

  /** Restore an older version as a NEW version (never overwrite current). */
  @Transactional
  public Attachment restoreAsNewVersion(UUID actor, UUID attachmentId, int sourceVersionNo, String changeNote) {
    Attachment att = attachments.findById(attachmentId)
        .orElseThrow(() -> AppException.notFound("Attachment not found"));
    if (att.getDeletedAt() != null) throw AppException.notFound("Attachment not found");
    var t = requireTask(att.getTaskId());
    requireNotPendingApproval(t, "file.restore", actor);
    List<FileVersion> all = versions.findByAttachmentIdOrderByVersionNoAsc(attachmentId);
    FileVersion src = all.stream().filter(v -> v.getVersionNo() == sourceVersionNo).findFirst()
        .orElseThrow(() -> AppException.notFound("Version not found"));
    int next = FileVersionChain.restoreAsNewVersion(all.stream().map(FileVersion::getVersionNo).toList());
    FileVersion nv = new FileVersion(UuidV7.generate(), attachmentId, next, src.getObjectKey(), src.getSizeBytes());
    nv.setChangeNote(changeNote == null ? "restore-from-v" + sourceVersionNo : changeNote);
    nv.setCreatedBy(actor);
    nv.setMimeType(src.getMimeType());
    versions.save(nv);
    att.setCurrentVersion(next);
    att.setVersion(att.getVersion() + 1);
    attachments.save(att);
    accessEvents.save(new FileAccessEvent(UuidV7.generate(), attachmentId, actor, "RESTORE", "ALLOWED", null));
    audit.append(actor, "file.restore", "attachment", attachmentId, "ALLOWED", null, "{}");
    return att;
  }

  @Transactional
  public Attachment softDelete(UUID actor, UUID attachmentId) {
    Attachment att = attachments.findById(attachmentId)
        .orElseThrow(() -> AppException.notFound("Attachment not found"));
    var t = requireTask(att.getTaskId());
    requireNotPendingApproval(t, "file.delete", actor);
    UUID group = att.getDeleteGroupId() != null ? att.getDeleteGroupId() : UuidV7.generate();
    att.setDeleteGroupId(group);
    att.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
    att.setDeletedBy(actor);
    att.setStatus("DELETED");
    attachments.save(att);
    accessEvents.save(new FileAccessEvent(UuidV7.generate(), attachmentId, actor, "DELETE", "ALLOWED", null));
    audit.append(actor, "file.delete", "attachment", attachmentId, "ALLOWED", null, "{}");
    return att;
  }

  @Transactional
  public Attachment restore(UUID actor, UUID attachmentId) {
    Attachment att = attachments.findById(attachmentId)
        .orElseThrow(() -> AppException.notFound("Attachment not found"));
    if (att.getDeletedAt() == null) return att;
    att.setDeletedAt(null);
    att.setDeletedBy(null);
    att.setDeleteGroupId(null);
    att.setStatus("AVAILABLE");
    attachments.save(att);
    audit.append(actor, "file.restore", "attachment", attachmentId, "ALLOWED", null, "{}");
    return att;
  }

  /** STORAGE_RECONCILE stub: enqueues job; real orphan-scan runs in worker phase. */
  public void reconcileStub(UUID actor) {
    jobs.enqueue("STORAGE_RECONCILE", "{}", null, "reconcile-" + UUID.randomUUID());
    audit.append(actor, "storage.reconcile", "system", null, "ALLOWED", "STUB", "{}");
  }

  /**
   * F2.3 staging cleanup: expired, never-committed upload sessions are marked
   * EXPIRED and their staging objects removed (best-effort; S3 delete is
   * idempotent). Page-capped at 500 rows per call. Returns rows expired.
   */
  @Transactional
  public int cleanupExpiredSessions() {
    var doomed = sessions.findByExpiresAtBeforeAndStatusIn(
        OffsetDateTime.now(ZoneOffset.UTC),
        List.of("PENDING", "UPLOADING", "SCAN_PENDING", "READY"),
        PageRequest.of(0, 500));
    int n = 0;
    for (UploadSession s : doomed) {
      s.setStatus("EXPIRED");
      sessions.save(s);
      if (s.getStagingKey() != null && !s.getStagingKey().isBlank()) {
        try {
          if ("local".equalsIgnoreCase(storageMode)) {
            java.nio.file.Files.deleteIfExists(localPath(s.getStagingKey()));
          } else {
          minio.removeObject(RemoveObjectArgs.builder().bucket(bucket)
              .object(s.getStagingKey()).build());
          }
        } catch (Exception ignored) {
          // leftovers stay private under staging/; retried on the next sweep
        }
      }
      n++;
    }
    audit.append(null, "storage.staging.cleanup", "system", null, "ALLOWED",
        "EXPIRED:" + n, "{}");
    return n;
  }

  /** Read staging bytes or throw 409 (vanished) / 503 (storage down). Never null. */
  private byte[] readStagingBytes(UploadSession s) {
    if (s.getStagingKey() == null || s.getStagingKey().isBlank()) {
      throw AppException.stateConflict("STAGING_OBJECT_MISSING",
          "No staging object for session " + s.getId());
    }
    if ("local".equalsIgnoreCase(storageMode)) {
      try { return java.nio.file.Files.readAllBytes(localPath(s.getStagingKey())); }
      catch (java.nio.file.NoSuchFileException e) { throw AppException.stateConflict("STAGING_OBJECT_MISSING", "Staging object missing"); }
      catch (java.io.IOException e) { throw StorageErrors.unavailable("staging", e); }
    }
    try (InputStream in = minio.getObject(GetObjectArgs.builder()
        .bucket(bucket).object(s.getStagingKey()).build())) {
      return in.readAllBytes();
    } catch (Exception e) {
      throw mapStagingError(e);
    }
  }

  private static AppException mapStagingError(Exception e) {
    if (e instanceof AppException ae) return ae;
    if (StorageErrors.isMissing(e)) {
      return AppException.stateConflict("STAGING_OBJECT_MISSING",
          "Staging object missing in storage");
    }
    return StorageErrors.unavailable("staging", e);
  }

  static String sha256Hex(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
