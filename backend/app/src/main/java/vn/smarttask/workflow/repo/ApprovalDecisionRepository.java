package vn.smarttask.workflow.repo;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.smarttask.workflow.entity.ApprovalDecision;

public interface ApprovalDecisionRepository extends JpaRepository<ApprovalDecision, UUID> {
  Optional<ApprovalDecision> findByRoundId(UUID roundId);
}
