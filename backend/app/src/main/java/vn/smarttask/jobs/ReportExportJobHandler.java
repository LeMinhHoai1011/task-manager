package vn.smarttask.jobs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.smarttask.common.AppException;
import vn.smarttask.common.entity.BackgroundJob;
import vn.smarttask.reports.service.ExportWorker;

/** REPORT_EXPORT -> real CSV/XLSX/PDF generation (ExportWorker). */
@Component
public class ReportExportJobHandler implements JobHandler {
  private final ExportWorker exports;
  private final ObjectMapper om;

  public ReportExportJobHandler(ExportWorker exports, ObjectMapper om) {
    this.exports = exports; this.om = om;
  }

  @Override
  public String kind() { return "REPORT_EXPORT"; }

  @Override
  public void handle(BackgroundJob job, String owner) {
    JsonNode payload = payloadOf(job);
    if (!payload.hasNonNull("export_job_id")) {
      throw AppException.badRequest("EXPORT_JOB_ID_MISSING", "REPORT_EXPORT needs export_job_id");
    }
    exports.process(UUID.fromString(payload.get("export_job_id").asText()));
  }

  private JsonNode payloadOf(BackgroundJob job) {
    try {
      return om.readTree(job.getPayload() == null ? "{}" : job.getPayload());
    } catch (Exception e) {
      return om.createObjectNode();
    }
  }
}
