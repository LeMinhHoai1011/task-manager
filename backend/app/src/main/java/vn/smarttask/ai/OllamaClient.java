package vn.smarttask.ai;

/** Minimal Ollama port. Production: HttpOllamaClient. Tests: MockOllamaClient. */
public interface OllamaClient {
  /** Returns raw model output. Throws on timeout / outage / invalid transport. */
  String generate(String prompt, long timeoutMs) throws OllamaException;
}
