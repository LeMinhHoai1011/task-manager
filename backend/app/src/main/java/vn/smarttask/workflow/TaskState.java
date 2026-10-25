package vn.smarttask.workflow;

/** Task lifecycle states. blocked_flag is orthogonal (separate boolean), not a state. */
public enum TaskState {
  TODO,
  IN_PROGRESS,
  PENDING_APPROVAL,
  COMPLETED,
  CANCELLED
}
