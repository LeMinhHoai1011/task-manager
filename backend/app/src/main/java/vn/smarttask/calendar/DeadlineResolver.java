package vn.smarttask.calendar;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * PH-4 deadline semantics: instant + original tz are stored.
 * Date-only input = 23:59:59 in the SETTER's tz (DST-safe via ZonedDateTime).
 * Changing the profile timezone never changes the stored instant — only rendering changes.
 * Clock injectable for tests.
 */
public class DeadlineResolver {
  private final Clock clock;

  public DeadlineResolver(Clock clock) { this.clock = clock; }
  public DeadlineResolver() { this(Clock.systemUTC()); }

  public record ResolvedDeadline(OffsetDateTime instant, String timezone, boolean dateOnly) {}

  /** Date-only: 23:59:59 on that date in setterTz. Handles DST gaps/overlaps via java.time rules. */
  public ResolvedDeadline fromDateOnly(LocalDate date, String setterTimezone) {
    ZoneId tz = ZoneId.of(setterTimezone == null ? "UTC" : setterTimezone);
    ZonedDateTime z = date.atTime(23, 59, 59).atZone(tz);
    return new ResolvedDeadline(z.toOffsetDateTime(), tz.getId(), true);
  }

  public ResolvedDeadline fromInstant(OffsetDateTime instant, String setterTimezone) {
    return new ResolvedDeadline(instant, setterTimezone, false);
  }

  /** Render the same instant in a (possibly changed) profile tz — instant unchanged. */
  public ZonedDateTime renderIn(OffsetDateTime instant, String profileTimezone) {
    return instant.atZoneSameInstant(ZoneId.of(profileTimezone == null ? "UTC" : profileTimezone));
  }

  public OffsetDateTime now() {
    return OffsetDateTime.now(clock);
  }

  public boolean isOverdue(OffsetDateTime dueAt, String state) {
    if (dueAt == null) return false;
    if ("COMPLETED".equals(state) || "CANCELLED".equals(state)) return false;
    return dueAt.isBefore(now());
  }
}
