package vn.smarttask.task;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DependencyCycleTest {
  @Test
  void selfDependencyIsCycle() {
    UUID a = UUID.randomUUID();
    assertTrue(DependencyChecker.wouldCreateCycle(a, a, List.of()));
  }

  @Test
  void directCycleDetected() {
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    var existing = List.of(new DependencyChecker.Edge(b, a));
    assertTrue(DependencyChecker.wouldCreateCycle(a, b, existing));
  }

  @Test
  void transitiveCycleDetected() {
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    UUID c = UUID.randomUUID();
    var existing = List.of(
        new DependencyChecker.Edge(b, c),
        new DependencyChecker.Edge(c, a));
    assertTrue(DependencyChecker.wouldCreateCycle(a, b, existing));
  }

  @Test
  void noCycleWhenUnrelated() {
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    UUID c = UUID.randomUUID();
    var existing = List.of(new DependencyChecker.Edge(c, a));
    assertFalse(DependencyChecker.wouldCreateCycle(a, b, existing));
  }

  @Test
  void longChainCycleDetected() {
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    UUID c = UUID.randomUUID();
    UUID d = UUID.randomUUID();
    var existing = List.of(
        new DependencyChecker.Edge(b, c),
        new DependencyChecker.Edge(c, d),
        new DependencyChecker.Edge(d, a));
    assertTrue(DependencyChecker.wouldCreateCycle(a, b, existing));
  }

  @Test
  void diamondWithoutCycleAllowed() {
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    UUID c = UUID.randomUUID();
    UUID d = UUID.randomUUID();
    var existing = List.of(
        new DependencyChecker.Edge(a, b),
        new DependencyChecker.Edge(a, c),
        new DependencyChecker.Edge(b, d),
        new DependencyChecker.Edge(c, d));
    // d -> a would close a loop, but b -> c is a fresh cross edge
    assertTrue(DependencyChecker.wouldCreateCycle(d, a, existing));
    assertFalse(DependencyChecker.wouldCreateCycle(b, c, existing));
  }

  @Test
  void nullSafe() {
    assertFalse(DependencyChecker.wouldCreateCycle(null, UUID.randomUUID(), List.of()));
    assertFalse(DependencyChecker.wouldCreateCycle(UUID.randomUUID(), null, List.of()));
  }
}
