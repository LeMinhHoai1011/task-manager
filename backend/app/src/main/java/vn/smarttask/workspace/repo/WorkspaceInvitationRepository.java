package vn.smarttask.workspace.repo;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.smarttask.workspace.entity.WorkspaceInvitation;

public interface WorkspaceInvitationRepository extends JpaRepository<WorkspaceInvitation, UUID> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select i from WorkspaceInvitation i where i.id=:id")
  java.util.Optional<WorkspaceInvitation> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
  org.springframework.data.domain.Page<WorkspaceInvitation> findByEmailIgnoreCaseAndStatus(String email,String status,org.springframework.data.domain.Pageable page);
  List<WorkspaceInvitation> findByWorkspaceIdAndEmailIgnoreCase(UUID workspaceId, String email);
}
