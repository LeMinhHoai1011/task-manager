package vn.smarttask.task.repo;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.smarttask.task.entity.Task;

public interface TaskRepository extends JpaRepository<Task, UUID>, JpaSpecificationExecutor<Task> {
  @Query("select t.state, count(t) from Task t where t.scope = 'PERSONAL' and t.ownerUserId = :owner and t.deletedAt is null group by t.state")
  List<Object[]> personalCounts(@Param("owner") UUID owner);
  @Query("select count(t) from Task t where t.scope = 'PERSONAL' and t.ownerUserId = :owner and t.deletedAt is null and t.state <> 'COMPLETED' and t.dueAt < :now")
  long personalOverdue(@Param("owner") UUID owner, @Param("now") java.time.OffsetDateTime now);
  org.springframework.data.domain.Page<Task> findByOwnerUserIdAndScopeAndDeletedAtIsNull(
      UUID owner, String scope, org.springframework.data.domain.Pageable pageable);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from Task t where t.id = :id")
  Optional<Task> findByIdForUpdate(@Param("id") UUID id);

  List<Task> findByProjectId(UUID projectId);
  List<Task> findByParentTaskId(UUID parentTaskId);
  org.springframework.data.domain.Page<Task> findByParentTaskIdAndDeletedAtIsNull(UUID parentTaskId,org.springframework.data.domain.Pageable page);
  List<Task> findByProjectIdAndState(UUID projectId, String state);

  /** F2.5 scoped reads: never scan the whole task table from request paths. */
  List<Task> findByProjectIdInAndDeletedAtIsNull(java.util.Collection<UUID> projectIds);
  List<Task> findByCreatedByAndDeletedAtIsNull(UUID createdBy);

  /** Project ids of live tasks assigned to a user (visibility scope, no full scan). */
  @Query("select distinct t.projectId from Task t where t.deletedAt is null and t.id in "
      + "(select a.taskId from vn.smarttask.task.entity.TaskAssignment a "
      + "where a.userId = :u and a.status = 'ACTIVE')")
  List<UUID> findAssignedProjectIdsByUser(@Param("u") UUID userId);
}
