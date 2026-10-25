package vn.smarttask.calendar;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class DeadlineDstTest {
  private final Clock fixed = Clock.fixed(Instant.parse("2026-03-01T12:00:00Z"), ZoneOffset.UTC);
  private final DeadlineResolver r = new DeadlineResolver(fixed);

  @Test
  void dateOnlyIs235959InSetterTz() {
    var d = r.fromDateOnly(LocalDate.of(2026, 3, 9), "America/New_York");
    // March 9 2026 is EDT (UTC-4) after spring-forward Mar 8
    assertEquals("America/New_York", d.timezone());
    assertTrue(d.dateOnly());
    assertEquals(23, d.instant().atZoneSameInstant(ZoneId.of("America/New_York")).getHour());
    assertEquals(59, d.instant().atZoneSameInstant(ZoneId.of("America/New_York")).getMinute());
    assertEquals(59, d.instant().atZoneSameInstant(ZoneId.of("America/New_York")).getSecond());
  }

  @Test
  void dstSpringForwardInstantIsUnambiguous() {
    // date-only on the DST transition day itself still resolves (lenient java.time rules)
    var d = r.fromDateOnly(LocalDate.of(2026, 3, 8), "America/New_York");
    assertNotNull(d.instant());
    // 23:59:59 EDT = 03:59:59Z next day
    assertEquals(Instant.parse("2026-03-09T03:59:59Z"), d.instant().toInstant());
  }

  @Test
  void profileTimezoneChangeKeepsInstant() {
    var d = r.fromDateOnly(LocalDate.of(2026, 5, 1), "Asia/Ho_Chi_Minh");
    var before = d.instant().toInstant();
    var renderedHanoi = r.renderIn(d.instant(), "Asia/Ho_Chi_Minh");
    var renderedUtc = r.renderIn(d.instant(), "UTC");
    assertEquals(before, renderedHanoi.toInstant());
    assertEquals(before, renderedUtc.toInstant());
    // same instant, different wall clock
    assertNotEquals(renderedHanoi.getHour(), renderedUtc.getHour());
  }

  @Test
  void instantDeadlineKeepsOriginalTz() {
    var inst = java.time.OffsetDateTime.parse("2026-05-01T10:00:00+02:00");
    var d = r.fromInstant(inst, "Europe/Berlin");
    assertEquals(inst.toInstant(), d.instant().toInstant());
    assertFalse(d.dateOnly());
  }
}
