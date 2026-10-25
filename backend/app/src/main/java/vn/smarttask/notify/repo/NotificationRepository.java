package vn.smarttask.notify.repo;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.smarttask.notify.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
  List<Notification> findByUserIdOrderByOccurredAtDesc(UUID userId);
  List<Notification> findByUserIdAndScopeTypeAndScopeId(UUID userId, String scopeType, UUID scopeId);
  java.util.Optional<Notification> findByDedupToken(String dedupToken);
}
