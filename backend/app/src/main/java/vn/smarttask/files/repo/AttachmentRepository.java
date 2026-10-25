package vn.smarttask.files.repo;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.smarttask.files.entity.Attachment;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {
  List<Attachment> findByTaskIdAndDeletedAtIsNull(UUID taskId);
}
