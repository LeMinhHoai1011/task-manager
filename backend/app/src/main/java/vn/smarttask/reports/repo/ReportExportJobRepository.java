package vn.smarttask.reports.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.smarttask.reports.entity.ReportExportJob;

public interface ReportExportJobRepository extends JpaRepository<ReportExportJob, UUID> {
  Optional<ReportExportJob> findByIdempotencyKey(String key);

  /** F2.5: requester's jobs newest-first (no full-table scan + memory filter). */
  List<ReportExportJob> findByRequestedByOrderByCreatedAtDesc(UUID requestedBy);

  /** F3 TTL sweep: generated/delivered rows past expiry. */
  List<ReportExportJob> findByStatusInAndExpiresAtBefore(
      List<String> statuses, java.time.OffsetDateTime now);
}
