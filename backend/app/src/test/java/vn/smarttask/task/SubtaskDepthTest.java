package vn.smarttask.task;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class SubtaskDepthTest {
  @Test
  void topLevelCanHaveSubtask() {
    assertTrue(SubtaskDepthGuard.isAllowed(0));
  }

  @Test
  void subtaskCannotHaveChild() {
    assertFalse(SubtaskDepthGuard.isAllowed(1));
    assertThrows(IllegalStateException.class, () -> SubtaskDepthGuard.check(1));
  }
}
