package vn.smarttask.jobs;

import org.springframework.stereotype.Component;
import vn.smarttask.common.entity.BackgroundJob;
import vn.smarttask.common.service.AuditService;

/**
 * FILE_SCAN -> acknowledged. V1 has no malware engine (scan stays
 * UNSUPPORTED, explicitly NOT malware-protected); the job completes so the
 * queue drains instead of sticking QUEUED forever.
 */
@Component
public class FileScanJobHandler implements JobHandler {
  private final AuditService audit;

  public FileScanJobHandler(AuditService audit) {
    this.audit = audit;
  }

  @Override
  public String kind() { return "FILE_SCAN"; }

  @Override
  public void handle(BackgroundJob job, String owner) {
    audit.append(null, "file.scan.complete", "background_job", job.getId(),
        "ALLOWED", "SCAN_UNSUPPORTED_NO_ENGINE", job.getPayload());
  }
}
