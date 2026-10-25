package vn.smarttask.realtime;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.smarttask.project.repo.ProjectMembershipRepository;
import vn.smarttask.workspace.repo.WorkspaceMembershipRepository;

/**
 * F4 realtime wiring: turns committed domain mutations into signal-only
 * publishes.
 *
 * <p>Recipients are resolved from live membership rows (project/workspace
 * members), never from a client-supplied header. Only routing keys are sent;
 * no titles/bodies/payloads. Publishing is deferred to {@code afterCommit} when
 * a transaction is active, so a rolled-back write never notifies clients.
 *
 * <p>Signals are advisory: a client that lost access re-fetches through the
 * REST API and gets 403/404, so a stale signal cannot leak content.
 */
@Component
public class RealtimePublisher {

  private static final Logger log = LoggerFactory.getLogger(RealtimePublisher.class);

  private final RealtimeRegistry registry;
  private final ProjectMembershipRepository projectMembers;
  private final WorkspaceMembershipRepository workspaceMembers;

  public RealtimePublisher(RealtimeRegistry registry,
      ProjectMembershipRepository projectMembers,
      WorkspaceMembershipRepository workspaceMembers) {
    this.registry = registry;
    this.projectMembers = projectMembers;
    this.workspaceMembers = workspaceMembers;
  }

  /** Signal every ACTIVE member of a project (scope = PROJECT). */
  public void project(UUID projectId, String kind, UUID objectId) {
    if (projectId == null) return;
    List<UUID> users = new ArrayList<>();
    for (var m : projectMembers.findByProjectId(projectId)) {
      if ("ACTIVE".equals(m.getStatus())) users.add(m.getUserId());
    }
    afterCommit(() -> {
      for (UUID userId : users) {
        registry.publishToUser(userId, kind, "PROJECT", projectId, objectId);
      }
    });
  }

  /** Signal every ACTIVE member of a workspace (scope = WORKSPACE). */
  public void workspace(UUID workspaceId, String kind, UUID objectId) {
    if (workspaceId == null) return;
    List<UUID> users = new ArrayList<>();
    for (var m : workspaceMembers.findByWorkspaceId(workspaceId)) {
      if ("ACTIVE".equals(m.getStatus())) users.add(m.getUserId());
    }
    afterCommit(() -> {
      for (UUID userId : users) {
        registry.publishToUser(userId, kind, "WORKSPACE", workspaceId, objectId);
      }
    });
  }

  /** Signal one user (e.g. the owner of a notification). */
  public void user(UUID userId, String kind, String scopeType, UUID scopeId, UUID objectId) {
    if (userId == null) return;
    afterCommit(() -> registry.publishToUser(userId, kind, scopeType, scopeId, objectId));
  }

  private void afterCommit(Runnable action) {
    Runnable safe = () -> {
      try {
        action.run();
      } catch (RuntimeException ex) {
        // Realtime is best-effort: a failed publish must never fail the write.
        log.warn("realtime publish failed: {}", ex.getMessage());
      }
    };
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          safe.run();
        }
      });
    } else {
      safe.run();
    }
  }
}
