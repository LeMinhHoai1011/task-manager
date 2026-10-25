package vn.smarttask.calendar;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReminderResumeTest {
  private final Clock fixed = Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC);

  private ReminderEngine.Rule rule(UUID user, String remindAt, boolean paused) {
    return new ReminderEngine.Rule(UUID.randomUUID(), user, UUID.randomUUID(),
        OffsetDateTime.parse(remindAt), paused, false);
  }

  @Test
  void pausedRulesDoNotFire() {
    UUID u = UUID.randomUUID();
    var engine = new ReminderEngine(fixed);
    var rules = List.of(rule(u, "2026-05-01T10:00:00Z", true));
    assertTrue(engine.dueNow(rules).isEmpty());
  }

  @Test
  void resumeEmitsSingleDigestPerUserNoBulkCatchUp() {
    UUID u1 = UUID.randomUUID();
    UUID u2 = UUID.randomUUID();
    var engine = new ReminderEngine(fixed);
    var rules = List.of(
        rule(u1, "2026-05-01T08:00:00Z", true),
        rule(u1, "2026-05-01T09:00:00Z", true),
        rule(u1, "2026-05-01T10:00:00Z", true),
        rule(u2, "2026-05-01T09:30:00Z", true),
        rule(u2, "2026-06-01T09:30:00Z", true)); // future: not missed
    var digests = engine.resumeDigests(rules);
    assertEquals(1, digests.get(u1), "exactly one digest even with 3 missed fires");
    assertEquals(1, digests.get(u2));
  }
}
