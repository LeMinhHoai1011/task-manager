package vn.smarttask.task;

import java.util.Set;

/**
 * PH-2/PH-3 execution lock: Task PENDING_APPROVAL blocks content mutations through ANY
 * API path (task content, deadline, assignment, checklist, dependency, timelog, files).
 * Comments remain allowed (audited). Pure domain helper — no infrastructure imports.
 */
public final class PendingApprovalGuard {
  private PendingApprovalGuard() {}

  public static final Set<String> LOCKED_ACTIONS = Set.of(
      "task.update", "task.deadline.set", "task.assign", "task.unassign",
      "task.checklist.add", "task.checklist.tick", "task.dependency.add", "task.timelog.add",
      "task.blocked.set", "file.upload.init", "file.upload.commit",
      "file.restore", "file.delete");

  public static boolean isLocked(String state) {
    return "PENDING_APPROVAL".equals(state);
  }

  public static boolean isLockedAction(String state, String action) {
    return isLocked(state) && LOCKED_ACTIONS.contains(action);
  }
}
