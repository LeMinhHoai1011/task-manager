package vn.smarttask.workflow;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkflowGuardTest {
  @Test
  void selfApprovalBlockedWhenAssigneeAndSubmitter() {
    UUID u = UUID.randomUUID();
    assertTrue(WorkflowGuard.isSelfApprovalBlocked(u, u, List.of(u)));
  }

  @Test
  void creatorNotBlockedWhenNotSubmitter() {
    UUID creator = UUID.randomUUID();
    UUID submitter = UUID.randomUUID();
    // creator is assignee but not submitter -> allowed
    assertFalse(WorkflowGuard.isSelfApprovalBlocked(creator, submitter, List.of(creator)));
    assertFalse(WorkflowGuard.isCreatorBlocked(creator, submitter, List.of(creator)));
  }

  @Test
  void submitterNotAssigneeNotBlocked() {
    UUID u = UUID.randomUUID();
    assertFalse(WorkflowGuard.isSelfApprovalBlocked(u, u, List.of(UUID.randomUUID())));
  }

  @Test
  void singleTerminalDecisionEnforced() {
    var existing = List.of(new WorkflowGuard.ApprovalDecisionRecord(
        UUID.randomUUID(), WorkflowGuard.Decision.APPROVED, true));
    assertTrue(WorkflowGuard.hasTerminalDecision(existing));
    assertThrows(IllegalStateException.class,
        () -> WorkflowGuard.checkSingleTerminalDecision(existing));
  }

  @Test
  void transitionTableBansTodoToCompleted() {
    assertFalse(WorkflowTransition.isAllowed(TaskState.TODO, TaskState.COMPLETED));
    assertTrue(WorkflowTransition.isAllowed(TaskState.TODO, TaskState.IN_PROGRESS));
    assertTrue(WorkflowTransition.isAllowed(TaskState.IN_PROGRESS, TaskState.COMPLETED));
  }

  @Test
  void aiActorCannotMutate() {
    assertFalse(WorkflowTransition.isActorAllowedToMutate(true));
    assertTrue(WorkflowTransition.isActorAllowedToMutate(false));
  }
}
