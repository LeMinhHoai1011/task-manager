package vn.smarttask.ai;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 * Pure parser test for the F3 HttpOllamaClient response extractor
 * (no Mockito, no network: real extraction logic, fake bodies).
 */
class HttpOllamaParserTest {
  @Test
  void extractsResponseField() throws Exception {
    String body = "{\"model\":\"m\",\"response\":\"{\\\"title\\\":\\\"T\\\"}\",\"done\":true}";
    assertEquals("{\"title\":\"T\"}", HttpOllamaClient.extractResponse(body));
  }

  @Test
  void unescapesNewlinesAndQuotes() throws Exception {
    String body = "{\"response\":\"a\\nb\\\"c\",\"done\":true}";
    assertEquals("a\nb\"c", HttpOllamaClient.extractResponse(body));
  }

  @Test
  void trailingFieldsDoNotLeakIntoResponse() throws Exception {
    String body = "{\"response\":\"hi\",\"done_reason\":\"stop\",\"done\":true}";
    assertEquals("hi", HttpOllamaClient.extractResponse(body));
  }

  @Test
  void missingFieldIsInvalid() {
    assertThrows(OllamaException.class,
        () -> HttpOllamaClient.extractResponse("{\"done\":true}"));
  }

  @Test
  void unterminatedIsInvalid() {
    assertThrows(OllamaException.class,
        () -> HttpOllamaClient.extractResponse("{\"response\":\"abc"));
  }
}
