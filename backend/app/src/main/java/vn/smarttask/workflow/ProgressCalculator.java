package vn.smarttask.workflow;

import java.util.List;
import java.util.OptionalDouble;

/**
 * Progress = top-level COMPLETED / (total top-level - CANCELLED).
 * Subtasks never enter the denominator. Empty/fully-cancelled -&gt; N/A (empty).
 */
public final class ProgressCalculator {
  private ProgressCalculator() {}

  public record TaskProgressInput(TaskState state, boolean isSubtask) {}

  public static OptionalDouble compute(List<TaskProgressInput> tasks) {
    if (tasks == null) {
      return OptionalDouble.empty();
    }
    List<TaskProgressInput> top = tasks.stream().filter(t -> !t.isSubtask()).toList();
    long denom = top.stream().filter(t -> t.state() != TaskState.CANCELLED).count();
    if (denom == 0) {
      return OptionalDouble.empty();
    }
    long done = top.stream().filter(t -> t.state() == TaskState.COMPLETED).count();
    return OptionalDouble.of((done * 100.0) / denom);
  }
}
