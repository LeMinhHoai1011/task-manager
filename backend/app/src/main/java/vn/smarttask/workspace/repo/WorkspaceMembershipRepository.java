package vn.smarttask.workspace.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.smarttask.workspace.entity.WorkspaceMembership;

public interface WorkspaceMembershipRepository extends JpaRepository<WorkspaceMembership, UUID> {
  Optional<WorkspaceMembership> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);
  List<WorkspaceMembership> findByWorkspaceId(UUID workspaceId);
  List<WorkspaceMembership> findByUserId(UUID userId);
  long countByWorkspaceIdAndRoleAndStatus(UUID workspaceId, String role, String status);
  @Query("select m from WorkspaceMembership m where m.workspaceId = :ws and m.userId = :user and m.status = 'ACTIVE'")
  Optional<WorkspaceMembership> findMembership(@Param("ws") UUID ws, @Param("user") UUID user);
}
