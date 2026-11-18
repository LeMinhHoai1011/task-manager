package vn.smarttask.calendar.repo;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.smarttask.calendar.entity.ReminderRule;

public interface ReminderRuleRepository extends JpaRepository<ReminderRule, UUID> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select r from ReminderRule r where r.id = :id")
  java.util.Optional<ReminderRule> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select r from ReminderRule r where r.userId = :userId order by r.id")
  List<ReminderRule> findByUserIdForUpdate(@org.springframework.data.repository.query.Param("userId") UUID userId);
  List<ReminderRule> findByUserId(UUID userId);
  org.springframework.data.domain.Page<ReminderRule> findByUserId(UUID userId,org.springframework.data.domain.Pageable page);
  List<ReminderRule> findByTaskId(UUID taskId);
}
