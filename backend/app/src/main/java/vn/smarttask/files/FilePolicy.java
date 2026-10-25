package vn.smarttask.files;

import java.util.Locale;
import java.util.Set;

/**
 * PH-3 pure file policy: allowlist content-type + max size from system_setting.
 * No AI, no infrastructure imports.
 */
public final class FilePolicy {
  private FilePolicy() {}

  public record PolicyResult(boolean allowed, String reasonCode) {
    public static PolicyResult ok() { return new PolicyResult(true, "OK"); }
    public static PolicyResult deny(String reason) { return new PolicyResult(false, reason); }
  }

  public static PolicyResult checkUpload(String declaredContentType, long declaredSizeBytes,
      Set<String> allowedContentTypes, long maxBytes, String taskState) {
    if ("PENDING_APPROVAL".equals(taskState)) {
      return PolicyResult.deny("EXECUTION_LOCKED_PENDING_APPROVAL");
    }
    if (declaredContentType == null
        || !allowedContentTypes.contains(declaredContentType.toLowerCase(Locale.ROOT))) {
      return PolicyResult.deny("FILE_TYPE_NOT_ALLOWED");
    }
    if (declaredSizeBytes <= 0 || declaredSizeBytes > maxBytes) {
      return PolicyResult.deny("FILE_TOO_LARGE");
    }
    return PolicyResult.ok();
  }

  public static long maxBytesFromMb(long mb) {
    return mb * 1024L * 1024L;
  }

  /** Scan stub: no engine configured -> UNSUPPORTED. Must be stated as NOT protected from malware. */
  public static String scanStub(boolean engineConfigured) {
    return engineConfigured ? "PENDING" : "UNSUPPORTED";
  }
}
