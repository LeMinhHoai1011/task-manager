package vn.smarttask.ai;

import java.util.UUID;

/**
 * Minimal participation stub for AI scope checks (ProjectApi).
 * Production impl delegates to ProjectMembershipRepository; tests stub it.
 */
public interface ProjectApi {
  boolean hasParticipation(UUID projectId, UUID userId);

  boolean isActiveUser(UUID userId);
}
