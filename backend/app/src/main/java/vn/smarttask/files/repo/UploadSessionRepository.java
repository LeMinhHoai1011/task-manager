package vn.smarttask.files.repo;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.smarttask.files.entity.UploadSession;

public interface UploadSessionRepository extends JpaRepository<UploadSession, UUID> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select s from UploadSession s where s.id = :id")
  Optional<UploadSession> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
  Optional<UploadSession> findByIdempotencyKey(String key);

  /** F2.3 staging cleanup: expired, never-committed sessions (page-capped). */
  java.util.List<UploadSession> findByExpiresAtBeforeAndStatusIn(
      OffsetDateTime now, Collection<String> statuses, Pageable pageable);
}
