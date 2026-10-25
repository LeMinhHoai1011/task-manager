package vn.smarttask.reports.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.reports.entity.ReportExportJob;
import vn.smarttask.reports.repo.ReportExportJobRepository;

/** F3 export transactional steps (each its own short tx; bytes live outside txs). */
@Service
public class ExportWorkerTx {
  private final ReportExportJobRepository exports;
  private final AuditService audit;

  public ExportWorkerTx(ReportExportJobRepository exports, AuditService audit) {
    this.exports = exports; this.audit = audit;
  }

  @Transactional
  public ReportExportJob markRunning(UUID jobId) {
    ReportExportJob job = require(jobId);
    if (!"QUEUED".equals(job.getStatus())) return job;
    job.setStatus("RUNNING");
    exports.save(job);
    audit.append(null, "report.export.running", "report", jobId, "ALLOWED", null, "{}");
    return job;
  }

  @Transactional
  public ReportExportJob markStored(UUID jobId, String objectKey, long size, long rows) {
    ReportExportJob job = require(jobId);
    job.setObjectKey(objectKey);
    job.setFileSizeBytes(size);
    job.setRowCount(rows);
    exports.save(job);
    return job;
  }

  @Transactional
  public ReportExportJob markFailed(UUID jobId, String errorCode) {
    ReportExportJob job = require(jobId);
    job.setStatus("FAILED");
    job.setErrorCode(errorCode);
    exports.save(job);
    audit.append(null, "report.export.fail", "report", jobId, "ALLOWED", errorCode, "{}");
    return job;
  }

  @Transactional
  public ReportExportJob markRevoked(UUID jobId) {
    ReportExportJob job = require(jobId);
    job.setStatus("CANCELLED_REVOKED");
    exports.save(job);
    audit.appendDenied(job.getRequestedBy(), "report.export.deliver", "report", jobId,
        "PERMISSION_REVOKED_AT_DELIVERY");
    return job;
  }

  /**
   * TTL sweep: GENERATED/DELIVERED past expires_at -> EXPIRED (paged).
   * Object deletion happens outside this tx (see ExportWorker).
   */
  @Transactional
  public List<ReportExportJob> sweepExpiredIds(int limit) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    List<ReportExportJob> doomed = exports
        .findByStatusInAndExpiresAtBefore(List.of("GENERATED", "DELIVERED"), now)
        .stream().limit(limit).toList();
    for (ReportExportJob j : doomed) {
      ReportExportJob fresh = require(j.getId());
      fresh.setStatus("EXPIRED");
      exports.save(fresh);
      audit.append(null, "report.export.expire", "report", j.getId(), "ALLOWED", "TTL_24H", "{}");
    }
    return doomed;
  }

  private ReportExportJob require(UUID jobId) {
    return exports.findById(jobId)
        .orElseThrow(() -> vn.smarttask.common.AppException.notFound("Export job not found"));
  }
}
