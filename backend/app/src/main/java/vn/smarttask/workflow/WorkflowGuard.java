package vn.smarttask.workflow;

import java.util.List;
import java.util.UUID;

/**
 * Guards for approval flow.
 * - guard_no_self_approval: a user who is BOTH assignee AND submitter (SUB) on the
 *   same round may not approve that round.
 * - single-terminal-decision: exactly one terminal decision (APPROVED/REJECTED) per round.
 */
public final class WorkflowGuard {
  private WorkflowGuard() {}

  public enum Decision { APPROVED, REJECTED, PENDING }

  public record Designation(UUID userId, String role) {}
  public record ApprovalDecisionRecord(UUID userId, Decision decision, boolean terminal) {}

  /**
   * Returns true when the approver is blocked by no-self-approval.
   * Blocked iff approverUserId is the submitter AND also an assignee (SUB).
   */
  public static boolean isSelfApprovalBlocked(UUID approverUserId, UUID submitterUserId,
      List<UUID> assigneeUserIds) {
    if (approverUserId == null || submitterUserId == null) {
      return false;
    }
    boolean isSubmitter = approverUserId.equals(submitterUserId);
    boolean isAssignee = assigneeUserIds != null && assigneeUserIds.contains(approverUserId);
    return isSubmitter && isAssignee;
  }

  /** Creator who is NOT submitter/assignee-SUB is not blocked. Convenience overload. */
  public static boolean isCreatorBlocked(UUID creatorUserId, UUID submitterUserId,
      List<UUID> assigneeUserIds) {
    return isSelfApprovalBlocked(creatorUserId, submitterUserId, assigneeUserIds);
  }

  /** True when the round already has a terminal decision (no second terminal allowed). */
  public static boolean hasTerminalDecision(List<ApprovalDecisionRecord> decisions) {
    if (decisions == null) {
      return false;
    }
    return decisions.stream().anyMatch(d -> d.terminal()
        && (d.decision() == Decision.APPROVED || d.decision() == Decision.REJECTED));
  }

  public static void checkSingleTerminalDecision(List<ApprovalDecisionRecord> existing) {
    if (hasTerminalDecision(existing)) {
      throw new IllegalStateException("APPROVAL_ROUND_ALREADY_DECIDED");
    }
  }
}
