package vn.smarttask.files.repo;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.smarttask.files.entity.UploadSession;

public interface UploadSessionRepository extends JpaRepository<UploadSession, UUID> {
  Optional<UploadSession> findByIdempotencyKey(String key);

  /** F2.3 staging cleanup: expired, never-committed sessions (page-capped). */
  java.util.List<UploadSession> findByExpiresAtBeforeAndStatusIn(
      OffsetDateTime now, Collection<String> statuses, Pageable pageable);
}
