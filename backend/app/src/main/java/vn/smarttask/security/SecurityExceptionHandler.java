package vn.smarttask.security;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.smarttask.common.ErrorEnvelope;

/** Maps authorization outcomes to ErrorEnvelope. 404 hides existence when READ is missing. */
@RestControllerAdvice
public class SecurityExceptionHandler {

  public static class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String msg) { super(msg); }
  }

  public static class ForbiddenException extends RuntimeException {
    public ForbiddenException(String msg) { super(msg); }
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ErrorEnvelope> notFound(ResourceNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorEnvelope.of(
        "RESOURCE_NOT_FOUND", "resource.not_found", "NO_READ_OR_MISSING", null, null));
  }

  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<ErrorEnvelope> forbidden(ForbiddenException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorEnvelope.of(
        "FORBIDDEN", "auth.forbidden", "WRITE_DENIED", null, null));
  }
}
