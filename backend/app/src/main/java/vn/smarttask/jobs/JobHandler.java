package vn.smarttask.jobs;

import vn.smarttask.common.entity.BackgroundJob;

/**
 * F3 job-kind handler. Implementations do the real work (no tx held by the
 * runner; each handler manages its own short transactions). Success = return;
 * revoked scope = {@link JobRevokedException}; domain failure =
 * {@link vn.smarttask.common.AppException} (transient statuses retry);
 * anything else = crash (retryable).
 */
public interface JobHandler {
  String kind();

  void handle(BackgroundJob job, String owner);
}
