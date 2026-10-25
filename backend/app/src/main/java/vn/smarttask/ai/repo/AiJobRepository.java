package vn.smarttask.ai.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.smarttask.ai.entity.AiJob;

public interface AiJobRepository extends JpaRepository<AiJob, UUID> {
  Optional<AiJob> findByIdempotencyKey(String idempotencyKey);

  List<AiJob> findByPrincipalUserIdOrderByCreatedAtDesc(UUID principalUserId);
}
