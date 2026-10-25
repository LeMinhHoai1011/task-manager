package vn.smarttask.files;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class FileVersionMonotonicTest {
  @Test
  void nextVersionIncrements() {
    assertEquals(1, FileVersionChain.nextVersion(List.of()));
    assertEquals(4, FileVersionChain.nextVersion(List.of(1, 2, 3)));
  }

  @Test
  void commitNeverOverwritesLowerVersion() {
    assertThrows(IllegalStateException.class,
        () -> FileVersionChain.requireMonotonic(List.of(1, 2, 3), 2));
    assertDoesNotThrow(() -> FileVersionChain.requireMonotonic(List.of(1, 2, 3), 4));
  }

  @Test
  void restoreCreatesNewVersionNotOverwrite() {
    int restored = FileVersionChain.restoreAsNewVersion(List.of(1, 2, 3));
    assertEquals(4, restored);
  }
}
