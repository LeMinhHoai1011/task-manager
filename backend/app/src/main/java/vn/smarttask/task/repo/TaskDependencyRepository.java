package vn.smarttask.task.repo;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.smarttask.task.entity.TaskDependency;

public interface TaskDependencyRepository extends JpaRepository<TaskDependency, UUID> {
  List<TaskDependency> findByTaskId(UUID taskId);

  @Query(value = "WITH RECURSIVE reach(n) AS ("
      + " SELECT depends_on_task_id FROM task_dependency WHERE task_id = :to"
      + " UNION SELECT d.depends_on_task_id FROM task_dependency d JOIN reach r ON d.task_id = r.n)"
      + " SELECT count(*) > 0 FROM reach WHERE n = :from", nativeQuery = true)
  boolean hasPath(@Param("from") UUID from, @Param("to") UUID to);
}
