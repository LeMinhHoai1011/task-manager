package vn.smarttask.reports;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PH-6 dashboards: Personal / Project / Workspace / System.
 * Only objects the actor may read are included; PARTIAL when some scope unreadable;
 * System dashboard carries no private content. Historical values derive from
 * workflow events (reopen never rewrites the past — snapshots are append-only).
 */
public final class DashboardComposer {
  private DashboardComposer() {}

  public enum Scope { PERSONAL, PROJECT, WORKSPACE, SYSTEM }

  public record ScopeAccess(String scopeType, String scopeId, boolean readable) {}

  public record Dashboard(Scope scope, Map<String, MetricDictionary.MetricValue> metrics,
      MetricDictionary.MetricState overall) {}

  public static Dashboard compose(Scope scope, List<ScopeAccess> accesses,
      Map<String, MetricDictionary.MetricValue> computed) {
    Map<String, MetricDictionary.MetricValue> out = new LinkedHashMap<>();
    boolean partial = false;
    for (var a : accesses) {
      if (!a.readable()) {
        partial = true;
      }
    }
    for (var e : computed.entrySet()) {
      if (MetricDictionary.isBanned(e.getKey())) continue; // R-07 never emitted
      if (scope == Scope.SYSTEM && isPrivateMetric(e.getKey())) continue; // no private content
      out.put(e.getKey(), e.getValue());
    }
    MetricDictionary.MetricState overall =
        partial ? MetricDictionary.MetricState.PARTIAL : MetricDictionary.MetricState.OK;
    return new Dashboard(scope, out, overall);
  }

  private static boolean isPrivateMetric(String code) {
    return code.startsWith("PERSONAL_") || code.equals("TIMELOG_MINUTES_TOTAL");
  }

  /** Personal: ASSIGNED / CREATED / MANAGED are three SEPARATE sets (may overlap, never summed). */
  public record PersonalSets(List<String> assignedTaskIds, List<String> createdTaskIds,
      List<String> managedProjectIds) {}

  public static Map<String, Integer> personalCounts(PersonalSets sets) {
    Map<String, Integer> m = new LinkedHashMap<>();
    m.put("PERSONAL_ASSIGNED", sets.assignedTaskIds().size());
    m.put("PERSONAL_CREATED", sets.createdTaskIds().size());
    m.put("PERSONAL_MANAGED", sets.managedProjectIds().size());
    return m; // no "total of mine" key — avoids double-count overlap
  }

  public static List<String> historicalFromWorkflowEvents(List<String> eventStates) {
    // replay only; never mutates past snapshots
    return new ArrayList<>(eventStates);
  }
}
