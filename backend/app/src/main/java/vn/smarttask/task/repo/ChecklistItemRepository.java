package vn.smarttask.task.repo;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.smarttask.task.entity.ChecklistItem;
import vn.smarttask.task.entity.TaskTag;
import vn.smarttask.task.entity.TimeLog;

public interface ChecklistItemRepository extends JpaRepository<ChecklistItem, UUID> {
  List<ChecklistItem> findByTaskIdOrderByPositionAsc(UUID taskId);
}
