package vn.smarttask.ai;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * CODE-ONLY (not run in CI here): exercises the real {@link HttpOllamaClient}
 * against a local Ollama machine.
 *
 * <p>Requires: bare-metal/GPU host with Ollama serving the configured model
 * (see DEC-41), e.g. {@code ollama serve} + {@code ollama pull llama3.1:8b}
 * reachable at {@code http://localhost:11434}. This sandbox has no
 * machine/GPU/Docker, so the test stays disabled.
 */
@Disabled("needs-ollama-machine: requires local Ollama per DEC-41")
class HttpOllamaClientLiveTest {

  @Test
  void generateReturnsNonBlank() throws Exception {
    HttpOllamaClient client = new HttpOllamaClient("http://localhost:11434", "llama3.1:8b");
    String out = client.generate("Respond with: {\"title\":\"hi\"}", 60_000L);
    org.junit.jupiter.api.Assertions.assertNotNull(out);
    org.junit.jupiter.api.Assertions.assertFalse(out.isBlank());
  }
}
