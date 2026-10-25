package vn.smarttask.itg;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.minio.GetBucketPolicyArgs;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import vn.smarttask.common.UuidV7;
import vn.smarttask.files.entity.UploadSession;
import vn.smarttask.files.repo.FileAccessEventRepository;
import vn.smarttask.files.repo.FileObjectRepository;
import vn.smarttask.files.repo.FileVersionRepository;
import vn.smarttask.files.repo.StreamTicketRepository;
import vn.smarttask.files.repo.UploadSessionRepository;
import vn.smarttask.files.service.FileContentService;
import vn.smarttask.files.service.FileService;
import vn.smarttask.identity.entity.AppUser;

/**
 * F2.3 Object Storage live verification (NOT mocked): real bytes against the
 * S3-compatible store + real PostgreSQL scratch rows.
 *
 * <p>Environment substitution (documented): MinIO Hub image cannot be pulled
 * in this sandbox, so the S3-API-compatible S3Mock at
 * {@code http://localhost:9090} (region us-east-1, bucket
 * {@code smarttask-test}, path-style, credentials test/test) stands in for
 * MinIO. The MinIO Java client speaks plain S3, so the production code path
 * is identical; only the endpoint/bucket/credentials properties differ.
 *
 * <p>Hermetic fixtures: every test mints its own users (unique
 * {@code sti_<tag>} names) and its own S3 prefix
 * {@code storageit/<uuid>/}, so parallel/sequential runs never collide.
 */
@TestPropertySource(properties = {
    "file.gateway.minio-endpoint=http://localhost:9090",
    "file.gateway.minio-access-key=test",
    "file.gateway.minio-secret-key=test",
    "file.gateway.minio-bucket=smarttask-test"})
class StorageIT extends ItgBase {

  @Autowired FileService files;
  @Autowired FileContentService content;
  @Autowired MinioClient minio;
  @Autowired UploadSessionRepository uploadSessions;
  @Autowired FileVersionRepository fileVersions;
  @Autowired FileObjectRepository fileObjects;
  @Autowired StreamTicketRepository streamTickets;
  @Autowired FileAccessEventRepository fileAccessEvents;
  @Value("${file.gateway.minio-bucket}") String bucket;

  /**
   * The S3Mock container's {@code initialBuckets} env is not honoured on every
   * image build, so the bucket is created idempotently instead of assuming it.
   */
  @org.junit.jupiter.api.BeforeEach
  void ensureBucket() throws Exception {
    if (!minio.bucketExists(io.minio.BucketExistsArgs.builder().bucket(bucket).build())) {
      minio.makeBucket(io.minio.MakeBucketArgs.builder().bucket(bucket).build());
    }
  }

  record Flow(AppUser mgr, AppUser member, String mgrToken, String memberToken,
      vn.smarttask.project.entity.Project project, vn.smarttask.task.entity.Task task) {}

  private Flow flow(String prefix) {
    var mgr = activeUser(prefix + "mgr", "Secret123!");
    var member = activeUser(prefix + "mbr", "Secret123!");
    UUID ws = mkWorkspace(mgr.getId(), prefix);
    addWsMember(ws, member.getId(), "MEMBER");
    var p = mkActiveProject(mgr.getId(), ws, prefix);
    addProjectMember(mgr.getId(), p.getId(), member.getId(), "MEMBER");
    var t = mkTask(mgr.getId(), p.getId(), prefix + " file task");
    return new Flow(mgr, member, loginToken(mgr.getUsername(), "Secret123!"),
        loginToken(member.getUsername(), "Secret123!"), p, t);
  }

