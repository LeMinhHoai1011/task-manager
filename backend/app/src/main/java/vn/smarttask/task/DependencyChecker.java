package vn.smarttask.task;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** In-memory mirror of the WITH RECURSIVE cycle check (unit-testable without PG). */
public final class DependencyChecker {
  private DependencyChecker() {}

  public record Edge(UUID from, UUID to) {}

  /** True if adding from-&gt;to would create a cycle given existing edges. */
  public static boolean wouldCreateCycle(UUID from, UUID to, List<Edge> existing) {
    if (from == null || to == null) {
      return false;
    }
    if (from.equals(to)) {
      return true;
    }
    Map<UUID, Set<UUID>> adj = new HashMap<>();
    if (existing != null) {
      for (Edge e : existing) {
        adj.computeIfAbsent(e.from(), k -> new HashSet<>()).add(e.to());
      }
    }
    // DFS from `to`: can we reach `from`?
    Deque<UUID> stack = new ArrayDeque<>();
    Set<UUID> visited = new HashSet<>();
    stack.push(to);
    while (!stack.isEmpty()) {
      UUID cur = stack.pop();
      if (cur.equals(from)) {
        return true;
      }
      if (!visited.add(cur)) {
        continue;
      }
      Set<UUID> next = adj.get(cur);
      if (next != null) {
        stack.addAll(next);
      }
    }
    return false;
  }
}
