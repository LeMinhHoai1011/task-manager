package vn.smarttask.ai;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Pure lifecycle guard (no Spring / JPA imports — ArchUnit-safe).
 * QUEUED -> CONTEXT_BUILDING -> RUNNING -> VALIDATING -> REAUTHORIZING -> COMPLETED,
 * with FAILED_TIMEOUT / FAILED_OLLAMA branches (retry iff attempt &lt; maxAttempts)
 * and FAILED_VALIDATION / CANCELLED_REVOKED (never retry).
 */
public final class AiJobLifecycle {
  private AiJobLifecycle() {}

  private static final Map<AiJobStatus, Set<AiJobStatus>> ALLOWED = new EnumMap<>(AiJobStatus.class);

  static {
    ALLOWED.put(AiJobStatus.QUEUED, EnumSet.of(AiJobStatus.CONTEXT_BUILDING, AiJobStatus.CANCELLED_REVOKED));
    ALLOWED.put(AiJobStatus.CONTEXT_BUILDING, EnumSet.of(AiJobStatus.RUNNING, AiJobStatus.CANCELLED_REVOKED));
    ALLOWED.put(AiJobStatus.RUNNING, EnumSet.of(AiJobStatus.VALIDATING, AiJobStatus.FAILED_TIMEOUT,
        AiJobStatus.FAILED_OLLAMA, AiJobStatus.CANCELLED_REVOKED));
    ALLOWED.put(AiJobStatus.VALIDATING, EnumSet.of(AiJobStatus.REAUTHORIZING, AiJobStatus.FAILED_VALIDATION,
        AiJobStatus.CANCELLED_REVOKED));
    ALLOWED.put(AiJobStatus.REAUTHORIZING, EnumSet.of(AiJobStatus.COMPLETED, AiJobStatus.CANCELLED_REVOKED));
    ALLOWED.put(AiJobStatus.COMPLETED, EnumSet.noneOf(AiJobStatus.class));
    ALLOWED.put(AiJobStatus.FAILED_TIMEOUT, EnumSet.of(AiJobStatus.QUEUED));
    ALLOWED.put(AiJobStatus.FAILED_OLLAMA, EnumSet.of(AiJobStatus.QUEUED));
    ALLOWED.put(AiJobStatus.FAILED_VALIDATION, EnumSet.noneOf(AiJobStatus.class));
    ALLOWED.put(AiJobStatus.CANCELLED_REVOKED, EnumSet.noneOf(AiJobStatus.class));
  }

  public static boolean canTransition(AiJobStatus from, AiJobStatus to) {
    Set<AiJobStatus> next = ALLOWED.get(from);
    return next != null && next.contains(to);
  }

  /** Retry allowed only for Ollama/timeout failures while attempt &lt; maxAttempts. */
  public static boolean canRetry(AiJobStatus status, int attempt, int maxAttempts) {
    return status.isRetryableFailure() && attempt < maxAttempts;
  }
}
