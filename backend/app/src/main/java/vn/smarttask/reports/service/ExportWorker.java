package vn.smarttask.reports.service;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.smarttask.common.AppException;
import vn.smarttask.jobs.JobRevokedException;
import vn.smarttask.project.repo.ProjectMembershipRepository;
import vn.smarttask.project.repo.ProjectRepository;
import vn.smarttask.reports.MetricDictionary;
import vn.smarttask.reports.entity.ReportExportJob;
import vn.smarttask.reports.repo.ReportExportJobRepository;
import vn.smarttask.task.repo.TaskAssignmentRepository;
import vn.smarttask.task.repo.TaskRepository;

/**
 * F3 export worker: generates REAL CSV/XLSX/PDF bytes (POI + PDFBox), scoped
 * to what the requester may see (membership rechecked at processing time),
 * stored under a private object key (never a public URL). No tx is held
 * while generating bytes or talking to storage.
 */
@Service
public class ExportWorker {
  static final List<String> HEADER =
      List.of("task_id", "title", "state", "priority", "due_at", "assignees", "project_id");

  private final ReportExportJobRepository exports;
  private final ReportService reports;
  private final ExportWorkerTx tx;
  private final TaskRepository tasks;
  private final TaskAssignmentRepository assignments;
  private final ProjectMembershipRepository projectMembers;
  private final ProjectRepository projects;
  private final MinioClient minio;
  private final String bucket;
  private final Path localRoot;

  public ExportWorker(ReportExportJobRepository exports, ReportService reports,
      ExportWorkerTx tx, TaskRepository tasks, TaskAssignmentRepository assignments,
      ProjectMembershipRepository projectMembers,
      ProjectRepository projects, MinioClient minio,
      @Value("${file.gateway.minio-bucket:smarttask}") String bucket,
      @Value("${file.gateway.local-root:./var/files}") String localRoot) {
    this.exports = exports; this.reports = reports; this.tx = tx; this.tasks = tasks;
    this.assignments = assignments; this.projectMembers = projectMembers;
    this.projects = projects; this.minio = minio; this.bucket = bucket;
    this.localRoot = Paths.get(localRoot).toAbsolutePath().normalize();
  }

  public record ExportResult(UUID exportJobId, String status, long rows, long bytes) {}

  /**
   * Process one export job. Revoked scope between request and processing ->
   * CANCELLED_REVOKED + {@link JobRevokedException} (the JobRunner turns the
   * background job CANCELLED). Idempotent re-drive: non-QUEUED jobs return
   * their current status without regenerating.
   */
  public ExportResult process(UUID exportJobId) {
    ReportExportJob job = exports.findById(exportJobId)
        .orElseThrow(() -> AppException.notFound("Export job not found"));
    if (!"QUEUED".equals(job.getStatus()) && !"RUNNING".equals(job.getStatus())) {
      return new ExportResult(job.getId(), job.getStatus(),
          job.getRowCount() == null ? 0 : job.getRowCount(),
          job.getFileSizeBytes() == null ? 0 : job.getFileSizeBytes());
    }
    if (MetricDictionary.isBanned(job.getReportCode())) {
      tx.markFailed(exportJobId, "EXPORT_BANNED");
      throw AppException.forbidden("Individual ranking exports are forbidden (R-07)");
    }
    tx.markRunning(exportJobId);

    // Recheck #2 at processing time (check #1 ran at request).
    if (!hasScopeAccess(job.getRequestedBy(), job.getScopeType(), job.getScopeId())) {
      tx.markRevoked(exportJobId);
      throw new JobRevokedException("CANCELLED_REVOKED");
    }

    List<List<String>> rows = buildRows(job);
    byte[] bytes = render(job.getFormat(), job.getReportCode(), rows);
    String key = "exports/" + job.getId() + "/report." + ext(job.getFormat());
    storeBytes(key, bytes);

    tx.markStored(exportJobId, key, bytes.length, rows.size());
    // Delivery recheck #3 inside completeExport; revoked meanwhile -> CANCELLED_REVOKED.
    ReportExportJob done = reports.completeExport(exportJobId, rows.size());
    if ("CANCELLED_REVOKED".equals(done.getStatus())) {
      deleteQuietly(key);
      throw new JobRevokedException("CANCELLED_REVOKED");
    }
    return new ExportResult(done.getId(), done.getStatus(), rows.size(), bytes.length);
  }

