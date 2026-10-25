package vn.smarttask.ai;

import org.springframework.http.HttpStatus;

/** AI error factory: stable codes for AI-01..05. */
public final class AiErrors {
  private AiErrors() {}

  public static vn.smarttask.common.AppException contextDenied(String msg) {
    return new vn.smarttask.common.AppException("AI_CONTEXT_DENIED", "ai.context.denied",
        "AI_CONTEXT_DENIED", HttpStatus.FORBIDDEN, msg);
  }

  public static vn.smarttask.common.AppException validationFailed(String msg) {
    return new vn.smarttask.common.AppException("AI_VALIDATION_FAILED", "ai.output.invalid",
        "AI_VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, msg);
  }

  public static vn.smarttask.common.AppException ollamaDown(String msg) {
    return new vn.smarttask.common.AppException("AI_OLLAMA_DOWN", "ai.ollama.down",
        "AI_OLLAMA_DOWN", HttpStatus.BAD_GATEWAY, msg);
  }

  public static vn.smarttask.common.AppException ollamaTimeout(String msg) {
    return new vn.smarttask.common.AppException("AI_OLLAMA_TIMEOUT", "ai.ollama.timeout",
        "AI_OLLAMA_TIMEOUT", HttpStatus.GATEWAY_TIMEOUT, msg);
  }

  public static vn.smarttask.common.AppException notFound(String msg) {
    // AI-02: other users' jobs (even SA) read as 404 — no existence leak.
    return vn.smarttask.common.AppException.notFound(msg);
  }
}
