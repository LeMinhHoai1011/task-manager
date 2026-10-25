package vn.smarttask.admin;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.smarttask.common.ApiResult;
import vn.smarttask.common.MetricsRegistry;

/**
 * PH-7 health/readiness/liveness. Readiness pings the datasource (fails
 * closed when the DB is unreachable); metrics remain an in-memory stub
 * (labelled as such) until a real registry lands.
 */
@RestController
public class HealthController {

  private final MetricsRegistry metrics = new MetricsRegistry();
  private final DataSource dataSource;

  public HealthController(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @GetMapping("/actuator/health")
  public ResponseEntity<ApiResult<Object>> health() {
    boolean db = ping();
    if (!db) {
      return ResponseEntity.status(503).body(ApiResult.ok(
          Map.of("status", "DOWN", "checks", Map.of("app", "UP", "db", "DOWN")),
          "health.success"));
    }
    return ResponseEntity.ok(ApiResult.ok(
        Map.of("status", "UP", "checks", Map.of("app", "UP", "db", "UP")),
        "health.success"));
  }

  @GetMapping("/api/v1/health/readiness")
  public ResponseEntity<ApiResult<Object>> readiness() {
    boolean db = ping();
    if (!db) {
      return ResponseEntity.status(503).body(ApiResult.ok(
          Map.of("ready", false, "db", "DOWN",
              "checked_at", OffsetDateTime.now(ZoneOffset.UTC).toString()),
          "readiness.success"));
    }
    return ResponseEntity.ok(ApiResult.ok(
        Map.of("ready", true, "db", "UP",
            "checked_at", OffsetDateTime.now(ZoneOffset.UTC).toString()),
        "readiness.success"));
  }

  @GetMapping("/api/v1/health/liveness")
  public ResponseEntity<ApiResult<Object>> liveness() {
    return ResponseEntity.ok(ApiResult.ok(Map.of("alive", true), "liveness.success"));
  }

  /** STUB: in-memory percentiles only; no external registry in V1. */
  @GetMapping("/api/v1/admin/metrics")
  public ResponseEntity<ApiResult<Object>> metrics(@RequestParam(defaultValue = "all") String route) {
    var p = metrics.percentiles(route);
    return ResponseEntity.ok(ApiResult.ok(
        Map.of("route", route, "count", p.count(), "p50", p.p50(), "p95", p.p95(),
            "p99", p.p99(), "note", "in-memory stub"),
        "metrics.success"));
  }

  private boolean ping() {
    try (var c = dataSource.getConnection()) {
      return c.isValid(2);
    } catch (Exception e) {
      return false;
    }
  }
}
