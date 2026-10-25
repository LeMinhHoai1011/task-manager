package vn.smarttask.common.repo;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import vn.smarttask.common.entity.OutboxEvent;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
  List<OutboxEvent> findByPublishedFalse();

  /**
   * F3 relay claim: due PENDING rows, skipping rows claimed by a live owner
   * (crash-safe: stale claims are reclaimed after {@code claimed_until}).
   * No @Lock: the native SQL already carries FOR UPDATE SKIP LOCKED
   * (Hibernate rejects setLockMode on native queries).
   */
  @Query(value = "select * from outbox_event where status = 'PENDING'"
      + " and next_attempt_at <= now()"
      + " and (claimed_until is null or claimed_until <= now())"
      + " order by created_at limit :limit for update skip locked",
      nativeQuery = true)
  List<OutboxEvent> claimDue(int limit);

  List<OutboxEvent> findByStatus(String status);
}
