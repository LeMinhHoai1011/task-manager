package vn.smarttask.ai;

/** AI job lifecycle. Terminal failures: FAILED_TIMEOUT / FAILED_OLLAMA (retryable
 *  while attempt < maxAttempts), FAILED_VALIDATION / CANCELLED_REVOKED (never retry). */
public enum AiJobStatus {
  QUEUED,
  CONTEXT_BUILDING,
  RUNNING,
  VALIDATING,
  REAUTHORIZING,
  COMPLETED,
  FAILED_TIMEOUT,
  FAILED_OLLAMA,
  FAILED_VALIDATION,
  CANCELLED_REVOKED;

  public boolean isTerminal() {
    return this == COMPLETED || this == FAILED_TIMEOUT || this == FAILED_OLLAMA
        || this == FAILED_VALIDATION || this == CANCELLED_REVOKED;
  }

  public boolean isRetryableFailure() {
    return this == FAILED_TIMEOUT || this == FAILED_OLLAMA;
  }
}
