package vn.smarttask.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Real Ollama HTTP client (/api/generate, non-streaming). Kept dependency-free
 * (java.net.http; no Spring imports — ArchUnit-safe) so production wiring
 * stays minimal.
 *
 * <p>F3: explicit connect + read timeouts (config-driven) and a bounded retry
 * on transport DOWN only (never on timeout: a timed-out generation may still
 * be running server-side). No cloud fallback by design (declared gap).
 */
public class HttpOllamaClient implements OllamaClient {
  private final String baseUrl;
  private final String model;
  private final long connectTimeoutMs;
  private final int maxRetries;
  private final HttpClient http;

  public HttpOllamaClient(String baseUrl, String model) {
    this(baseUrl, model, 5000L, 1);
  }

  public HttpOllamaClient(String baseUrl, String model, long connectTimeoutMs, int maxRetries) {
    this.baseUrl = baseUrl == null ? "http://localhost:11434" : baseUrl;
    this.model = model == null ? "llama3.1:8b" : model;
    this.connectTimeoutMs = Math.max(1000L, connectTimeoutMs);
    this.maxRetries = Math.max(0, Math.min(maxRetries, 3));
    this.http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(this.connectTimeoutMs))
        .build();
  }

  @Override
  public String generate(String prompt, long timeoutMs) throws OllamaException {
    String body = "{\"model\":" + json(model) + ",\"prompt\":" + json(prompt)
        + ",\"stream\":false}";
    HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create(baseUrl + "/api/generate"))
        .timeout(Duration.ofMillis(Math.max(1000L, timeoutMs)))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();
    int tries = 0;
    for (;;) {
      try {
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
          throw new OllamaException(OllamaException.Kind.DOWN,
              "ollama http status " + res.statusCode());
        }
        return extractResponse(res.body());
      } catch (OllamaException e) {
        if (e.getKind() == OllamaException.Kind.DOWN && tries < maxRetries) {
          tries++;
          continue;
        }
        throw e;
      } catch (java.net.http.HttpTimeoutException e) {
        throw new OllamaException(OllamaException.Kind.TIMEOUT, "ollama timeout", e);
      } catch (java.io.IOException e) {
        if (tries < maxRetries) {
          tries++;
          continue;
        }
        throw new OllamaException(OllamaException.Kind.DOWN, "ollama unreachable", e);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new OllamaException(OllamaException.Kind.TIMEOUT, "ollama interrupted", e);
      }
    }
  }

  /** Minimal extraction of the top-level "response" string (escape-aware). */
  static String extractResponse(String raw) throws OllamaException {
    if (raw == null) throw new OllamaException(OllamaException.Kind.INVALID, "empty body");
    int idx = raw.indexOf("\"response\"");
    if (idx < 0) throw new OllamaException(OllamaException.Kind.INVALID, "no response field");
    int colon = raw.indexOf(':', idx);
    if (colon < 0) throw new OllamaException(OllamaException.Kind.INVALID, "bad response field");
    int q = raw.indexOf('"', colon);
    if (q < 0) throw new OllamaException(OllamaException.Kind.INVALID, "bad response field");
    StringBuilder sb = new StringBuilder();
    for (int i = q + 1; i < raw.length(); i++) {
      char c = raw.charAt(i);
      if (c == '\\' && i + 1 < raw.length()) {
        char n = raw.charAt(++i);
        switch (n) {
          case 'n' -> sb.append('\n');
          case 'r' -> sb.append('\r');
          case 't' -> sb.append('\t');
          case 'u' -> {
            if (i + 4 < raw.length()) {
              try {
                sb.append((char) Integer.parseInt(raw.substring(i + 1, i + 5), 16));
                i += 4;
              } catch (NumberFormatException e) {
                sb.append('u');
              }
            } else {
              sb.append('u');
            }
          }
          default -> sb.append(n);
        }
      } else if (c == '"') {
        return sb.toString();
      } else {
        sb.append(c);
      }
    }
    throw new OllamaException(OllamaException.Kind.INVALID, "unterminated response field");
  }

  private static String json(String s) {
    return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
        .replace("\n", "\\n").replace("\r", "\\r") + "\"";
  }
}
