package vn.smarttask.files.repo;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.smarttask.files.entity.Attachment;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select a from Attachment a where a.id = :id")
  java.util.Optional<Attachment> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
  List<Attachment> findByTaskIdAndDeletedAtIsNull(UUID taskId);
}
