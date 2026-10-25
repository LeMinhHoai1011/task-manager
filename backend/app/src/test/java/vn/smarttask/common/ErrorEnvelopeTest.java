package vn.smarttask.common;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ErrorEnvelopeTest {
  @Test
  void envelopeShape() {
    var e = ErrorEnvelope.of("RESOURCE_NOT_FOUND", "resource.not_found", "NO_READ_OR_MISSING",
        Map.of("id", "123"), "cid-1");
    assertEquals("RESOURCE_NOT_FOUND", e.error().code());
    assertEquals("resource.not_found", e.error().messageKey());
    assertEquals("NO_READ_OR_MISSING", e.error().reasonCode());
    assertEquals("123", e.error().details().get("id"));
    assertEquals("cid-1", e.correlationId());
    assertNotNull(e.timestamp());
  }
}
