package vn.smarttask.common;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class HardeningUnitTest {
  @Test
  void percentilesComputed() {
    MetricsRegistry m = new MetricsRegistry();
    for (int i = 1; i <= 100; i++) m.record("GET /tasks", i);
    var p = m.percentiles("GET /tasks");
    assertEquals(100, p.count());
    assertEquals(50, p.p50());
    assertEquals(95, p.p95());
    assertEquals(99, p.p99());
  }

  @Test
  void sensitiveKeysMasked() {
    assertTrue(RedactingLogger.isSensitiveKey("password_hash"));
    assertTrue(RedactingLogger.isSensitiveKey("Authorization"));
    assertTrue(RedactingLogger.isSensitiveKey("session_token"));
    assertTrue(RedactingLogger.isSensitiveKey("task_body"));
    assertFalse(RedactingLogger.isSensitiveKey("task_id"));
    assertFalse(RedactingLogger.isSensitiveKey("correlation_id"));
    assertEquals("***REDACTED***", RedactingLogger.mask("password", "secret123"));
    assertEquals("visible", RedactingLogger.mask("task_id", "visible"));
  }
}
