package vn.smarttask.ai.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.smarttask.ai.entity.AiContextManifest;

public interface AiContextManifestRepository extends JpaRepository<AiContextManifest, String> {
}
