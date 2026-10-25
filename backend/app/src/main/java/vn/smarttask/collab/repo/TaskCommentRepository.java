package vn.smarttask.collab.repo;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.smarttask.collab.entity.ActivityEvent;
import vn.smarttask.collab.entity.TaskComment;

public interface TaskCommentRepository extends JpaRepository<TaskComment, UUID> {
  List<TaskComment> findByTaskIdOrderByCreatedAtAsc(UUID taskId);
}
