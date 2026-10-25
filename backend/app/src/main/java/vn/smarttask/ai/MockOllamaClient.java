package vn.smarttask.ai;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test double: returns a valid suggestion or injects timeout/down/invalid modes.
 * No network, no GPU, no Docker. A hook can flip external state mid-RUNNING
 * (e.g. revoke membership) to exercise REAUTHORIZING.
 */
public class MockOllamaClient implements OllamaClient {
  public enum Mode { VALID, TIMEOUT, DOWN, INVALID_JSON, INVALID_SCHEMA }

  private volatile Mode mode;
  private volatile Runnable onCallHook;
  private final AtomicInteger calls = new AtomicInteger();

  public MockOllamaClient(Mode mode) {
    this.mode = mode;
  }

  public void setMode(Mode mode) { this.mode = mode; }

  public void setOnCallHook(Runnable hook) { this.onCallHook = hook; }

  public int calls() { return calls.get(); }

  @Override
  public String generate(String prompt, long timeoutMs) throws OllamaException {
    calls.incrementAndGet();
    Runnable hook = onCallHook;
    if (hook != null) hook.run();
    return switch (mode) {
      case TIMEOUT -> throw new OllamaException(OllamaException.Kind.TIMEOUT, "mock ollama timeout");
      case DOWN -> throw new OllamaException(OllamaException.Kind.DOWN, "mock ollama down");
      case INVALID_JSON -> "this is not json {{{";
      case INVALID_SCHEMA -> "{\"description\":\"missing title\",\"priority\":\"SUPER\"}";
      case VALID -> "{\"title\":\"Draft from AI\",\"description\":\"draft desc\","
          + "\"priority\":\"HIGH\",\"subtasks\":[{\"title\":\"sub 1\"}]}";
    };
  }
}
