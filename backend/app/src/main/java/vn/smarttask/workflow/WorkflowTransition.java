package vn.smarttask.workflow;

import java.util.Map;
import java.util.Set;

/**
 * Transition table T-01..T-12 per 05-WORKFLOW-STATE-MACHINES 14.2.
 * Only these edges are allowed. Notably TODO-&gt;COMPLETED is FORBIDDEN
 * (must go through IN_PROGRESS / approval), and no AI actor may mutate.
 */
public final class WorkflowTransition {
  private WorkflowTransition() {}

  public record Edge(TaskState from, TaskState to, String id) {}

  public static final Map<String, Edge> TABLE = Map.ofEntries(
      Map.entry("T-01", new Edge(TaskState.TODO, TaskState.IN_PROGRESS, "T-01")),
      Map.entry("T-02", new Edge(TaskState.IN_PROGRESS, TaskState.TODO, "T-02")),
      Map.entry("T-03", new Edge(TaskState.IN_PROGRESS, TaskState.PENDING_APPROVAL, "T-03")),
      Map.entry("T-04", new Edge(TaskState.PENDING_APPROVAL, TaskState.IN_PROGRESS, "T-04")),
      Map.entry("T-05", new Edge(TaskState.PENDING_APPROVAL, TaskState.COMPLETED, "T-05")),
      Map.entry("T-06", new Edge(TaskState.PENDING_APPROVAL, TaskState.CANCELLED, "T-06")),
      Map.entry("T-07", new Edge(TaskState.TODO, TaskState.CANCELLED, "T-07")),
      Map.entry("T-08", new Edge(TaskState.IN_PROGRESS, TaskState.CANCELLED, "T-08")),
      Map.entry("T-09", new Edge(TaskState.IN_PROGRESS, TaskState.COMPLETED, "T-09")),
      Map.entry("T-10", new Edge(TaskState.CANCELLED, TaskState.TODO, "T-10")),
      Map.entry("T-11", new Edge(TaskState.COMPLETED, TaskState.IN_PROGRESS, "T-11")),
      Map.entry("T-12", new Edge(TaskState.TODO, TaskState.PENDING_APPROVAL, "T-12")));

  private static final Set<String> ALLOWED = Set.of(
      "TODO->IN_PROGRESS",
      "IN_PROGRESS->TODO",
      "IN_PROGRESS->PENDING_APPROVAL",
      "PENDING_APPROVAL->IN_PROGRESS",
      "PENDING_APPROVAL->COMPLETED",
      "PENDING_APPROVAL->CANCELLED",
      "TODO->CANCELLED",
      "IN_PROGRESS->CANCELLED",
      "IN_PROGRESS->COMPLETED",
      "CANCELLED->TODO",
      "COMPLETED->IN_PROGRESS",
      "TODO->PENDING_APPROVAL");

  public static boolean isAllowed(TaskState from, TaskState to) {
    if (from == null || to == null) {
      return false;
    }
    // Explicit bans (defence in depth even though not in ALLOWED set):
    // - TODO->COMPLETED is forbidden
    // - CANCELLED->COMPLETED / COMPLETED->CANCELLED / COMPLETED->TODO / TODO->TODO etc. forbidden
    return ALLOWED.contains(from.name() + "->" + to.name());
  }

  /** AI actors must never mutate workflow state. */
  public static boolean isActorAllowedToMutate(boolean isAiActor) {
    return !isAiActor;
  }
}
