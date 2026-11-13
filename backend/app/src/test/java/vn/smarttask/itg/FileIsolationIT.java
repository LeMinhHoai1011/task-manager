package vn.smarttask.itg;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.smarttask.common.AppException;
import vn.smarttask.files.entity.Attachment;
import vn.smarttask.files.entity.UploadSession;
import vn.smarttask.files.repo.UploadSessionRepository;
import vn.smarttask.files.service.FileService;
import org.springframework.test.context.TestPropertySource;
import static org.junit.jupiter.api.Assertions.*;

/** Database-level negative cases must reject BEFORE touching any storage provider. */
@TestPropertySource(properties = {"file.gateway.mode=local", "file.gateway.local-root=target/audit-file-isolation"})
class FileIsolationIT extends ItgBase {
  @Autowired FileService files;
  @Autowired UploadSessionRepository uploads;
  @Autowired vn.smarttask.files.repo.FileVersionRepository fileVersions;
  @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

  @Test void commitCannotTargetAttachmentOnAnotherTask() {
    var actor = activeUser("fileowner", "Strong-Audit-135!");
    UUID ws = mkWorkspace(actor.getId(), "files");
    var project = mkActiveProject(actor.getId(), ws, "files");
    var source = mkTask(actor.getId(), project.getId(), "Source");
    var target = mkTask(actor.getId(), project.getId(), "Target");
    var attachment = attachments.saveAndFlush(new Attachment(UUID.randomUUID(), target.getId(), "private.pdf", "application/pdf", 10, actor.getId()));
    var upload = new UploadSession(UUID.randomUUID(), actor.getId(), source.getId(), "other.pdf", "application/pdf", 10, OffsetDateTime.now().plusHours(1));
    upload.setStatus("READY"); uploads.saveAndFlush(upload);
    assertEquals(404, assertThrows(AppException.class,
        () -> files.commit(actor.getId(), upload.getId(), attachment.getId(), "overwrite")).getHttpStatus().value());
    assertEquals(target.getId(), attachments.findById(attachment.getId()).orElseThrow().getTaskId());
  }

  @Test void idempotencyKeyCannotRevealAnotherUsersUploadSession() {
    var first = activeUser("filefirst", "Strong-Audit-135!");
    var second = activeUser("filesecond", "Strong-Audit-135!");
    var ws = mkWorkspace(first.getId(), "files");
    var project = mkActiveProject(first.getId(), ws, "files");
    var task = mkTask(first.getId(), project.getId(), "Source");
    String key = "upload-" + UUID.randomUUID();
    var existing = files.initUpload(first.getId(), task.getId(), "ok.pdf", "application/pdf", 10, key);
    assertEquals(existing.getId(), files.initUpload(first.getId(), task.getId(), "ok.pdf", "application/pdf", 10, key).getId());
    assertEquals(404, assertThrows(AppException.class,
        () -> files.initUpload(second.getId(), task.getId(), "ok.pdf", "application/pdf", 10, key)).getHttpStatus().value());
  }

  UUID readyUpload(UUID actor, UUID task, String text) {
    byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    var session = files.initUpload(actor, task, "Unicode-ghi-chú.pdf", "application/pdf", bytes.length, "upload-" + UUID.randomUUID());
    files.stageBytes(actor, session.getId(), bytes);
    files.stageComplete(actor, session.getId(), bytes.length, null);
    return session.getId();
  }

  @Test void simultaneousCommitsAllocateDifferentVersionsWithoutOverwritingBytes() throws Exception {
    var actor = activeUser("fileparallel", "Strong-Audit-135!");
    var ws = mkWorkspace(actor.getId(), "fileparallel");
    var project = mkActiveProject(actor.getId(), ws, "files");
    var task = mkTask(actor.getId(), project.getId(), "Concurrent versions");
    var original = files.commit(actor.getId(), readyUpload(actor.getId(), task.getId(), "original"), null, null);
    UUID first = readyUpload(actor.getId(), task.getId(), "first revision");
    UUID second = readyUpload(actor.getId(), task.getId(), "second revision");
    var start = new java.util.concurrent.CountDownLatch(1);
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var one = pool.submit(() -> { start.await(); return files.commit(actor.getId(), first, original.getId(), null); });
      var two = pool.submit(() -> { start.await(); return files.commit(actor.getId(), second, original.getId(), null); });
      start.countDown();
      one.get(20, java.util.concurrent.TimeUnit.SECONDS);
      two.get(20, java.util.concurrent.TimeUnit.SECONDS);
    }
    var versions = fileVersions.findByAttachmentIdOrderByVersionNoAsc(original.getId());
    assertEquals(java.util.List.of(1, 2, 3), versions.stream().map(v -> v.getVersionNo()).toList());
    var contents = new java.util.HashSet<String>();
    for (var version : versions) {
      var bytes = java.nio.file.Files.readAllBytes(java.nio.file.Path.of("target/audit-file-isolation").resolve(version.getObjectKey()));
      assertEquals(version.getChecksumSha256(), java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
      contents.add(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
    }
    assertEquals(java.util.Set.of("original", "first revision", "second revision"), contents);
  }

  @Test void rollbackRetainsStagingAndAllowsRetry() {
    var actor = activeUser("filerollback", "Strong-Audit-135!");
    var project = mkActiveProject(actor.getId(), mkWorkspace(actor.getId(), "rollback"), "rollback");
    var task = mkTask(actor.getId(), project.getId(), "Rollback upload");
    UUID upload = readyUpload(actor.getId(), task.getId(), "retryable bytes");
    String staging = uploads.findById(upload).orElseThrow().getStagingKey();
    UUID rolledBack = new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status -> {
      var result = files.commit(actor.getId(), upload, null, null);
      status.setRollbackOnly();
      return result.getId();
    });
    assertTrue(attachments.findById(rolledBack).isEmpty());
    assertEquals("READY", uploads.findById(upload).orElseThrow().getStatus());
    var stagingPath = java.nio.file.Path.of("target/audit-file-isolation").resolve(staging);
    assertTrue(java.nio.file.Files.exists(stagingPath));
    assertNotNull(files.commit(actor.getId(), upload, null, null));
    assertFalse(java.nio.file.Files.exists(stagingPath));
  }

  @Test void expiredReadyUploadCannotBeCommitted() {
    var actor = activeUser("fileexpired", "Strong-Audit-135!");
    var project = mkActiveProject(actor.getId(), mkWorkspace(actor.getId(), "expired"), "expired");
    var task = mkTask(actor.getId(), project.getId(), "Expired upload");
    var expired = new UploadSession(UUID.randomUUID(), actor.getId(), task.getId(), "expired.pdf", "application/pdf", 10, OffsetDateTime.now().minusMinutes(1));
    expired.setStatus("READY"); uploads.saveAndFlush(expired);
    assertEquals("UPLOAD_SESSION_EXPIRED", assertThrows(AppException.class,
        () -> files.commit(actor.getId(), expired.getId(), null, null)).getReasonCode());
  }
}
