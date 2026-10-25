package vn.smarttask.workflow.repo;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.smarttask.workflow.entity.WorkflowEvent;

public interface WorkflowEventRepository extends JpaRepository<WorkflowEvent, UUID> {
  List<WorkflowEvent> findByTaskIdOrderByCreatedAtAsc(UUID taskId);
}
