package vn.smarttask.reports;

import java.util.List;
import java.util.OptionalDouble;

/**
 * PH-6 metric dictionary (per 02B §3.4). Pure domain — no infrastructure imports.
 * Banned: any per-person productivity ranking (R-07) — no such metric exists here.
 */
public final class MetricDictionary {
  private MetricDictionary() {}

  public static final List<String> CODES = List.of(
      "PROGRESS_PERCENT", "TASK_UNIQUE_TOTAL", "TASK_ASSIGNMENT_COUNT",
      "TASK_BY_STATE_TODO", "TASK_BY_STATE_IN_PROGRESS", "TASK_BY_STATE_PENDING_APPROVAL",
      "TASK_BY_STATE_COMPLETED", "TASK_BY_STATE_CANCELLED",
      "TASK_BLOCKED_COUNT", "TASK_OVERDUE_COUNT", "TASK_OVERDUE_PENDING_APPROVAL_COUNT",
      "MILESTONE_OPEN_COUNT", "MILESTONE_COMPLETED_COUNT", "MILESTONE_CANCELLED_COUNT",
      "MILESTONE_OVERDUE_COUNT",
      "APPROVAL_PENDING_COUNT", "APPROVAL_APPROVED_COUNT", "APPROVAL_REJECTED_COUNT",
      "APPROVAL_AVG_WAIT_MINUTES", "APPROVAL_P95_WAIT_MINUTES", "APPROVAL_NO_APPROVER_COUNT",
      "TIMELOG_MINUTES_TOTAL", "FILE_STORAGE_BYTES", "FILE_COUNT",
      "PERSONAL_ASSIGNED", "PERSONAL_CREATED", "PERSONAL_MANAGED");

  /** R-07: individual productivity ranking is forbidden — never a metric code. */
  public static boolean isBanned(String code) {
    if (code == null) return false;
    String c = code.toUpperCase();
    return c.contains("RANK") || c.contains("LEADERBOARD")
        || c.equals("PRODUCTIVITY_RANKING_INDIVIDUAL");
  }

  public enum MetricState { OK, NOT_APPLICABLE, NO_PERMISSION, INSUFFICIENT_DATA, PARTIAL }

  public record MetricValue(String code, MetricState state, Double numeric, Long intValue,
      Integer numerator, Integer denominator) {
    public static MetricValue na(String code) {
      return new MetricValue(code, MetricState.NOT_APPLICABLE, null, null, null, null);
    }
    public static MetricValue ok(String code, double v, Integer num, Integer den) {
      return new MetricValue(code, MetricState.OK, v, null, num, den);
    }
    public static MetricValue okInt(String code, long v) {
      return new MetricValue(code, MetricState.OK, null, v, null, null);
    }
  }

  public record TaskRow(String state, int depth, boolean blocked, java.time.OffsetDateTime dueAt,
      java.util.UUID id) {}

  /** PROGRESS_PERCENT: top-level COMPLETED / (top-level total − top-level CANCELLED); denom 0 -> N/A. */
  public static MetricValue progressPercent(List<TaskRow> tasks) {
    var top = tasks.stream().filter(t -> t.depth() == 0).toList();
    int denom = (int) top.stream().filter(t -> !"CANCELLED".equals(t.state())).count();
    if (denom == 0) return MetricValue.na("PROGRESS_PERCENT");
    int num = (int) top.stream().filter(t -> "COMPLETED".equals(t.state())).count();
    return MetricValue.ok("PROGRESS_PERCENT", num * 100.0 / denom, num, denom);
  }

  /** TASK_UNIQUE_TOTAL = distinct tasks; TASK_ASSIGNMENT_COUNT = assignment rows (may exceed unique). */
  public static MetricValue uniqueTotal(List<java.util.UUID> distinctTaskIds) {
    return MetricValue.okInt("TASK_UNIQUE_TOTAL", distinctTaskIds.stream().distinct().count());
  }

  public static MetricValue assignmentCount(long assignmentRows) {
    return MetricValue.okInt("TASK_ASSIGNMENT_COUNT", assignmentRows);
  }

  /** Overdue split: generic overdue vs overdue-while-pending-approval. */
  public static MetricValue overdueCount(List<TaskRow> tasks, java.time.OffsetDateTime now) {
    long v = tasks.stream().filter(t -> t.dueAt() != null && t.dueAt().isBefore(now)
        && !"COMPLETED".equals(t.state()) && !"CANCELLED".equals(t.state())).count();
    return MetricValue.okInt("TASK_OVERDUE_COUNT", v);
  }

  public static MetricValue overduePendingApprovalCount(List<TaskRow> tasks, java.time.OffsetDateTime now) {
    long v = tasks.stream().filter(t -> t.dueAt() != null && t.dueAt().isBefore(now)
        && "PENDING_APPROVAL".equals(t.state())).count();
    return MetricValue.okInt("TASK_OVERDUE_PENDING_APPROVAL_COUNT", v);
  }

  public static MetricValue byState(List<TaskRow> tasks, String state) {
    long v = tasks.stream().filter(t -> state.equals(t.state())).count();
    return MetricValue.okInt("TASK_BY_STATE_" + state, v);
  }

  /** Approval stats count ROUNDS, never multiplied by approver count. */
  public record ApprovalRoundRow(String status) {}

  public static MetricValue approvalCount(List<ApprovalRoundRow> rounds, String status) {
    long v = rounds.stream().filter(r -> status.equals(r.status())).count();
    return MetricValue.okInt("APPROVAL_" + status + "_COUNT", v);
  }
}
