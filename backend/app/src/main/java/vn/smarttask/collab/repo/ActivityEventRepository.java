package vn.smarttask.collab.repo;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.smarttask.collab.entity.ActivityEvent;

public interface ActivityEventRepository extends JpaRepository<ActivityEvent, UUID> {
  List<ActivityEvent> findByTaskIdOrderByCreatedAtAsc(UUID taskId);
}
