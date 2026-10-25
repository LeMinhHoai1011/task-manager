package vn.smarttask.ai.repo;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.smarttask.ai.entity.AiSuggestion;

public interface AiSuggestionRepository extends JpaRepository<AiSuggestion, UUID> {
  Optional<AiSuggestion> findByJobId(UUID jobId);
}
