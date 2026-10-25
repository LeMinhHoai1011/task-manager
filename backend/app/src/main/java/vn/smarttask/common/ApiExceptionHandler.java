package vn.smarttask.common;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps AppException to ErrorEnvelope (incl. §7 enrichment fields). */
@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(AppException.class)
  public ResponseEntity<ErrorEnvelope> handle(AppException ex) {
    return ResponseEntity.status(ex.getHttpStatus()).body(ErrorEnvelope.of(ex, null));
  }
}
