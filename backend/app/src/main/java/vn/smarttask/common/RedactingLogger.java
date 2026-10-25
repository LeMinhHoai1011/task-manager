package vn.smarttask.common;

import org.slf4j.Logger;

/**
 * PH-7 redacting logger: structured logs carry correlation_id and NEVER
 * token/password/private content. Keys containing these tokens are masked.
 */
public final class RedactingLogger {
  private RedactingLogger() {}

  private static final String[] SENSITIVE_KEYS = {
      "password", "token", "secret", "authorization", "cookie", "otp",
      "recovery_code", "body", "description", "content", "payload"};

  public static boolean isSensitiveKey(String key) {
    if (key == null) return false;
    String k = key.toLowerCase();
    for (String s : SENSITIVE_KEYS) {
      if (k.contains(s)) return true;
    }
    return false;
  }

  public static String mask(String key, String value) {
    if (isSensitiveKey(key)) return "***REDACTED***";
    return value;
  }

  /** Structured info log with correlation id; value masked when key is sensitive. */
  public static void info(Logger log, String correlationId, String event, String key, String value) {
    log.info("correlation_id={} event={} {}={}", correlationId, event, key, mask(key, value));
  }
}
