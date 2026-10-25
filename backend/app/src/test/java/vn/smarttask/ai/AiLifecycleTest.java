package vn.smarttask.ai;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Lifecycle matrix: happy path + failure branches + retry rules (pure, no infra). */
class AiLifecycleTest {

  @Test
  void happyPathTransitions() {
    assertTrue(AiJobLifecycle.canTransition(AiJobStatus.QUEUED, AiJobStatus.CONTEXT_BUILDING));
    assertTrue(AiJobLifecycle.canTransition(AiJobStatus.CONTEXT_BUILDING, AiJobStatus.RUNNING));
    assertTrue(AiJobLifecycle.canTransition(AiJobStatus.RUNNING, AiJobStatus.VALIDATING));
    assertTrue(AiJobLifecycle.canTransition(AiJobStatus.VALIDATING, AiJobStatus.REAUTHORIZING));
    assertTrue(AiJobLifecycle.canTransition(AiJobStatus.REAUTHORIZING, AiJobStatus.COMPLETED));
  }

  @Test
  void illegalTransitionsRejected() {
    assertFalse(AiJobLifecycle.canTransition(AiJobStatus.QUEUED, AiJobStatus.COMPLETED));
    assertFalse(AiJobLifecycle.canTransition(AiJobStatus.COMPLETED, AiJobStatus.QUEUED));
    assertFalse(AiJobLifecycle.canTransition(AiJobStatus.FAILED_VALIDATION, AiJobStatus.QUEUED));
    assertFalse(AiJobLifecycle.canTransition(AiJobStatus.CANCELLED_REVOKED, AiJobStatus.QUEUED));
    assertFalse(AiJobLifecycle.canTransition(AiJobStatus.RUNNING, AiJobStatus.COMPLETED));
  }

  @Test
  void retryMatrix() {
    assertTrue(AiJobLifecycle.canRetry(AiJobStatus.FAILED_TIMEOUT, 1, 3));
    assertTrue(AiJobLifecycle.canRetry(AiJobStatus.FAILED_OLLAMA, 2, 3));
    assertFalse(AiJobLifecycle.canRetry(AiJobStatus.FAILED_OLLAMA, 3, 3));
    assertFalse(AiJobLifecycle.canRetry(AiJobStatus.FAILED_VALIDATION, 1, 3));
    assertFalse(AiJobLifecycle.canRetry(AiJobStatus.CANCELLED_REVOKED, 1, 3));
    assertFalse(AiJobLifecycle.canRetry(AiJobStatus.COMPLETED, 1, 3));
  }
}
