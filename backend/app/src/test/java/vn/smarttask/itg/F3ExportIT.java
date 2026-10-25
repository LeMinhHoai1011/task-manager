package vn.smarttask.itg;

import static org.junit.jupiter.api.Assertions.*;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import vn.smarttask.common.repo.BackgroundJobRepository;
import vn.smarttask.jobs.JobRunner;
import vn.smarttask.reports.repo.ReportExportJobRepository;
import vn.smarttask.reports.service.ExportWorker;
import vn.smarttask.reports.service.ReportService;

/**
 * F3 ExportIT (real PostgreSQL + S3Mock): real CSV/XLSX/PDF bytes, scope
 * filtering (no beyond-permission rows), requester-only download + recheck,
 * TTL cleanup. Schedulers stay off; the JobRunner is driven directly.
 */
@TestPropertySource(properties = {
    "smarttask.workers.enabled=false",
    "file.gateway.minio-endpoint=http://localhost:9090",
    "file.gateway.minio-access-key=test",
    "file.gateway.minio-secret-key=test",
    "file.gateway.minio-bucket=smarttask-test"})
class F3ExportIT extends ItgBase {

  @Autowired ReportService reports;
  @Autowired ExportWorker exports;
  @Autowired JobRunner runner;
  @Autowired ReportExportJobRepository exportJobs;
  @Autowired BackgroundJobRepository bgJobs;
  @Autowired MinioClient minio;

  private static final String BUCKET = "smarttask-test";

  /** S3Mock's initialBuckets env is not always honoured: create it idempotently. */
  @org.junit.jupiter.api.BeforeEach
  void ensureBucket() throws Exception {
    if (!minio.bucketExists(io.minio.BucketExistsArgs.builder().bucket(BUCKET).build())) {
      minio.makeBucket(io.minio.MakeBucketArgs.builder().bucket(BUCKET).build());
    }
  }

  record Flow(UUID mgr, UUID member, UUID project, UUID task) {}

  private Flow flow(String prefix) {
    var mgr = activeUser(prefix + "mgr", "Secret123!");
    var member = activeUser(prefix + "mbr", "Secret123!");
    UUID ws = mkWorkspace(mgr.getId(), prefix);
    var p = mkActiveProject(mgr.getId(), ws, prefix);
    addWsMember(ws, member.getId(), "MEMBER");
    addProjectMember(mgr.getId(), p.getId(), member.getId(), "MEMBER");
    var t = mkTask(member.getId(), p.getId(), prefix + "-task-" + tag());
    return new Flow(mgr.getId(), member.getId(), p.getId(), t.getId());
  }

  private String period() {
    return tag();
  }

  private byte[] storedBytes(String key) throws Exception {
    try (var in = minio.getObject(GetObjectArgs.builder()
        .bucket("smarttask-test").object(key).build())) {
      return in.readAllBytes();
    }
  }

  /**
   * The scratch DB is shared across IT classes: run worker passes until OUR
   * export reaches the expected status. Backoff-aware (sleeps until the
   * background job is due) and fails fast on unexpected terminal states.
   */
  private vn.smarttask.reports.entity.ReportExportJob runUntilExport(UUID id, String status) {
    vn.smarttask.reports.entity.ReportExportJob row = exportJobs.findById(id).orElseThrow();
    long deadline = System.currentTimeMillis() + 120000;
    while (System.currentTimeMillis() < deadline) {
      runner.processBatch("w-exp-loop");
      row = exportJobs.findById(id).orElseThrow();
      if (status.equals(row.getStatus())) return row;
      if (("FAILED".equals(row.getStatus()) || "CANCELLED_REVOKED".equals(row.getStatus())
          || "EXPIRED".equals(row.getStatus())) && !status.equals(row.getStatus())) return row;
      var bg = row.getBackgroundJobId() == null ? null
          : bgJobs.findById(row.getBackgroundJobId()).orElse(null);
      if (bg != null && "FAILED".equals(bg.getStatus()) && bg.getNextAttemptAt() != null) {
        long wait = bg.getNextAttemptAt().toInstant().toEpochMilli() - System.currentTimeMillis();
        sleepQuiet(Math.min(Math.max(wait, 0), 3000));
      } else {
        sleepQuiet(300);
      }
    }
    return row;
  }

