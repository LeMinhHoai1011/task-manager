package vn.smarttask.task;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import vn.smarttask.task.entity.Task;

/**
 * F2.5 list cursor: keyset {@code v1|sort|order|epochMilli|id} over
 * (updated_at,id)/(created_at,id), with legacy plain-{@code id} cursors
 * still accepted (old clients keep working). Resolution is a positional seek
 * over the already scope-bounded candidate set, so there is no deep offset.
 */
public final class TaskCursor {
  private TaskCursor() {}

  /** Start index for the page after {@code cursor} in {@code sorted}. */
  public static int resolveStart(List<Task> sorted, String cursor, String sort, String order) {
    if (cursor == null || cursor.isBlank()) return 0;
    if (!cursor.startsWith("v1|")) {
      // legacy id cursor
      for (int i = 0; i < sorted.size(); i++) {
        if (sorted.get(i).getId().toString().equals(cursor)) return i + 1;
      }
      return 0;
    }
    String[] parts = cursor.split("\\|");
    if (parts.length != 5) return 0;
    long ts;
    UUID id;
    try {
      ts = Long.parseLong(parts[3]);
      id = UUID.fromString(parts[4]);
    } catch (Exception e) {
      return 0;
    }
    boolean asc = "asc".equalsIgnoreCase(parts[2]);
    for (int i = 0; i < sorted.size(); i++) {
      Task t = sorted.get(i);
      long v = keyOf(t, parts[1]);
      int c = Long.compare(v, ts);
      if (c == 0) c = t.getId().compareTo(id);
      if (asc ? c > 0 : c < 0) return i;
    }
    return sorted.size();
  }

  /** Next cursor for the last row of a page (keyset form). */
  public static String nextCursor(Task last, String sort, String order) {
    long ts = keyOf(last, sort);
    return "v1|" + sort + "|" + order + "|" + ts + "|" + last.getId();
  }

  private static long keyOf(Task t, String sort) {
    OffsetDateTime v = "created_at".equals(sort) ? t.getCreatedAt()
        : "due_at".equals(sort) ? t.getDueAt() : t.getUpdatedAt();
    return v == null ? 0L : v.toInstant().toEpochMilli();
  }
}
