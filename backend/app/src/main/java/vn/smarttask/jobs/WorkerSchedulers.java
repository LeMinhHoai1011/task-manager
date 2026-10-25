package vn.smarttask.jobs;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.smarttask.common.service.JobService;
import vn.smarttask.reports.service.ExportWorker;

/**
 * F3 worker schedules. Disabled with {@code smarttask.workers.enabled=false}
 * (integration tests drive the workers directly instead).
 */
@Component
@ConditionalOnProperty(name = "smarttask.workers.enabled", havingValue = "true",
    matchIfMissing = true)
public class WorkerSchedulers {
  private final OutboxRelay outboxRelay;
  private final JobRunner jobRunner;
  private final JobService jobs;
  private final ExportWorker exports;

  public WorkerSchedulers(OutboxRelay outboxRelay, JobRunner jobRunner, JobService jobs,
      ExportWorker exports) {
    this.outboxRelay = outboxRelay; this.jobRunner = jobRunner; this.jobs = jobs;
    this.exports = exports;
  }

  /** Crash recovery on boot: re-queue RUNNING rows whose lease died with the process. */
  @EventListener(ApplicationReadyEvent.class)
  public void reclaimOnStartup() {
    jobs.reclaimExpiredLeases(200, "startup");
  }

  @Scheduled(fixedDelay = 2000)
  public void relayOutbox() {
    outboxRelay.processBatch();
  }

  @Scheduled(fixedDelay = 2000)
  public void runJobs() {
    jobRunner.processBatch();
  }

  @Scheduled(fixedDelay = 3600000)
  public void expireExports() {
    exports.cleanupExpired();
  }
}
