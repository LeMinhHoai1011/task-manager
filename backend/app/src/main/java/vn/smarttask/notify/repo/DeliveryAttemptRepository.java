package vn.smarttask.notify.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.smarttask.notify.entity.DeliveryAttempt;

public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, UUID> {
  Optional<DeliveryAttempt> findByDedupToken(String dedupToken);
  List<DeliveryAttempt> findByNotificationIdOrderByAttemptNoAsc(UUID notificationId);
  List<DeliveryAttempt> findByStatus(String status);
}
