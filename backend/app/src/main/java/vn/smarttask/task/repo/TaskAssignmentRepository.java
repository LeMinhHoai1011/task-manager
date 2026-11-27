package vn.smarttask.task.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.smarttask.task.entity.TaskAssignment;

public interface TaskAssignmentRepository extends JpaRepository<TaskAssignment, UUID> {
  List<TaskAssignment> findByTaskId(UUID taskId);
  Optional<TaskAssignment> findByTaskIdAndUserId(UUID taskId, UUID userId);
  List<TaskAssignment> findByTaskIdAndStatus(UUID taskId, String status);

  /** F2.5: EXISTS / COUNT / scoped-id queries instead of loading full rows. */
  boolean existsByTaskIdAndUserId(UUID taskId, UUID userId);
  boolean existsByTaskIdAndUserIdAndStatus(UUID taskId, UUID userId, String status);
  long countByTaskIdAndStatus(UUID taskId, String status);

  @Query("select a.taskId from TaskAssignment a where a.userId = :u and a.status = 'ACTIVE'")
  List<UUID> findActiveTaskIdsByUser(@Param("u") UUID userId);

  /** Grouped active-assignee counts for one dashboard query (no per-task N+1). */
  @Query("select a.taskId as taskId, count(a) as cnt from TaskAssignment a "
      + "where a.taskId in :ids and a.status = 'ACTIVE' group by a.taskId")
  List<TaskAssignCount> countActiveByTaskIds(@Param("ids") java.util.Collection<UUID> taskIds);

  @Query(value = """
      select a.task_id as "taskId", u.display_name as "displayName"
      from task_assignment a join app_user u on u.id=a.user_id
      where a.task_id in (:ids) and a.status='ACTIVE' and a.is_primary
      """, nativeQuery = true)
  List<PrimaryAssignee> primaryNames(@Param("ids") java.util.Collection<UUID> taskIds);

  interface PrimaryAssignee {
    UUID getTaskId();
    String getDisplayName();
  }

  interface TaskAssignCount {
    UUID getTaskId();
    long getCnt();
  }
}
