package vn.smarttask.calendar;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PH-4 reminder pause/resume: pausing suspends fires; resume recomputes the schedule
 * and emits exactly ONE digest notification per user (no bulk catch-up of missed fires).
 * Pure domain — clock injectable.
 */
public class ReminderEngine {
  private final Clock clock;

  public ReminderEngine(Clock clock) { this.clock = clock; }
  public ReminderEngine() { this(java.time.Clock.systemUTC()); }

  public record Rule(UUID id, UUID userId, UUID taskId, OffsetDateTime remindAt,
      boolean paused, boolean fired) {}

  public record FireDecision(UUID ruleId, UUID userId, boolean fire, boolean asDigest, String reason) {}

  /** Compute what fires now for active, unpaused, due rules. */
  public List<FireDecision> dueNow(List<Rule> rules) {
    OffsetDateTime now = OffsetDateTime.now(clock);
    List<FireDecision> out = new ArrayList<>();
    for (Rule r : rules) {
      if (r.fired() || r.paused()) continue;
      if (!r.remindAt().isAfter(now)) {
        out.add(new FireDecision(r.id(), r.userId(), true, false, "DUE"));
      }
    }
    return out;
  }

  /**
   * Resume: recompute — all rules that became due WHILE paused collapse into ONE
   * digest per user; no per-fire catch-up bulk.
   */
  public Map<UUID, Integer> resumeDigests(List<Rule> rules) {
    OffsetDateTime now = OffsetDateTime.now(clock);
    Map<UUID, Integer> missedPerUser = new LinkedHashMap<>();
    for (Rule r : rules) {
      if (!r.paused() || r.fired()) continue;
      if (!r.remindAt().isAfter(now)) {
        missedPerUser.merge(r.userId(), 1, Integer::sum);
      }
    }
    // one digest per user regardless of missed count (no bulk catch-up)
    Map<UUID, Integer> digests = new LinkedHashMap<>();
    for (UUID user : missedPerUser.keySet()) {
      digests.put(user, 1);
    }
    return digests;
  }
}
