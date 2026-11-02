package vn.smarttask.notify.repo;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.smarttask.notify.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Notification> {
  @org.springframework.data.jpa.repository.Modifying(clearAutomatically=true,flushAutomatically=true)
  @org.springframework.data.jpa.repository.Query("update Notification n set n.readAt=:now,n.read=true where n.userId=:user and n.readAt is null and n.redacted=false and n.deferredUntil is null")
  int markAllRead(@org.springframework.data.repository.query.Param("user") UUID user,@org.springframework.data.repository.query.Param("now") java.time.OffsetDateTime now);
  long countByUserIdAndReadAtIsNullAndRedactedFalse(UUID userId);
  @org.springframework.data.jpa.repository.Query("select count(n) from Notification n where n.userId=:user and n.readAt is null and n.redacted=false and n.deferredUntil is null")
  long visibleUnread(@org.springframework.data.repository.query.Param("user") UUID user);
  List<Notification> findByUserIdOrderByOccurredAtDesc(UUID userId);
  List<Notification> findByUserIdAndScopeTypeAndScopeId(UUID userId, String scopeType, UUID scopeId);
  java.util.Optional<Notification> findByDedupToken(String dedupToken);
}
