package vn.smarttask.security;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 * F0: the hard-coded zero-UUID fake actor must be gone from main sources.
 * Every controller resolves the actor from the SecurityContext principal.
 */
class FakeActorRemovedTest {

  private static final String FAKE_ACTOR = "00000000-0000-0000-0000-000000000000";

  @Test
  void noFakeActorLiteralInMainSources() throws Exception {
    Path root = Paths.get("app", "src", "main", "java");
    if (!Files.isDirectory(root)) {
      root = Paths.get("src", "main", "java");
    }
    assertTrue(Files.isDirectory(root), "main sources not found from " + Paths.get("").toAbsolutePath());
    StringBuilder hits = new StringBuilder();
    try (Stream<Path> walk = Files.walk(root)) {
      for (Path p : walk.filter(f -> f.toString().endsWith(".java")).toList()) {
        if (Files.readString(p).contains(FAKE_ACTOR)) {
          hits.append(p).append("\n");
        }
      }
    }
    assertEquals("", hits.toString(), "Fake actor literal still present:\n" + hits);
  }
}
