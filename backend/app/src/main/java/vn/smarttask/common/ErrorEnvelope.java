package vn.smarttask.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Standard error envelope. Controllers must return this shape.
 * message_key is an i18n key, never rendered text.
 * §7 adds optional fields (absent when null): reauth_required,
 * retry_after_seconds, resource_version, winning_decision, resource_state.
 * Only those five vanish when null; the legacy shape is byte-identical.
 */
public record ErrorEnvelope(
    @JsonProperty("error") ErrorBody error,
    @JsonProperty("correlation_id") String correlationId,
    @JsonProperty("timestamp") OffsetDateTime timestamp) {

  public record ErrorBody(
      @JsonProperty("code") String code,
      @JsonProperty("message_key") String messageKey,
      @JsonProperty("reason_code") String reasonCode,
      @JsonProperty("details") Map<String, Object> details,
      @JsonProperty("reauth_required") @JsonInclude(JsonInclude.Include.NON_NULL) Boolean reauthRequired,
      @JsonProperty("retry_after_seconds") @JsonInclude(JsonInclude.Include.NON_NULL) Long retryAfterSeconds,
      @JsonProperty("resource_version") @JsonInclude(JsonInclude.Include.NON_NULL) Long resourceVersion,
      @JsonProperty("winning_decision") @JsonInclude(JsonInclude.Include.NON_NULL) String winningDecision,
      @JsonProperty("resource_state") @JsonInclude(JsonInclude.Include.NON_NULL) String resourceState) {}

  public static ErrorEnvelope of(String code, String messageKey, String reasonCode,
      Map<String, Object> details, String correlationId) {
    return new ErrorEnvelope(
        new ErrorBody(code, messageKey, reasonCode, details == null ? Map.of() : details,
            null, null, null, null, null),
        correlationId,
        OffsetDateTime.now(java.time.ZoneOffset.UTC));
  }

  /** §7 enriched envelope; null extras vanish from JSON. */
  public static ErrorEnvelope enriched(String code, String messageKey, String reasonCode,
      Map<String, Object> details, String correlationId,
      Boolean reauthRequired, Long retryAfterSeconds, Long resourceVersion,
      String winningDecision, String resourceState) {
    return new ErrorEnvelope(
        new ErrorBody(code, messageKey, reasonCode, details == null ? Map.of() : details,
            reauthRequired, retryAfterSeconds, resourceVersion, winningDecision, resourceState),
        correlationId,
        OffsetDateTime.now(java.time.ZoneOffset.UTC));
  }

  public static ErrorEnvelope of(AppException ex, String correlationId) {
    return enriched(ex.getCode(), ex.getMessageKey(), ex.getReasonCode(), null, correlationId,
        ex.getReauthRequired(), ex.getRetryAfterSeconds(), ex.getResourceVersion(),
        ex.getWinningDecision(), ex.getResourceState());
  }
}
