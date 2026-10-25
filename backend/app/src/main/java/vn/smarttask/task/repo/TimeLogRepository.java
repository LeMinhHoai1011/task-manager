package vn.smarttask.task.repo;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.smarttask.task.entity.TimeLog;

public interface TimeLogRepository extends JpaRepository<TimeLog, UUID> {
  List<TimeLog> findByTaskId(UUID taskId);
}
