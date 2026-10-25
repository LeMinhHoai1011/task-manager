package vn.smarttask.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import vn.smarttask.common.AppException;

/** Membership revoked mid-RUNNING -> CANCELLED_REVOKED + redacted suggestion + GET 404 + no retry. */
class AiReauthorizeTest {

  @Test
  void revokeBetweenRunningAndDeliveryCancelsAndRedacts() {
    AiFixture f = new AiFixture(MockOllamaClient.Mode.VALID);
    // Revoke right inside the Ollama call (i.e. between RUNNING and REAUTHORIZING).
    f.ollama.setOnCallHook(() -> f.participants.remove(f.user));

    AppException thrown = assertThrows(AppException.class, f::requestOk);
    assertEquals(404, thrown.getHttpStatus().value());

    assertEquals("CANCELLED_REVOKED", f.jobRef.get().getStatus());
    assertNotNull(f.sugRef.get());
    assertEquals("REDACTED", f.sugRef.get().getStatus());
    assertEquals("{}", f.sugRef.get().getPayload());

    // No retry after revoke.
    AppException retryDenied =
        assertThrows(AppException.class, () -> f.service.retry(f.user, f.jobRef.get().getId()));
    assertEquals(409, retryDenied.getHttpStatus().value());
    assertEquals(1, f.ollama.calls());

    // GET after revoke reads as 404.
    AppException read = assertThrows(AppException.class,
        () -> f.service.status(f.user, f.jobRef.get().getId()));
    assertEquals(404, read.getHttpStatus().value());

    verify(f.audit, atLeastOnce()).appendDenied(eq(f.user), anyString(), anyString(), any(),
        anyString());
    verifyNoInteractions(f.taskService);
  }
}
