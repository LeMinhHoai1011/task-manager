package vn.smarttask.project.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.smarttask.project.entity.ProjectMembership;

public interface ProjectMembershipRepository extends JpaRepository<ProjectMembership, UUID> {
  Optional<ProjectMembership> findByProjectIdAndUserId(UUID projectId, UUID userId);
  List<ProjectMembership> findByProjectId(UUID projectId);
  long countByProjectIdAndRoleAndStatus(UUID projectId, String role, String status);
  @Query("select m from ProjectMembership m where m.projectId = :p and m.userId = :u and m.status = 'ACTIVE'")
  Optional<ProjectMembership> findMembership(@Param("p") UUID p, @Param("u") UUID u);
  default long countActiveManager(UUID projectId) {
    return countByProjectIdAndRoleAndStatus(projectId, "MANAGER", "ACTIVE");
  }

  /** F2.5: visibility scope without scanning the whole membership table. */
  @Query("select m.projectId from ProjectMembership m where m.userId = :u and m.status = 'ACTIVE'")
  List<UUID> findActiveProjectIdsByUser(@Param("u") UUID userId);

  List<ProjectMembership> findByUserIdAndStatus(UUID userId, String status);
}
