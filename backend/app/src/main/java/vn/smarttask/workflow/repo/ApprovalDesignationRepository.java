package vn.smarttask.workflow.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.smarttask.workflow.entity.ApprovalDecision;
import vn.smarttask.workflow.entity.ApprovalDesignation;
import vn.smarttask.workflow.entity.WorkflowEvent;

public interface ApprovalDesignationRepository extends JpaRepository<ApprovalDesignation, UUID> {
  List<ApprovalDesignation> findByRoundId(UUID roundId);
  Optional<ApprovalDesignation> findByRoundIdAndUserId(UUID roundId, UUID userId);
}
