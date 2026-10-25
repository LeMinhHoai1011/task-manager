package vn.smarttask.common.repo;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.smarttask.common.entity.AuditEvent;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
  /** F2.5: newest-first audit page (DB ordering, no in-memory sort of findAll). */
  Page<AuditEvent> findAllByOrderByIdDesc(Pageable pageable);

  /** Keyset page: rows older than the cursor id (UUIDv7 ids are time-ordered). */
  Page<AuditEvent> findByIdLessThanOrderByIdDesc(UUID cursor, Pageable pageable);
}
