package vn.smarttask.security;

import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestHeader;
import vn.smarttask.ai.AiController;
import vn.smarttask.calendar.CalendarController;
import vn.smarttask.collab.CollabController;
import vn.smarttask.files.FileController;
import vn.smarttask.identity.AuthController;
import vn.smarttask.notify.NotificationController;
import vn.smarttask.project.ProjectController;
import vn.smarttask.reports.ReportController;
import vn.smarttask.task.TaskController;
import vn.smarttask.workflow.WorkflowController;
import vn.smarttask.workspace.WorkspaceController;

/**
 * F0: no controller trusts client identity headers. Scans controller method
 * parameters for X-User-Id / X-Is-Admin / X-Has-Grant / X-Reauth (exact).
 * X-Reauth-Ticket (a consumed ticket id bound to the principal) is allowed.
 */
class ClientHeaderTrustTest {

  private static final List<String> BANNED = List.of(
      "X-User-Id", "X-Is-Admin", "X-Has-Grant", "X-Reauth");

  private static final List<Class<?>> CONTROLLERS = List.of(
      AuthController.class, AiController.class, TaskController.class,
      WorkflowController.class, FileController.class, WorkspaceController.class,
      ProjectController.class, ReportController.class, CalendarController.class,
      NotificationController.class, CollabController.class);

  @Test
  void noControllerAcceptsSpoofableIdentityHeaders() {
    StringBuilder violations = new StringBuilder();
    for (Class<?> c : CONTROLLERS) {
      for (var m : c.getDeclaredMethods()) {
        for (Parameter p : m.getParameters()) {
          RequestHeader h = p.getAnnotation(RequestHeader.class);
          if (h != null && BANNED.contains(h.value())) {
            violations.append(c.getSimpleName()).append("#").append(m.getName())
                .append(" trusts ").append(h.value()).append("\n");
          }
        }
      }
    }
    assertEquals("", violations.toString(), "Spoofable header trust found:\n" + violations);
  }

  @Test
  void noSpoofableHeaderLiteralsInMainSources() throws Exception {
    Path root = Paths.get("app", "src", "main", "java");
    if (!Files.isDirectory(root)) {
      root = Paths.get("src", "main", "java");
    }
    assertTrue(Files.isDirectory(root), "main sources not found from " + Paths.get("").toAbsolutePath());
    StringBuilder hits = new StringBuilder();
    try (Stream<Path> walk = Files.walk(root)) {
      for (Path p : walk.filter(f -> f.toString().endsWith(".java")).toList()) {
        String src = Files.readString(p);
        for (String banned : BANNED) {
          // "X-Reauth" must not match the allowed "X-Reauth-Ticket".
          String probe = "X-Reauth".equals(banned)
              ? src.replace("X-Reauth-Ticket", "")
              : src;
          if (probe.contains("\"" + banned + "\"")) {
            hits.append(p).append(" contains ").append(banned).append("\n");
          }
        }
      }
    }
    assertEquals("", hits.toString(), "Banned header literals in main:\n" + hits);
  }
}
