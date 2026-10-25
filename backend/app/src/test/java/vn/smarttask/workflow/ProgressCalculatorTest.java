package vn.smarttask.workflow;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProgressCalculatorTest {
  @Test
  void fiftyPercentWithFourTopLevelTwoDone() {
    var tasks = List.of(
        new ProgressCalculator.TaskProgressInput(TaskState.COMPLETED, false),
        new ProgressCalculator.TaskProgressInput(TaskState.COMPLETED, false),
        new ProgressCalculator.TaskProgressInput(TaskState.TODO, false),
        new ProgressCalculator.TaskProgressInput(TaskState.IN_PROGRESS, false));
    var r = ProgressCalculator.compute(tasks);
    assertTrue(r.isPresent());
    assertEquals(50.0, r.getAsDouble(), 0.001);
  }

  @Test
  void naWhenEmpty() {
    assertTrue(ProgressCalculator.compute(List.of()).isEmpty());
  }

  @Test
  void naWhenAllCancelled() {
    var tasks = List.of(
        new ProgressCalculator.TaskProgressInput(TaskState.CANCELLED, false),
        new ProgressCalculator.TaskProgressInput(TaskState.CANCELLED, false));
    assertTrue(ProgressCalculator.compute(tasks).isEmpty());
  }

  @Test
  void subtasksExcludedFromDenominator() {
    var tasks = List.of(
        new ProgressCalculator.TaskProgressInput(TaskState.COMPLETED, false),
        new ProgressCalculator.TaskProgressInput(TaskState.TODO, false),
        new ProgressCalculator.TaskProgressInput(TaskState.COMPLETED, true),
        new ProgressCalculator.TaskProgressInput(TaskState.COMPLETED, true));
    var r = ProgressCalculator.compute(tasks);
    assertTrue(r.isPresent());
    assertEquals(50.0, r.getAsDouble(), 0.001);
  }

  @Test
  void cancelledExcludedFromDenominator() {
    var tasks = List.of(
        new ProgressCalculator.TaskProgressInput(TaskState.COMPLETED, false),
        new ProgressCalculator.TaskProgressInput(TaskState.CANCELLED, false),
        new ProgressCalculator.TaskProgressInput(TaskState.TODO, false));
    var r = ProgressCalculator.compute(tasks);
    assertTrue(r.isPresent());
    assertEquals(50.0, r.getAsDouble(), 0.001);
  }
}