  /** TTL sweep: EXPIRE due rows + delete their objects (best-effort). Returns expired count. */
  public int cleanupExpired() {
    var doomed = tx.sweepExpiredIds(200);
    for (ReportExportJob j : doomed) {
      if (j.getObjectKey() != null) deleteQuietly(j.getObjectKey());
    }
    return doomed.size();
  }

  // ---------------------------------------------------------------- scope

  boolean hasScopeAccess(UUID actor, String scopeType, UUID scopeId) {
    if ("PROJECT".equals(scopeType) && scopeId != null) {
      return projectMembers.findMembership(scopeId, actor).isPresent();
    }
    if ("PERSONAL".equals(scopeType)) {
      return scopeId == null || scopeId.equals(actor);
    }
    // WORKSPACE/SYSTEM narrow later; generation only includes member projects.
    return true;
  }

  /** Rows strictly inside the requester's visible scope (never beyond permission). */
  List<List<String>> buildRows(ReportExportJob job) {
    List<UUID> projectIds = visibleProjects(job);
    List<List<String>> rows = new ArrayList<>();
    for (UUID pid : projectIds) {
      for (var t : tasks.findByProjectId(pid)) {
        if (t.getDeletedAt() != null) continue;
        if (job.getPeriodFrom() != null && t.getCreatedAt() != null
            && t.getCreatedAt().toLocalDate().isBefore(job.getPeriodFrom())) continue;
        if (job.getPeriodTo() != null && t.getCreatedAt() != null
            && t.getCreatedAt().toLocalDate().isAfter(job.getPeriodTo())) continue;
        long assignees = assignments.findByTaskIdAndStatus(t.getId(), "ACTIVE").size();
        rows.add(List.of(t.getId().toString(), safe(t.getTitle()),
            safe(t.getState()), safe(t.getPriority()),
            t.getDueAt() == null ? "" : t.getDueAt().toString(),
            String.valueOf(assignees), pid.toString()));
      }
    }
    return rows;
  }

  private List<UUID> visibleProjects(ReportExportJob job) {
    UUID actor = job.getRequestedBy();
    if ("PROJECT".equals(job.getScopeType()) && job.getScopeId() != null) {
      return List.of(job.getScopeId());
    }
    if ("PERSONAL".equals(job.getScopeType())) {
      List<UUID> ids = new ArrayList<>(assignments.findActiveTaskIdsByUser(actor));
      List<UUID> out = new ArrayList<>();
      for (var t : tasks.findByCreatedByAndDeletedAtIsNull(actor)) out.add(t.getProjectId());
      if (!ids.isEmpty()) {
        for (var t : tasks.findAllById(ids)) {
          if (t.getDeletedAt() == null && !out.contains(t.getProjectId())) out.add(t.getProjectId());
        }
      }
      return out.stream().filter(pid -> projectMembers.findMembership(pid, actor).isPresent())
          .distinct().toList();
    }
    // WORKSPACE/SYSTEM: member projects only (workspace filter when scoped).
    List<UUID> member = new ArrayList<>(projectMembers.findActiveProjectIdsByUser(actor));
    if ("WORKSPACE".equals(job.getScopeType()) && job.getScopeId() != null) {
      UUID ws = job.getScopeId();
      member.removeIf(pid -> projects.findById(pid)
          .map(p -> !ws.equals(p.getWorkspaceId())).orElse(true));
    }
    return member;
  }

  // ---------------------------------------------------------------- render

  public static String ext(String format) {
    return switch (format) {
      case "PDF" -> "pdf";
      case "XLSX" -> "xlsx";
      default -> "csv";
    };
  }

  public static String contentType(String format) {
    return switch (format) {
      case "PDF" -> "application/pdf";
      case "XLSX" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
      default -> "text/csv";
    };
  }

  byte[] render(String format, String reportCode, List<List<String>> rows) {
    return switch (format) {
      case "XLSX" -> renderXlsx(reportCode, rows);
      case "PDF" -> renderPdf(reportCode, rows);
      default -> renderCsv(reportCode, rows);
    };
  }

  public static byte[] renderCsv(String reportCode, List<List<String>> rows) {
    StringBuilder sb = new StringBuilder();
    sb.append("# report=").append(reportCode).append("\n");
    sb.append(String.join(",", HEADER)).append("\n");
    for (List<String> r : rows) {
      List<String> cells = new ArrayList<>();
      for (String c : r) cells.add(csvCell(c));
      sb.append(String.join(",", cells)).append("\n");
    }
    return sb.toString().getBytes(StandardCharsets.UTF_8);
  }

