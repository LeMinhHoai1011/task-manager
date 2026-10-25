package vn.smarttask.calendar;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CalendarViewTest {
  private static CalendarView.TaskDue due(String iso) {
    return new CalendarView.TaskDue(UUID.randomUUID(), UUID.randomUUID(),
        iso == null ? null : OffsetDateTime.parse(iso));
  }

  @Test
  void dayWeekMonthUnscheduled() {
    var tasks = List.of(
        due("2026-05-04T08:00:00Z"), // Monday
        due("2026-05-06T08:00:00Z"), // Wednesday
        due("2026-05-20T08:00:00Z"), // later this month
        due(null));
    ZoneId utc = ZoneOffset.UTC;
    assertEquals(1, CalendarView.query(tasks, CalendarView.View.DAY,
        LocalDate.of(2026, 5, 4), utc).size());
    assertEquals(2, CalendarView.query(tasks, CalendarView.View.WEEK,
        LocalDate.of(2026, 5, 4), utc).size());
    assertEquals(3, CalendarView.query(tasks, CalendarView.View.MONTH,
        LocalDate.of(2026, 5, 15), utc).size());
    assertEquals(1, CalendarView.query(tasks, CalendarView.View.UNSCHEDULED,
        LocalDate.of(2026, 5, 4), utc).size());
  }
}
