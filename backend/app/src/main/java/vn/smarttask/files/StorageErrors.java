package vn.smarttask.files;

import io.minio.errors.ErrorResponseException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import vn.smarttask.common.AppException;

/**
 * F2.3 storage error mapping. S3 failures must never leak a stack trace
 * (500) to clients: a missing key is 409 {@code FILE_OBJECT_MISSING}, a
 * connectivity failure is 503 {@code STORAGE_UNAVAILABLE}. Only the root
 * cause simple class name is kept in the message, never a stack.
 */
public final class StorageErrors {
  private StorageErrors() {}

  public static boolean isMissing(Exception ex) {
    if (ex instanceof ErrorResponseException ere) {
      String code = ere.errorResponse() == null ? "" : String.valueOf(ere.errorResponse().code());
      return code.contains("NoSuchKey") || code.contains("NoSuchBucket")
          || code.contains("NoSuchVersion");
    }
    return false;
  }

  public static boolean isConnectivity(Exception ex) {
    for (Throwable t = ex; t != null; t = t.getCause()) {
      if (t instanceof ConnectException || t instanceof SocketTimeoutException
          || t instanceof UnknownHostException
          || t instanceof java.net.SocketException
          || t instanceof javax.net.ssl.SSLException) {
        return true;
      }
      String m = String.valueOf(t.getMessage());
      if (m.contains("Connection refused") || m.contains("Unable to execute HTTP request")
          || m.contains("Failed to connect") || m.contains("Connect timed out")
          || m.contains("No route to host") || m.contains("Temporary failure in name resolution")) {
        return true;
      }
    }
    return false;
  }

  /** Map any storage failure to a clean envelope error (no stack). */
  public static AppException map(Exception ex, String objectKey) {
    if (isMissing(ex)) {
      return AppException.stateConflict("FILE_OBJECT_MISSING",
          "Stored object missing for key " + objectKey);
    }
    return unavailable(objectKey, ex);
  }

  public static AppException unavailable(String objectKey, Exception ex) {
    String rootName = ex == null ? "unknown" : ex.getClass().getSimpleName();
    Throwable root = ex;
    for (Throwable t = ex; t != null && t.getCause() != null && t.getCause() != t; t = t.getCause()) {
      root = t.getCause();
    }
    if (root != null && root != ex) rootName = root.getClass().getSimpleName();
    return AppException.storageUnavailable("STORAGE_UNAVAILABLE",
        "Object storage unavailable for key " + objectKey + " (" + rootName + ")");
  }
}
