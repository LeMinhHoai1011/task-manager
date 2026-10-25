package vn.smarttask.task;

/** Subtask depth guard: depth <= 1 (top-level 0, subtask 1, no sub-subtasks). */
public final class SubtaskDepthGuard {
  private SubtaskDepthGuard() {}

  public static final int MAX_DEPTH = 1;

  public static boolean isAllowed(int parentDepth) {
    return parentDepth < MAX_DEPTH;
  }

  public static void check(int parentDepth) {
    if (!isAllowed(parentDepth)) {
      throw new IllegalStateException("SUBTASK_DEPTH_EXCEEDED");
    }
  }
}
