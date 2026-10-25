package vn.smarttask.ai.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.smarttask.ai.entity.AiModelConfiguration;

public interface AiModelConfigurationRepository extends JpaRepository<AiModelConfiguration, String> {
}
