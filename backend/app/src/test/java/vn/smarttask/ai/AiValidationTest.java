package vn.smarttask.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import vn.smarttask.common.AppException;

/** Bad model output -> FAILED_VALIDATION, never retried, no suggestion draft kept. */
class AiValidationTest {

  @Test
  void invalidJsonFailsValidationWithoutRetry() {
    AiFixture f = new AiFixture(MockOllamaClient.Mode.INVALID_JSON);
    var job = f.requestOk();
    assertEquals("FAILED_VALIDATION", job.getStatus());
    assertNull(f.sugRef.get());

    AppException denied =
        assertThrows(AppException.class, () -> f.service.retry(f.user, job.getId()));
    assertEquals(409, denied.getHttpStatus().value());
    assertEquals(1, f.ollama.calls());
    verifyNoInteractions(f.taskService);
  }

  @Test
  void schemaViolationFailsValidationWithoutRetry() {
    AiFixture f = new AiFixture(MockOllamaClient.Mode.INVALID_SCHEMA);
    var job = f.requestOk();
    assertEquals("FAILED_VALIDATION", job.getStatus());

    AppException denied =
        assertThrows(AppException.class, () -> f.service.retry(f.user, job.getId()));
    assertEquals(409, denied.getHttpStatus().value());
    verify(f.suggestions, never()).save(any());
  }
}
