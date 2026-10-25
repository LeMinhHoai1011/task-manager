package vn.smarttask.task;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import vn.smarttask.common.AppException;
import vn.smarttask.task.entity.Task;

/**
 * §7 unified sort: accepts {@code sort=-field,field2} (docs) plus the legacy
 * {@code sort + order} pair as fallback. Allowlist:
 * due_at, start_at, priority, state, title, created_at, updated_at.
 * Unknown fields -> 400 UNSUPPORTED_SORT_FIELD (never a silent fallback).
 */
public final class TaskSort {
  private TaskSort() {}

  private static final Set<String> ALLOWED = Set.of(
      "due_at", "start_at", "priority", "state", "title", "created_at", "updated_at");

  private static final List<String> PRIORITY_RANK = List.of("LOW", "MEDIUM", "HIGH", "URGENT");

  public record SortKey(String field, boolean desc) {}

  /** Parses docs syntax ({@code -field} = desc) or legacy (field + order). */
  public static List<SortKey> parse(String sort, String order) {
    String s = sort == null || sort.isBlank() ? "updated_at" : sort.trim();
    List<SortKey> keys = new ArrayList<>();
    if (s.contains(",") || s.startsWith("-") || s.startsWith("+")) {
      for (String part : s.split(",")) {
        String p = part.trim();
        if (p.isEmpty()) continue;
        boolean desc = p.startsWith("-");
        String field = p.startsWith("-") || p.startsWith("+") ? p.substring(1) : p;
        check(field);
        keys.add(new SortKey(field, desc));
      }
      if (keys.isEmpty()) keys.add(new SortKey("updated_at", true));
      return List.copyOf(keys);
    }
    check(s);
    boolean desc = !"asc".equalsIgnoreCase(order == null ? "desc" : order);
    return List.of(new SortKey(s, desc));
  }

  private static void check(String field) {
    if (!ALLOWED.contains(field)) throw AppException.unsupportedSortField(field);
  }

  private static int rankPriority(String p) {
    int i = PRIORITY_RANK.indexOf(p == null ? "" : p.toUpperCase());
    return i < 0 ? 1 : i;
  }

  private static Comparator<Task> keyComparator(SortKey k) {
    Comparator<Task> c = switch (k.field()) {
      case "created_at" -> Comparator.comparing(Task::getCreatedAt,
          Comparator.nullsLast(Comparator.naturalOrder()));
      case "due_at" -> Comparator.comparing(Task::getDueAt,
          Comparator.nullsLast(Comparator.naturalOrder()));
      case "start_at" -> Comparator.comparing(Task::getStartAt,
          Comparator.nullsLast(Comparator.naturalOrder()));
      case "priority" -> Comparator.comparingInt(t -> rankPriority(t.getPriority()));
      case "state" -> Comparator.comparing(t -> t.getState() == null ? "" : t.getState(),
          Comparator.nullsLast(Comparator.naturalOrder()));
      case "title" -> Comparator.comparing(t -> t.getTitle() == null ? "" : t.getTitle(),
          Comparator.nullsLast(Comparator.naturalOrder()));
      default -> Comparator.comparing(Task::getUpdatedAt,
          Comparator.nullsLast(Comparator.naturalOrder()));
    };
    c = c.thenComparing(Task::getId);
    return k.desc() ? c.reversed() : c;
  }

  /** Total order (id tiebreak) so keyset cursors stay deterministic. */
  public static Comparator<Task> comparator(String sort, String order) {
    List<SortKey> keys = parse(sort, order);
    Comparator<Task> c = keyComparator(keys.get(0));
    for (int i = 1; i < keys.size(); i++) c = c.thenComparing(keyComparator(keys.get(i)));
    return c;
  }

  /** Canonical order token for cursor encode/decode ("asc"/"desc" of first key). */
  public static String primaryOrder(String sort, String order) {
    return parse(sort, order).get(0).desc() ? "desc" : "asc";
  }
}
