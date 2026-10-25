package vn.smarttask.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * CODE-ONLY (not run in CI here): latency benchmark for the Ollama round-trip.
 *
 * <p>Requires: Ollama machine per DEC-41 (bare-metal/GPU, model pre-pulled).
 * Reports p50/p95 over 20 generations and asserts p95 stays within the
 * configured job timeout budget. Disabled here: no machine/GPU/Docker.
 */
@Disabled("needs-ollama-machine: latency benchmark requires Ollama host per DEC-41")
class AiLatencyBenchmarkTest {

  @Test
  void reportP50P95WithinBudget() throws Exception {
    HttpOllamaClient client = new HttpOllamaClient("http://localhost:11434", "llama3.1:8b");
    List<Long> samples = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      long start = System.nanoTime();
      client.generate("Respond with: {\"title\":\"bench\"}", 60_000L);
      samples.add((System.nanoTime() - start) / 1_000_000L);
    }
    Collections.sort(samples);
    long p50 = samples.get(samples.size() / 2);
    long p95 = samples.get((int) (samples.size() * 0.95) - 1);
    System.out.println("AI latency ms: p50=" + p50 + " p95=" + p95);
    org.junit.jupiter.api.Assertions.assertTrue(p95 < 60_000L, "p95 exceeds timeout budget");
  }
}
