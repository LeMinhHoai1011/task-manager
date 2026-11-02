package vn.smarttask.calendar.repo;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.smarttask.calendar.entity.ReminderRule;

public interface ReminderRuleRepository extends JpaRepository<ReminderRule, UUID> {
  List<ReminderRule> findByUserId(UUID userId);
  org.springframework.data.domain.Page<ReminderRule> findByUserId(UUID userId,org.springframework.data.domain.Pageable page);
  List<ReminderRule> findByTaskId(UUID taskId);
}
