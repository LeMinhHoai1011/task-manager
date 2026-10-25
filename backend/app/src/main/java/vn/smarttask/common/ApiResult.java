package vn.smarttask.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Success envelope: data + message_key (i18n key, never rendered text). */
public record ApiResult<T>(
    @JsonProperty("data") T data,
    @JsonProperty("message_key") String messageKey,
    @JsonProperty("timestamp") OffsetDateTime timestamp) {

  public static <T> ApiResult<T> ok(T data, String messageKey) {
    return new ApiResult<>(data, messageKey, OffsetDateTime.now(ZoneOffset.UTC));
  }
}
