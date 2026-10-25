package vn.smarttask.task.repo;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import vn.smarttask.task.entity.TaskTag;

public interface TaskTagRepository extends JpaRepository<TaskTag, UUID> {
  List<TaskTag> findByTaskId(UUID taskId);

  /** F2.5: tag filter as EXISTS (no entity load per task). */
  boolean existsByTaskIdAndTag(UUID taskId, String tag);
}
