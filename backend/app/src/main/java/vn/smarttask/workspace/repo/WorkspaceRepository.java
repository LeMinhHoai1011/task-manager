package vn.smarttask.workspace.repo;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.smarttask.workspace.entity.Workspace;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select w from Workspace w where w.id = :id")
  Optional<Workspace> findByIdForUpdate(@Param("id") UUID id);

  long countByOwnerUserIdAndTypeAndStatusNot(UUID ownerId, String type, String status);
  List<Workspace> findByOwnerUserId(UUID ownerId);
}
