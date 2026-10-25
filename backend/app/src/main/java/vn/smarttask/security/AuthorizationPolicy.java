package vn.smarttask.security;

import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Authorization pipeline P1..P9 (PH-1 manual).
 * P1 authenticate, P2 isValidActiveActor, P3 scope, P4 hasParticipation,
 * P5 role/permission, P6 ownership, P7 state-guard, P8 rate-limit, P9 audit.
 *
 * Rule: missing READ on a resource that the actor must not know exists
 * returns RESOURCE_NOT_FOUND (404 + message_key resource.not_found) instead of 403,
 * to avoid resource-existence oracle. Write-denied on a visible resource returns 403.
 */
@Component
public class AuthorizationPolicy {

  public enum Decision { ALLOW, NOT_FOUND, FORBIDDEN }

  public record Actor(UUID userId, boolean active, boolean locked) {}
  public record Resource(UUID id, boolean exists, boolean actorParticipates, boolean actorHasRead) {}

  /** Effective project/workspace state for P7. */
  public record EffectiveState(String workspaceStatus, String projectStatus) {}

  public boolean isValidActiveActor(Actor actor) {
    // P2: non-null, active, not locked
    return actor != null && actor.userId() != null && actor.active() && !actor.locked();
  }

  public boolean hasParticipation(Actor actor, UUID scopeId, Set<UUID> participantUserIds) {
    // P4
    return actor != null && participantUserIds != null && participantUserIds.contains(actor.userId());
  }

  /** P1..P9 pipeline (stubbed steps, real checks land in later phases). */
  public Decision decide(Actor actor, Resource resource, String requiredPermission) {
    if (!isValidActiveActor(actor)) {
      return Decision.FORBIDDEN;
    }
    if (resource == null || !resource.exists()) {
      return Decision.NOT_FOUND;
    }
    // 404-khi-thieu-READ: actor participates-or-not but lacks READ -> hide existence
    if (!resource.actorHasRead()) {
      return Decision.NOT_FOUND;
    }
    // P5: permission check stub — deny write-like permissions without participation flag
    if (requiredPermission != null && requiredPermission.endsWith(":write")
        && !resource.actorParticipates()) {
      return Decision.FORBIDDEN;
    }
    return Decision.ALLOW;
  }

  /** P7 effective state guard. Workspace ARCHIVED overrides project. */
  public Decision decideWithState(Actor actor, Resource resource, String requiredPermission,
      EffectiveState state, String action) {
    Decision base = decide(actor, resource, requiredPermission);
    if (base != Decision.ALLOW) return base;
    if (state == null) return Decision.ALLOW;
    if ("ARCHIVED".equals(state.workspaceStatus()) || "DELETED".equals(state.workspaceStatus())) {
      return Decision.FORBIDDEN;
    }
    if (state.projectStatus() != null) {
      switch (state.projectStatus()) {
        case "ON_HOLD", "COMPLETED", "ARCHIVED", "DELETED" -> {
          if (isWriteAction(action)) return Decision.FORBIDDEN;
        }
        case "PLANNING" -> {
          if ("task.start".equals(action) || "task.submit".equals(action)) return Decision.FORBIDDEN;
        }
        default -> {}
      }
    }
    return Decision.ALLOW;
  }

  private boolean isWriteAction(String action) {
    if (action == null) return false;
    return action.contains("write") || action.contains("update") || action.contains("delete")
        || action.contains("create") || action.contains("start") || action.contains("submit");
  }

  public static String errorCodeFor(Decision d) {
    return switch (d) {
      case NOT_FOUND -> "RESOURCE_NOT_FOUND";
      case FORBIDDEN -> "FORBIDDEN";
      case ALLOW -> "OK";
    };
  }
}
