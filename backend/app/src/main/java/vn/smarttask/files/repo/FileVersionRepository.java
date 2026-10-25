package vn.smarttask.files.repo;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.smarttask.files.entity.FileVersion;

public interface FileVersionRepository extends JpaRepository<FileVersion, UUID> {
  List<FileVersion> findByAttachmentIdOrderByVersionNoAsc(UUID attachmentId);
}
