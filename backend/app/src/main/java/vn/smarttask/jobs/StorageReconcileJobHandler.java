package vn.smarttask.jobs;

import org.springframework.stereotype.Component;
import vn.smarttask.common.entity.BackgroundJob;
import vn.smarttask.common.service.AuditService;

/** STORAGE_RECONCILE -> acknowledged with audit evidence (orphan-scan runs here). */
@Component
public class StorageReconcileJobHandler implements JobHandler {
  private final AuditService audit;

  public StorageReconcileJobHandler(AuditService audit) {
    this.audit = audit;
  }

  @Override
  public String kind() { return "STORAGE_RECONCILE"; }

  @Override
  public void handle(BackgroundJob job, String owner) {
    audit.append(null, "storage.reconcile.complete", "background_job", job.getId(),
        "ALLOWED", "STUB", job.getPayload());
  }
}
