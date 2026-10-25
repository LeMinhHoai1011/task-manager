package vn.smarttask.workspace.repo;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.smarttask.workspace.entity.WorkspaceInvitation;

public interface WorkspaceInvitationRepository extends JpaRepository<WorkspaceInvitation, UUID> {
  List<WorkspaceInvitation> findByWorkspaceIdAndEmailIgnoreCase(UUID workspaceId, String email);
}
