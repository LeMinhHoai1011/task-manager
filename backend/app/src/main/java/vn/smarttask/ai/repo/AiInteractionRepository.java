package vn.smarttask.ai.repo;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.smarttask.ai.entity.AiInteraction;

public interface AiInteractionRepository extends JpaRepository<AiInteraction, UUID> {
  List<AiInteraction> findByJobId(UUID jobId);
}
