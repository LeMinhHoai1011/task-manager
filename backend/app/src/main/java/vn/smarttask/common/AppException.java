package vn.smarttask.common;

import org.springframework.http.HttpStatus;

/** Domain exception carrying ErrorEnvelope codes. Manual (no-AI) PH-1. */
public class AppException extends RuntimeException {
  private final String code;
  private final String messageKey;
  private final String reasonCode;
  private final HttpStatus httpStatus;
  // §7 enrichment (all optional; absent fields vanish from the envelope).
  private Boolean reauthRequired;
  private Long retryAfterSeconds;
  private Long resourceVersion;
  private String winningDecision;
  private String resourceState;

  public AppException(String code, String messageKey, String reasonCode, HttpStatus httpStatus, String msg) {
    super(msg);
    this.code = code;
    this.messageKey = messageKey;
    this.reasonCode = reasonCode;
    this.httpStatus = httpStatus;
  }

  public String getCode() { return code; }
  public String getMessageKey() { return messageKey; }
  public String getReasonCode() { return reasonCode; }
  public HttpStatus getHttpStatus() { return httpStatus; }
  public Boolean getReauthRequired() { return reauthRequired; }
  public Long getRetryAfterSeconds() { return retryAfterSeconds; }
  public Long getResourceVersion() { return resourceVersion; }
  public String getWinningDecision() { return winningDecision; }
  public String getResourceState() { return resourceState; }

  public AppException withReauthRequired(boolean v) { this.reauthRequired = v; return this; }
  public AppException withRetryAfterSeconds(long v) { this.retryAfterSeconds = v; return this; }
  public AppException withResourceVersion(long v) { this.resourceVersion = v; return this; }
  public AppException withWinningDecision(String v) { this.winningDecision = v; return this; }
  public AppException withResourceState(String v) { this.resourceState = v; return this; }

  public static AppException invalidSession(String msg) {
    return new AppException("INVALID_SESSION", "auth.session.invalid", "SESSION_INVALID",
        HttpStatus.UNAUTHORIZED, msg);
  }
  public static AppException accountNotActive(String msg) {
    return new AppException("ACCOUNT_NOT_ACTIVE", "auth.account.not_active", "ACCOUNT_NOT_ACTIVE",
        HttpStatus.FORBIDDEN, msg);
  }
  public static AppException accountLocked(String msg) {
    return new AppException("ACCOUNT_LOCKED", "auth.account.locked", "ACCOUNT_LOCKED",
        HttpStatus.LOCKED, msg);
  }
  public static AppException notFound(String msg) {
    return new AppException("RESOURCE_NOT_FOUND", "resource.not_found", "NO_READ_OR_MISSING",
        HttpStatus.NOT_FOUND, msg);
  }
  public static AppException forbidden(String msg) {
    return new AppException("FORBIDDEN", "auth.forbidden", "WRITE_DENIED",
        HttpStatus.FORBIDDEN, msg);
  }
  public static AppException stateConflict(String reason, String msg) {
    return new AppException("STATE_CONFLICT", "state.conflict", reason,
        HttpStatus.CONFLICT, msg);
  }
  public static AppException lastProjectManager(String msg) {
    return new AppException("LAST_PROJECT_MANAGER", "project.manager.last", "LAST_PROJECT_MANAGER",
        HttpStatus.CONFLICT, msg);
  }
  public static AppException reauthRequired(String msg) {
    return new AppException("REAUTH_REQUIRED", "auth.reauth.required", "REAUTH_REQUIRED",
        HttpStatus.valueOf(428), msg).withReauthRequired(true);
  }
  public static AppException versionConflict(String msg) {
    return new AppException("VERSION_CONFLICT", "resource.version.conflict", "VERSION_CONFLICT",
        HttpStatus.CONFLICT, msg);
  }
  /** Version conflict carrying the current resource version (§7 resource_version). */
  public static AppException versionConflict(String msg, long currentVersion) {
    return versionConflict(msg).withResourceVersion(currentVersion);
  }
  public static AppException unprocessable(String reason, String msg) {
    return new AppException("UNPROCESSABLE", "resource.unprocessable", reason,
        HttpStatus.UNPROCESSABLE_ENTITY, msg);
  }
  public static AppException executionLocked(String reason, String msg) {
    return new AppException("EXECUTION_LOCKED", "task.execution.locked", reason,
        HttpStatus.LOCKED, msg);
  }
  public static AppException badRequest(String reason, String msg) {
    return new AppException("BAD_REQUEST", "request.invalid", reason,
        HttpStatus.BAD_REQUEST, msg);
  }
  public static AppException storageUnavailable(String reason, String msg) {
    return new AppException("STORAGE_UNAVAILABLE", "storage.unavailable", reason,
        HttpStatus.SERVICE_UNAVAILABLE, msg);
  }
  /**
   * §7: missing If-Match answers 428 (docs), keeping code IF_MATCH_REQUIRED.
   * (Previously 400.)
   */
  public static AppException ifMatchRequired() {
    return new AppException("IF_MATCH_REQUIRED", "resource.if_match.required",
        "IF_MATCH_REQUIRED", HttpStatus.valueOf(428), "If-Match required");
  }
  /** §7 unified sort: unknown field -> 400, never a silent fallback. */
  public static AppException unsupportedSortField(String field) {
    return new AppException("UNSUPPORTED_SORT_FIELD", "request.sort.unsupported",
        "UNSUPPORTED_SORT_FIELD", HttpStatus.BAD_REQUEST,
        "Unsupported sort field: " + field);
  }
}
