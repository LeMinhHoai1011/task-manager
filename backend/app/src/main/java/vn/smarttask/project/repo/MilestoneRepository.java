package vn.smarttask.project.repo;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.smarttask.project.entity.Milestone;

public interface MilestoneRepository extends JpaRepository<Milestone, UUID> {
  List<Milestone> findByProjectId(UUID projectId);
}
