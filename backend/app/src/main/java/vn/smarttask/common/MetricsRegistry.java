package vn.smarttask.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PH-7 metrics stub: in-memory latency recorder with P50/P95/P99.
 * No external registry required in V1.
 */
public class MetricsRegistry {
  private final Map<String, List<Long>> samples = new ConcurrentHashMap<>();
  private static final int MAX_SAMPLES = 4096;

  public void record(String route, long latencyMs) {
    samples.compute(route, (k, v) -> {
      List<Long> l = v == null ? Collections.synchronizedList(new ArrayList<>()) : v;
      synchronized (l) {
        l.add(latencyMs);
        if (l.size() > MAX_SAMPLES) l.remove(0);
      }
      return l;
    });
  }

  public record Percentiles(String route, int count, long p50, long p95, long p99) {}

  public Percentiles percentiles(String route) {
    List<Long> l = samples.get(route);
    if (l == null || l.isEmpty()) return new Percentiles(route, 0, 0, 0, 0);
    List<Long> sorted;
    synchronized (l) {
      sorted = new ArrayList<>(l);
    }
    Collections.sort(sorted);
    return new Percentiles(route, sorted.size(), pick(sorted, 50), pick(sorted, 95), pick(sorted, 99));
  }

  private static long pick(List<Long> sorted, int p) {
    int idx = Math.min(sorted.size() - 1, (int) Math.ceil(p / 100.0 * sorted.size()) - 1);
    return sorted.get(Math.max(idx, 0));
  }
}
