package vn.smarttask.ai;

/** Ollama failure modes. TIMEOUT/DOWN are retryable; INVALID means bad output. */
public class OllamaException extends Exception {
  public enum Kind { TIMEOUT, DOWN, INVALID }

  private final Kind kind;

  public OllamaException(Kind kind, String msg) {
    super(msg);
    this.kind = kind;
  }

  public OllamaException(Kind kind, String msg, Throwable cause) {
    super(msg, cause);
    this.kind = kind;
  }

  public Kind getKind() { return kind; }
}
