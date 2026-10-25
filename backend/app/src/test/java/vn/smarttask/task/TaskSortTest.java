package vn.smarttask.task;

import static org.junit.jupiter.api.Assertions.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.smarttask.common.AppException;
import vn.smarttask.task.entity.Task;

/** §7 unified sort: docs syntax + legacy fallback + 400 on unknown fields. */
class TaskSortTest {
  private Task task(String title, String priority, String state, int dayOffset) {
    Task t = new Task(UUID.randomUUID(), UUID.randomUUID(), title, UUID.randomUUID());
    t.setPriority(priority);
    t.setState(state);
    t.setDueAt(OffsetDateTime.now(ZoneOffset.UTC).plusDays(dayOffset));
    return t;
  }

  @Test
  void legacySortAndOrderStillWork() {
    Comparator<Task> c = TaskSort.comparator("updated_at", "desc");
    assertNotNull(c);
    assertEquals("desc", TaskSort.primaryOrder("updated_at", "desc"));
    assertEquals("asc", TaskSort.primaryOrder("created_at", "asc"));
  }

  @Test
  void docsSyntaxMinusMeansDesc() {
    var keys = TaskSort.parse("-due_at,priority", null);
    assertEquals(2, keys.size());
    assertEquals("due_at", keys.get(0).field());
    assertTrue(keys.get(0).desc());
    assertEquals("priority", keys.get(1).field());
    assertFalse(keys.get(1).desc());
  }

  @Test
  void docsSyntaxOrdersByDueDate() {
    Task early = task("e", "LOW", "TODO", 1);
    Task late = task("l", "URGENT", "TODO", 9);
    // docs syntax: bare field = asc, minus = desc
    List<Task> rows = new java.util.ArrayList<>(List.of(late, early));
    rows.sort(TaskSort.comparator("-due_at", null));
    assertEquals(late.getId(), rows.get(0).getId());
    rows.sort(TaskSort.comparator("due_at", "asc"));
    assertEquals(early.getId(), rows.get(0).getId());
    // legacy single field without order keeps the old default (desc)
    rows.sort(TaskSort.comparator("due_at", null));
    assertEquals(late.getId(), rows.get(0).getId());
  }

  @Test
  void priorityUsesRankNotLexicographic() {
    Task low = task("low", "LOW", "TODO", 1);
    Task urgent = task("urgent", "URGENT", "TODO", 1);
    List<Task> rows = new java.util.ArrayList<>(List.of(urgent, low));
    rows.sort(TaskSort.comparator("priority", "asc"));
    assertEquals(low.getId(), rows.get(0).getId());
    rows.sort(TaskSort.comparator("-priority", null));
    assertEquals(urgent.getId(), rows.get(0).getId());
  }

  @Test
  void unknownFieldIs400NeverSilentFallback() {
    AppException ex = assertThrows(AppException.class,
        () -> TaskSort.comparator("hacker_field", "desc"));
    assertEquals(400, ex.getHttpStatus().value());
    assertEquals("UNSUPPORTED_SORT_FIELD", ex.getReasonCode());
    AppException ex2 = assertThrows(AppException.class,
        () -> TaskSort.parse("-title,foo", null));
    assertEquals("UNSUPPORTED_SORT_FIELD", ex2.getReasonCode());
  }

  @Test
  void allowlistCoversContractFields() {
    for (String f : List.of("due_at", "start_at", "priority", "state", "title",
        "created_at", "updated_at")) {
      assertNotNull(TaskSort.comparator(f, "asc"), f);
    }
  }
}