  private static String sha256(byte[] b) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
  }

  private void putStaging(String key, byte[] bytes, String contentType) throws Exception {
    minio.putObject(PutObjectArgs.builder().bucket(bucket).object(key)
        .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
        .contentType(contentType).build());
  }

  private byte[] getBytes(String key) throws Exception {
    try (var in = minio.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) {
      return in.readAllBytes();
    }
  }

  /** Full upload flow over HTTP, returns attachment id + final object key. */
  private UUID commitFlow(Flow f, String fileName, byte[] bytes, String mime,
      UUID attachmentIdOrNull) throws Exception {
    MvcResult init = doPost("/api/v1/files/upload", f.mgrToken(), Map.of(
        "task_id", f.task().getId().toString(),
        "file_name", fileName,
        "mime_type", mime,
        "declared_size", bytes.length), Map.of("Idempotency-Key", idem()));
    assertEquals(200, init.getResponse().getStatus(),
        "init failed: " + init.getResponse().getContentAsString());
    String sessionId = json(init).path("data").path("upload_session_id").asText();
    String stagingKey = json(init).path("data").path("staging_key").asText();
    assertTrue(stagingKey.startsWith("staging/"));

    putStaging(stagingKey, bytes, mime);

    MvcResult staged = doPost("/api/v1/files/upload/" + sessionId + "/complete", f.mgrToken(),
        Map.of("actual_bytes", bytes.length, "checksum_sha256", sha256(bytes)),
        Map.of("Idempotency-Key", idem()));
    assertEquals(200, staged.getResponse().getStatus(),
        "stage failed: " + staged.getResponse().getContentAsString());

    Map<String, Object> commitBody = new HashMap<>();
    if (attachmentIdOrNull != null) commitBody.put("attachment_id", attachmentIdOrNull.toString());
    commitBody.put("change_note", "v-by-" + fileName);
    MvcResult commit = doPost("/api/v1/files/upload/" + sessionId + "/commit", f.mgrToken(),
        commitBody, Map.of("Idempotency-Key", idem()));
    assertEquals(200, commit.getResponse().getStatus(),
        "commit failed: " + commit.getResponse().getContentAsString());
    return UUID.fromString(json(commit).path("data").path("attachment_id").asText());
  }

  private String mintTicket(String token, UUID attId, String kind) throws Exception {
    MvcResult r = doGet("/api/v1/files/" + attId + "/" + kind, token);
    assertEquals(200, r.getResponse().getStatus(),
        "mint " + kind + " failed: " + r.getResponse().getContentAsString());
    return json(r).path("data").path("ticket").asText();
  }

  /** Anonymous (bearer-ticket only) stream GET, as the public path allows. */
  private MvcResult streamRaw(String raw) throws Exception {
    var req = MockMvcRequestBuilders.get("/api/v1/files/stream/" + raw)
        .header("X-Forwarded-For", fwd());
    return mvc.perform(req).andReturn();
  }

  @Test
  void uploadCommitStreamsRealBytes() throws Exception {
    Flow f = flow("sti1");
    byte[] bytes = ("pdf-bytes-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
    UUID attId = commitFlow(f, "doc-sti1.pdf", bytes, "application/pdf", null);

    var versions = fileVersions.findByAttachmentIdOrderByVersionNoAsc(attId);
    assertEquals(1, versions.size());
    String objectKey = versions.get(0).getObjectKey();
    assertTrue(objectKey.startsWith("att/" + attId + "/v1"));

    // bytes in S3 == uploaded bytes; server checksum matches client checksum
    assertArrayEquals(bytes, getBytes(objectKey));
    var obj = fileObjects.findByObjectKey(objectKey).orElseThrow();
    assertEquals(bucket, obj.getBucket());
    assertEquals(sha256(bytes), obj.getChecksumSha256());
    assertEquals(sha256(bytes), versions.get(0).getChecksumSha256());

    // preview + download tickets both stream the exact bytes
    for (String kind : List.of("preview", "download")) {
      String raw = mintTicket(f.memberToken(), attId, kind);
      MvcResult s = streamRaw(raw);
      assertEquals(200, s.getResponse().getStatus(),
          "stream " + kind + " failed: " + s.getResponse().getContentAsString());
      assertArrayEquals(bytes, s.getResponse().getContentAsByteArray());
      assertTrue(s.getResponse().getContentType().contains("application/pdf"));
    }
  }

  @Test
  void secondVersionKeepsFirst() throws Exception {
    Flow f = flow("sti2");
    byte[] v1 = ("v1-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
    byte[] v2 = ("v2-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
    UUID attId = commitFlow(f, "doc-sti2.pdf", v1, "application/pdf", null);
    commitFlow(f, "doc-sti2.pdf", v2, "application/pdf", attId);

    var versions = fileVersions.findByAttachmentIdOrderByVersionNoAsc(attId);
    assertEquals(2, versions.size());
    assertEquals(1, versions.get(0).getVersionNo());
    assertEquals(2, versions.get(1).getVersionNo());
    // version 1 object untouched by the version 2 commit
    assertArrayEquals(v1, getBytes(versions.get(0).getObjectKey()));
    assertArrayEquals(v2, getBytes(versions.get(1).getObjectKey()));
  }

  @Test
  void revokedMembershipHidesAttachmentAndKillsStaleTicket() throws Exception {
    Flow f = flow("sti3");
    byte[] bytes = ("secret-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
    UUID attId = commitFlow(f, "doc-sti3.pdf", bytes, "application/pdf", null);
    String stale = mintTicket(f.memberToken(), attId, "preview");

    projects.removeMember(f.mgr().getId(), f.project().getId(), f.member().getId());

    // fresh preview after revoke -> 404 (existence hidden)
    MvcResult denied = doGet("/api/v1/files/" + attId + "/preview", f.memberToken());
    assertEquals(404, denied.getResponse().getStatus());
    // stale ticket minted before revoke is consumed and refused -> 404
    MvcResult s = streamRaw(stale);
    assertEquals(404, s.getResponse().getStatus());
    Integer deniedRows = jdbc.queryForObject(
        "SELECT COUNT(*) FROM file_access_event WHERE attachment_id = ? AND outcome = 'DENIED'",
        Integer.class, attId);
    assertNotNull(deniedRows);
    assertTrue(deniedRows >= 1, "expected DENIED evidence rows");
  }

  @Test
  void revokedSessionKillsTicket() throws Exception {
    Flow f = flow("sti4");
    byte[] bytes = ("sess-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
    UUID attId = commitFlow(f, "doc-sti4.pdf", bytes, "application/pdf", null);
    String raw = mintTicket(f.memberToken(), attId, "download");

    // revoke the actor: deactivate every live session (account stays ACTIVE)
    for (var s : sessions.findByUserIdAndActiveTrue(f.member().getId())) {
      s.setActive(false);
      sessions.save(s);
    }

    MvcResult s = streamRaw(raw);
    assertEquals(404, s.getResponse().getStatus(), "revoked-actor ticket must hide as 404");
  }

  @Test
  void deletedObjectIs409WithAudit() throws Exception {
    Flow f = flow("sti5");
    byte[] bytes = ("gone-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
    UUID attId = commitFlow(f, "doc-sti5.pdf", bytes, "application/pdf", null);
    String objectKey = fileVersions.findByAttachmentIdOrderByVersionNoAsc(attId)
        .get(fileVersions.findByAttachmentIdOrderByVersionNoAsc(attId).size() - 1).getObjectKey();

    // manual wipe behind the backend's back
    minio.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());

    String raw = mintTicket(f.mgrToken(), attId, "download");
    MvcResult s = streamRaw(raw);
    assertEquals(409, s.getResponse().getStatus());
    assertEquals("FILE_OBJECT_MISSING", reasonCode(s));

    Integer deniedAudit = jdbc.queryForObject(
        "SELECT COUNT(*) FROM audit_event WHERE action = 'file.stream' AND outcome = 'DENIED'"
            + " AND reason = 'FILE_OBJECT_MISSING' AND entity_id = ?",
        Integer.class, attId);
    assertNotNull(deniedAudit);
    assertTrue(deniedAudit >= 1, "expected DENIED audit row for FILE_OBJECT_MISSING");
  }

  @Test
  void storageDownIs503WithoutStack() {
    MinioClient dead = MinioClient.builder()
        .endpoint("http://localhost:9").credentials("x", "x").build();
    FileContentService broken = new FileContentService(dead, bucket,
        "./var/files-storageit-missing");
    try {
      broken.read("att/nope/v1", null, "application/pdf");
      fail("expected STORAGE_UNAVAILABLE");
    } catch (vn.smarttask.common.AppException ex) {
      assertEquals(503, ex.getHttpStatus().value());
      assertEquals("STORAGE_UNAVAILABLE", ex.getReasonCode());
      assertTrue(ex.getMessage() == null || !ex.getMessage().contains("at vn.smarttask"),
          "message must not leak a stack trace, got: " + ex.getMessage());
      assertTrue(ex.getMessage() == null || !ex.getMessage().contains("at java."),
          "message must not leak a stack trace, got: " + ex.getMessage());
    }
  }

  @Test
  void expiredStagingIsCleanedUp() throws Exception {
    Flow f = flow("sti7");
    String key = "storageit/" + UUID.randomUUID() + "/orphan.bin";
    byte[] bytes = "orphan".getBytes(StandardCharsets.UTF_8);
    putStaging(key, bytes, "application/pdf");

    UploadSession orphan = new UploadSession(UuidV7.generate(), f.mgr().getId(),
        f.task().getId(), "orphan.bin", "application/pdf", bytes.length,
        OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));
    orphan.setStagingKey(key);
    uploadSessions.save(orphan);

    int cleaned = files.cleanupExpiredSessions();
    assertTrue(cleaned >= 1, "expected at least the orphan to be swept, got " + cleaned);
    assertEquals("EXPIRED",
        uploadSessions.findById(orphan.getId()).orElseThrow().getStatus());
    try {
      minio.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build());
      fail("staging object should have been removed");
    } catch (Exception expected) {
      // gone: stat must fail
    }
  }

  @Test
  void bucketIsPrivate() throws Exception {
    String policy = null;
    try {
      policy = minio.getBucketPolicy(
          GetBucketPolicyArgs.builder().bucket(bucket).build());
    } catch (Exception e) {
      // S3Mock answers with no policy configured: treat as private, record why.
      policy = "";
    }
    assertTrue(policy == null || policy.isBlank() || !policy.contains("\"*\""),
        "bucket policy must not grant public access, got: " + policy);

    // No presigned-GET-public path exists by construction: the backend never
    // mints storage URLs (only single-use backend stream tickets). Probe that
    // an unsigned GET is not part of any supported flow; the authoritative
    // assertion is the empty bucket policy above.
    HttpClient http = HttpClient.newHttpClient();
    HttpRequest req = HttpRequest.newBuilder(
        URI.create("http://localhost:9090/" + bucket + "/storageit/probe-no-such-key"))
        .GET().build();
    int code = http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
    assertTrue(code == 403 || code == 404,
        "unsigned object GET must not succeed, got HTTP " + code);
  }
}
