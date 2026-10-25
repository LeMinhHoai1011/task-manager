package vn.smarttask.files.repo;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.smarttask.files.entity.FileObject;

public interface FileObjectRepository extends JpaRepository<FileObject, UUID> {
  Optional<FileObject> findByObjectKey(String objectKey);
}
