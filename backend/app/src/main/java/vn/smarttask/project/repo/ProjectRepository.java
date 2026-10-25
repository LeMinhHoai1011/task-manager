package vn.smarttask.project.repo;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.smarttask.project.entity.Project;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Project p where p.id = :id")
  Optional<Project> findByIdForUpdate(@Param("id") UUID id);

  List<Project> findByWorkspaceId(UUID workspaceId);
}