  private static void sleepQuiet(long ms) {
    try {
      Thread.sleep(Math.max(ms, 50));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Test
  void csvExportHasRealRows() throws Exception {
    Flow f = flow("f3e1");
    var job = reports.requestExport(f.member(), "PROJECT_PROGRESS", "PROJECT", f.project(),
        LocalDate.now().minusDays(7), LocalDate.now(), "CSV", "f3e-csv-" + period());
    assertEquals("QUEUED", job.getStatus());

    var done = runUntilExport(job.getId(), "GENERATED");
    assertEquals("GENERATED", done.getStatus());
    assertNotNull(done.getObjectKey());
    assertTrue(done.getFileSizeBytes() > 0);
    String csv = new String(storedBytes(done.getObjectKey()), StandardCharsets.UTF_8);
    assertTrue(csv.contains("task_id,title,state,priority"));
    assertTrue(csv.contains("f3e1-task-"), "expected the member's task row in CSV");
    assertTrue(done.getRowCount() >= 1);
  }

  @Test
  void xlsxAndPdfAreRealFiles() throws Exception {
    Flow f = flow("f3e2");
    var xlsx = reports.requestExport(f.member(), "PROJECT_PROGRESS", "PROJECT", f.project(),
        LocalDate.now().minusDays(7), LocalDate.now(), "XLSX", "f3e-xlsx-" + period());
    var pdf = reports.requestExport(f.member(), "PROJECT_PROGRESS", "PROJECT", f.project(),
        LocalDate.now().minusDays(7), LocalDate.now(), "PDF", "f3e-pdf-" + period());
    runner.processBatch("w-exp2");

    var xrow = runUntilExport(xlsx.getId(), "GENERATED");
    assertEquals("GENERATED", xrow.getStatus());
    try (var wb = new XSSFWorkbook(new ByteArrayInputStream(storedBytes(xrow.getObjectKey())))) {
      assertNotNull(wb.getSheet("report"));
      assertEquals("task_id", wb.getSheet("report").getRow(1).getCell(0).getStringCellValue());
    }
    var prow = runUntilExport(pdf.getId(), "GENERATED");
    assertEquals("GENERATED", prow.getStatus());
    byte[] pdfBytes = storedBytes(prow.getObjectKey());
    assertEquals("%PDF", new String(pdfBytes, 0, 4, StandardCharsets.US_ASCII));
  }

  @Test
  void workspaceScopeExcludesUnseenProject() throws Exception {
    var mgr = activeUser("f3e3mgr", "Secret123!");
    var member = activeUser("f3e3mbr", "Secret123!");
    UUID ws = mkWorkspace(mgr.getId(), "f3e3");
    var seen = mkActiveProject(mgr.getId(), ws, "f3e3seen");
    var hidden = mkActiveProject(mgr.getId(), ws, "f3e3hidden");
    addWsMember(ws, member.getId(), "MEMBER");
    addProjectMember(mgr.getId(), seen.getId(), member.getId(), "MEMBER");
    var seenTask = mkTask(member.getId(), seen.getId(), "f3e3-visible-" + tag());
    var hiddenTask = mkTask(mgr.getId(), hidden.getId(), "f3e3-hidden-" + tag());

    var job = reports.requestExport(member.getId(), "WORKSPACE_OVERVIEW", "WORKSPACE", ws,
        LocalDate.now().minusDays(7), LocalDate.now(), "CSV", "f3e-ws-" + period());
    runner.processBatch("w-exp3");

    var done = runUntilExport(job.getId(), "GENERATED");
    assertEquals("GENERATED", done.getStatus());
    String csv = new String(storedBytes(done.getObjectKey()), StandardCharsets.UTF_8);
    assertTrue(csv.contains(seenTask.getId().toString()), "member project rows must be present");
    assertFalse(csv.contains(hiddenTask.getId().toString()),
        "rows beyond permission must never be exported");
  }

  @Test
  void downloadIsRequesterOnlyWithRecheck() throws Exception {
    Flow f = flow("f3e4");
    var outsider = activeUser("f3e4out", "Secret123!");
    var job = reports.requestExport(f.member(), "PROJECT_PROGRESS", "PROJECT", f.project(),
        LocalDate.now().minusDays(7), LocalDate.now(), "CSV", "f3e-dl-" + period());
    runUntilExport(job.getId(), "GENERATED");

    String mgrToken = loginToken(
        users.findById(f.mgr()).orElseThrow().getUsername(), "Secret123!");
    String memberToken = loginToken(
        users.findById(f.member()).orElseThrow().getUsername(), "Secret123!");
    String outToken = loginToken(outsider.getUsername(), "Secret123!");

    var ok = doGet("/api/v1/export-jobs/" + job.getId() + "/download", memberToken);
    assertEquals(200, ok.getResponse().getStatus());
    assertTrue(ok.getResponse().getContentAsByteArray().length > 0);
    assertTrue(ok.getResponse().getContentType().contains("text/csv"));

    var denied = doGet("/api/v1/export-jobs/" + job.getId() + "/download", outToken);
    assertEquals(404, denied.getResponse().getStatus());

    // revoke between generation and download -> 404 (recheck at delivery)
    projects.removeMember(f.mgr(), f.project(), f.member());
    var revoked = doGet("/api/v1/export-jobs/" + job.getId() + "/download", memberToken);
    assertEquals(404, revoked.getResponse().getStatus());
    // mgr token unused except to prove login still works for the manager
    assertNotNull(mgrToken);
  }

  @Test
  void idempotentRequestReturnsSameJob() {
    Flow f = flow("f3e5");
    String key = "f3e-idem-" + period();
    var a = reports.requestExport(f.member(), "PROJECT_PROGRESS", "PROJECT", f.project(),
        LocalDate.now().minusDays(7), LocalDate.now(), "CSV", key);
    var b = reports.requestExport(f.member(), "PROJECT_PROGRESS", "PROJECT", f.project(),
        LocalDate.now().minusDays(7), LocalDate.now(), "CSV", key);
    assertEquals(a.getId(), b.getId());
  }

  @Test
  void ttlExpiryCleansUpObjects() throws Exception {
    Flow f = flow("f3e6");
    var job = reports.requestExport(f.member(), "PROJECT_PROGRESS", "PROJECT", f.project(),
        LocalDate.now().minusDays(7), LocalDate.now(), "CSV", "f3e-ttl-" + period());
    runner.processBatch("w-exp6");
    var done = runUntilExport(job.getId(), "GENERATED");
    assertEquals("GENERATED", done.getStatus());
    String key = done.getObjectKey();

    // age past TTL, sweep
    var fresh = exportJobs.findById(job.getId()).orElseThrow();
    fresh.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));
    exportJobs.save(fresh);
    assertTrue(exports.cleanupExpired() >= 1);

    assertEquals("EXPIRED",
        exportJobs.findById(job.getId()).orElseThrow().getStatus());
    try {
      minio.statObject(StatObjectArgs.builder().bucket("smarttask-test").object(key).build());
      fail("expired export object must be deleted");
    } catch (Exception expected) {
      // gone
    }
    // expired download answers 410
    String memberToken = loginToken(
        users.findById(f.member()).orElseThrow().getUsername(), "Secret123!");
    var gone = doGet("/api/v1/export-jobs/" + job.getId() + "/download", memberToken);
    assertEquals(410, gone.getResponse().getStatus());
  }

  @Test
  void bannedMetricExportIsForbidden() {
    Flow f = flow("f3e7");
    var ex = assertThrows(vn.smarttask.common.AppException.class, () -> reports.requestExport(
        f.member(), "PRODUCTIVITY_RANKING_INDIVIDUAL", "PROJECT", f.project(),
        LocalDate.now().minusDays(7), LocalDate.now(), "CSV", "f3e-ban-" + period()));
    assertEquals(403, ex.getHttpStatus().value());
  }
}
