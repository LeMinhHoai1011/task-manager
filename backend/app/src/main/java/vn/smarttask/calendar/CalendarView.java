package vn.smarttask.calendar;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * PH-4 calendar views: day/week/month/unscheduled over task due instants.
 * Pure domain — no infrastructure imports.
 */
public final class CalendarView {
  private CalendarView() {}

  public enum View { DAY, WEEK, MONTH, UNSCHEDULED }

  public record TaskDue(UUID taskId, UUID projectId, OffsetDateTime dueAt) {}

  public static List<TaskDue> query(List<TaskDue> all, View view, LocalDate anchor, ZoneId zone) {
    List<TaskDue> out = new ArrayList<>();
    for (TaskDue t : all) {
      if (view == View.UNSCHEDULED) {
        if (t.dueAt() == null) out.add(t);
        continue;
      }
      if (t.dueAt() == null) continue;
      LocalDate d = t.dueAt().atZoneSameInstant(zone).toLocalDate();
      boolean match = switch (view) {
        case DAY -> d.equals(anchor);
        case WEEK -> !d.isBefore(anchor) && d.isBefore(anchor.plusDays(7));
        case MONTH -> d.getYear() == anchor.getYear() && d.getMonth() == anchor.getMonth();
        default -> false;
      };
      if (match) out.add(t);
    }
    return out;
  }
}
