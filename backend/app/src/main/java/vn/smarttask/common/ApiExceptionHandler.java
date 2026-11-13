package vn.smarttask.common;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps AppException to ErrorEnvelope (incl. §7 enrichment fields). */
@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
  public ResponseEntity<ErrorEnvelope> optimisticConflict() {
    return handle(AppException.versionConflict("Resource changed; reload before saving"));
  }
  @ExceptionHandler(AppException.class)
  public ResponseEntity<ErrorEnvelope> handle(AppException ex) {
    return ResponseEntity.status(ex.getHttpStatus()).body(ErrorEnvelope.of(ex, null));
  }
}
