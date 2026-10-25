package vn.smarttask.workflow.repo;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.smarttask.workflow.entity.ApprovalDecision;
import vn.smarttask.workflow.entity.ApprovalDesignation;
import vn.smarttask.workflow.entity.ApprovalRound;
import vn.smarttask.workflow.entity.WorkflowEvent;

public interface ApprovalRoundRepository extends JpaRepository<ApprovalRound, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from ApprovalRound r where r.id = :id")
  Optional<ApprovalRound> findByIdForUpdate(@Param("id") UUID id);

  List<ApprovalRound> findByTaskIdOrderByRoundNoAsc(UUID taskId);
  Optional<ApprovalRound> findFirstByTaskIdOrderByRoundNoDesc(UUID taskId);
  List<ApprovalRound> findByTaskIdAndStatus(UUID taskId, String status);
  @Query("select r from ApprovalRound r where r.taskId in (select t.id from vn.smarttask.task.entity.Task t where t.projectId = :p) and r.status = 'OPEN'")
  List<ApprovalRound> findOpenByProject(@Param("p") UUID projectId);

  /** F2.5: all round statuses of a project in ONE query (dashboard N+1 fix). */
  @Query("select r.status from ApprovalRound r where r.taskId in "
      + "(select t.id from vn.smarttask.task.entity.Task t where t.projectId = :p)")
  List<String> findStatusesByProjectId(@Param("p") UUID projectId);
}
