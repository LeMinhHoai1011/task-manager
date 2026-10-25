package vn.smarttask.reports;

import static org.junit.jupiter.api.Assertions.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MetricDictionaryTest {
  private MetricDictionary.TaskRow row(String state, int depth) {
    return new MetricDictionary.TaskRow(state, depth, false, null, UUID.randomUUID());
  }

  @Test
  void progressNaWhenDenominatorZero() {
    assertEquals(MetricDictionary.MetricState.NOT_APPLICABLE,
        MetricDictionary.progressPercent(List.of()).state());
    assertEquals(MetricDictionary.MetricState.NOT_APPLICABLE,
        MetricDictionary.progressPercent(List.of(row("CANCELLED", 0), row("CANCELLED", 0))).state());
  }

  @Test
  void progressExcludesCancelledAndSubtasks() {
    var tasks = List.of(row("COMPLETED", 0), row("TODO", 0), row("CANCELLED", 0),
        row("COMPLETED", 1), row("COMPLETED", 1));
    var v = MetricDictionary.progressPercent(tasks);
    assertEquals(50.0, v.numeric(), 0.001);
    assertEquals(1, v.numerator());
    assertEquals(2, v.denominator());
  }

  @Test
  void uniqueVsAssignmentCounts() {
    UUID a = UUID.randomUUID();
    var unique = MetricDictionary.uniqueTotal(List.of(a, a, UUID.randomUUID()));
    assertEquals(2L, unique.intValue());
    // 3 assignment rows across 2 distinct tasks: both metrics reported together
    var assign = MetricDictionary.assignmentCount(3);
    assertEquals(3L, assign.intValue());
    assertNotEquals(unique.intValue(), assign.intValue());
  }

  @Test
  void overdueDistinguishesPendingApproval() {
    var now = OffsetDateTime.now(ZoneOffset.UTC);
    var past = now.minusDays(1);
    var tasks = List.of(
        new MetricDictionary.TaskRow("IN_PROGRESS", 0, false, past, UUID.randomUUID()),
        new MetricDictionary.TaskRow("PENDING_APPROVAL", 0, false, past, UUID.randomUUID()),
        new MetricDictionary.TaskRow("COMPLETED", 0, false, past, UUID.randomUUID()));
    assertEquals(2L, MetricDictionary.overdueCount(tasks, now).intValue());
    assertEquals(1L, MetricDictionary.overduePendingApprovalCount(tasks, now).intValue());
  }

  @Test
  void approvalCountsRoundsNotApprovers() {
    var rounds = List.of(
        new MetricDictionary.ApprovalRoundRow("PENDING"),
        new MetricDictionary.ApprovalRoundRow("PENDING"));
    assertEquals(2L, MetricDictionary.approvalCount(rounds, "PENDING").intValue());
  }

  @Test
  void rankingBanned() {
    assertTrue(MetricDictionary.isBanned("PRODUCTIVITY_RANKING_INDIVIDUAL"));
    assertTrue(MetricDictionary.isBanned("user_leaderboard"));
    assertFalse(MetricDictionary.isBanned("PROGRESS_PERCENT"));
  }
}
