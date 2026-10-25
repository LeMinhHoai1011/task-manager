package vn.smarttask.common.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import vn.smarttask.common.entity.BackgroundJob;

public interface BackgroundJobRepository extends JpaRepository<BackgroundJob, UUID> {
  Optional<BackgroundJob> findByIdempotencyKey(String key);

  /**
   * Legacy lease stub (kept for compat): QUEUED rows only.
   * Prefer {@link #claimDue(int)} + {@link JobService} lease handling.
   * No @Lock: the native SQL already carries FOR UPDATE SKIP LOCKED
   * (Hibernate rejects setLockMode on native queries).
   */
  @Query(value = "select * from background_job where status = 'QUEUED' order by created_at limit :limit for update skip locked",
      nativeQuery = true)
  List<BackgroundJob> leaseQueued(int limit);

  /**
   * F3 worker claim: QUEUED rows plus due FAILED retries (attempts below max,
   * backoff elapsed), skipping rows leased by a live owner.
   */
  @Query(value = "select * from background_job"
      + " where ((status = 'QUEUED')"
      + " or (status = 'FAILED' and attempts < max_attempts and next_attempt_at <= now()))"
      + " and (lease_expires_at is null or lease_expires_at <= now())"
      + " order by created_at limit :limit for update skip locked",
      nativeQuery = true)
  List<BackgroundJob> claimDue(int limit);

  /** Crash recovery: RUNNING rows whose lease expired (owner died mid-job). */
  @Query(value = "select * from background_job where status = 'RUNNING'"
      + " and lease_expires_at is not null and lease_expires_at <= now()"
      + " order by created_at limit :limit for update skip locked",
      nativeQuery = true)
  List<BackgroundJob> claimStaleRunning(int limit);

  List<BackgroundJob> findByStatus(String status);
}
