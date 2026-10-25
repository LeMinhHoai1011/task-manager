package vn.smarttask.files.repo;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.smarttask.files.entity.FileAccessEvent;

public interface FileAccessEventRepository extends JpaRepository<FileAccessEvent, UUID> {
}
