package vn.smarttask.task;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PendingApprovalGuardTest {
  @Test
  void pendingBlocksAllMutationPaths() {
    for (String action : PendingApprovalGuard.LOCKED_ACTIONS) {
      assertTrue(PendingApprovalGuard.isLockedAction("PENDING_APPROVAL", action),
          "must lock " + action);
    }
  }

  @Test
  void nonPendingStatesAllow() {
    for (String state : new String[]{"TODO", "IN_PROGRESS", "COMPLETED", "CANCELLED"}) {
      assertFalse(PendingApprovalGuard.isLockedAction(state, "task.update"));
    }
  }

  @Test
  void commentsNotInLockedSet() {
    assertFalse(PendingApprovalGuard.LOCKED_ACTIONS.contains("comment.create"));
  }
}