  private static String csvCell(String c) {
    String v = c == null ? "" : c;
    if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
      return "\"" + v.replace("\"", "\"\"") + "\"";
    }
    return v;
  }

  public static byte[] renderXlsx(String reportCode, List<List<String>> rows) {
    try (Workbook wb = new XSSFWorkbook();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
      Sheet sheet = wb.createSheet("report");
      Row title = sheet.createRow(0);
      title.createCell(0).setCellValue("report=" + reportCode);
      Row head = sheet.createRow(1);
      for (int i = 0; i < HEADER.size(); i++) head.createCell(i).setCellValue(HEADER.get(i));
      for (int r = 0; r < rows.size(); r++) {
        Row row = sheet.createRow(r + 2);
        List<String> cells = rows.get(r);
        for (int c = 0; c < cells.size(); c++) row.createCell(c).setCellValue(cells.get(c));
      }
      wb.write(out);
      return out.toByteArray();
    } catch (Exception e) {
      throw AppException.storageUnavailable("EXPORT_RENDER_FAILED", "XLSX render failed");
    }
  }

  public static byte[] renderPdf(String reportCode, List<List<String>> rows) {
    try (PDDocument doc = new PDDocument();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
      PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
      PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
      PDPage page = new PDPage();
      doc.addPage(page);
      PDPageContentStream cs = new PDPageContentStream(doc, page);
      float y = 750f;
      cs.beginText();
      cs.setFont(bold, 12);
      cs.newLineAtOffset(50, y);
      cs.showText("report=" + reportCode + " rows=" + rows.size());
      cs.endText();
      y -= 20;
      cs.beginText();
      cs.setFont(font, 8);
      cs.newLineAtOffset(50, y);
      List<String> lines = new ArrayList<>();
      lines.add(String.join(" | ", HEADER));
      for (List<String> r : rows) {
        lines.add((r.size() > 1 ? r.get(0).substring(0, 8) + " " + r.get(1) : String.join(" ", r))
            + " | " + (r.size() > 2 ? r.get(2) : "") + " | " + (r.size() > 3 ? r.get(3) : ""));
      }
      for (String line : lines) {
        if (y < 60) {
          cs.endText();
          cs.close();
          page = new PDPage();
          doc.addPage(page);
          cs = new PDPageContentStream(doc, page);
          y = 750f;
          cs.beginText();
          cs.setFont(font, 8);
          cs.newLineAtOffset(50, y);
        }
        cs.showText(ascii(line));
        cs.newLineAtOffset(0, -12);
        y -= 12;
      }
      cs.endText();
      cs.close();
      doc.save(out);
      return out.toByteArray();
    } catch (Exception e) {
      throw AppException.storageUnavailable("EXPORT_RENDER_FAILED", "PDF render failed");
    }
  }

  /** PDFBox standard-14 fonts are WinAnsi: fold to ASCII (never crash on i18n titles). */
  private static String ascii(String s) {
    StringBuilder sb = new StringBuilder();
    for (char c : s.toCharArray()) sb.append(c > 127 ? '?' : c);
    String v = sb.toString();
    return v.length() > 150 ? v.substring(0, 150) : v;
  }

  private static String safe(String s) {
    return s == null ? "" : s;
  }

  // ---------------------------------------------------------------- storage

  private void storeBytes(String key, byte[] bytes) {
    try {
      minio.putObject(PutObjectArgs.builder().bucket(bucket).object(key)
          .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
          .contentType(contentType(key.endsWith(".pdf") ? "PDF"
              : key.endsWith(".xlsx") ? "XLSX" : "CSV"))
          .build());
      return;
    } catch (Exception e) {
      // fall through to the private local root (download reads either)
    }
    try {
      Path local = localRoot.resolve(key).normalize();
      if (!local.startsWith(localRoot)) {
        throw AppException.storageUnavailable("EXPORT_STORE_FAILED", "Bad export key");
      }
      Files.createDirectories(local.getParent());
      Files.write(local, bytes);
    } catch (Exception e) {
      throw AppException.storageUnavailable("EXPORT_STORE_FAILED", "Export store failed");
    }
  }

  private void deleteQuietly(String key) {
    try {
      minio.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
    } catch (Exception ignored) {
      // best-effort
    }
    try {
      Path local = localRoot.resolve(key).normalize();
      if (local.startsWith(localRoot)) Files.deleteIfExists(local);
    } catch (Exception ignored) {
      // best-effort
    }
  }
}
