package vn.smarttask.ai;

import java.util.UUID;
import vn.smarttask.project.repo.ProjectMembershipRepository;

/** Production ProjectApi backed by membership repo (read-only). */
public class RepositoryProjectApi implements ProjectApi {
  private final ProjectMembershipRepository members;
  private final vn.smarttask.identity.repo.AppUserRepository users;

  public RepositoryProjectApi(ProjectMembershipRepository members,
      vn.smarttask.identity.repo.AppUserRepository users) {
    this.members = members;
    this.users = users;
  }

  @Override
  public boolean hasParticipation(UUID projectId, UUID userId) {
    if (projectId == null || userId == null) return false;
    return members.findMembership(projectId, userId).isPresent();
  }

  @Override
  public boolean isActiveUser(UUID userId) {
    if (userId == null) return false;
    return users.findById(userId)
        .map(u -> u.isActive() && "ACTIVE".equals(u.getStatus()) && !u.isLocked())
        .orElse(false);
  }
}
