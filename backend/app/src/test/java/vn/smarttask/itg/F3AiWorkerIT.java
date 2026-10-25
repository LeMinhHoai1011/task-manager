package vn.smarttask.itg;

import static org.junit.jupiter.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import vn.smarttask.ai.HttpOllamaClient;
import vn.smarttask.ai.AiFeature;
import vn.smarttask.ai.MockOllamaClient;
import vn.smarttask.ai.entity.AiContextManifest;
import vn.smarttask.ai.entity.AiJob;
import vn.smarttask.ai.repo.AiContextManifestRepository;
import vn.smarttask.ai.repo.AiJobRepository;
import vn.smarttask.ai.repo.AiSuggestionRepository;
import vn.smarttask.ai.service.AiJobRunner;
import vn.smarttask.ai.service.AiJobStore;
import vn.smarttask.ai.service.AiService;
import vn.smarttask.common.AppException;
import vn.smarttask.common.UuidV7;

/**
 * F3 AiWorkerIT (real PostgreSQL): Ollama runs OUTSIDE any DB tx —
 * timeout via a real JDK HttpServer (fake Ollama endpoint, real HTTP timeout),
 * offline down graceful, revoke mid-job -> CANCELLED_REVOKED + redact,
 * no-mutate (Task rows untouched). No Mockito in the HTTP paths.
 */
@TestPropertySource(properties = {"smarttask.workers.enabled=false"})
class F3AiWorkerIT extends ItgBase {

  static final MockOllamaClient MOCK = new MockOllamaClient(MockOllamaClient.Mode.VALID);

  @TestConfiguration
  static class MockOllama {
    @Bean
    @Primary
    MockOllamaClient mockOllama() {
      return MOCK;
    }
  }

  @Autowired AiService ai;
  @Autowired AiJobRunner runner;
  @Autowired AiJobStore store;
  @Autowired AiJobRepository aiJobs;
  @Autowired AiSuggestionRepository suggestions;
  @Autowired AiContextManifestRepository manifests;
  @Autowired vn.smarttask.ai.ProjectApi projectApi;
  @Autowired com.fasterxml.jackson.databind.ObjectMapper objectMapper;
  @Autowired java.time.Clock clock;

  private UUID[] projectFixture(String prefix) {
    MOCK.setMode(MockOllamaClient.Mode.VALID);
    MOCK.setOnCallHook(null);
    var mgr = activeUser(prefix + "mgr", "Secret123!");
    var user = activeUser(prefix + "usr", "Secret123!");
    UUID ws = mkWorkspace(mgr.getId(), prefix);
    var p = mkActiveProject(mgr.getId(), ws, prefix);
    addWsMember(ws, user.getId(), "MEMBER");
    addProjectMember(mgr.getId(), p.getId(), user.getId(), "MEMBER");
    AiContextManifest m = new AiContextManifest("TASK_DRAFT");
    m.setAllowedFields("[\"title\",\"description\",\"priority\",\"assignee_user_id\",\"subtasks\"]");
    m.setForbiddenPatterns("[\"password*\",\"secret*\"]");
    m.setEnabled(true);
    manifests.save(m);
    return new UUID[]{mgr.getId(), user.getId(), p.getId()};
  }

  @Test
  void requestCompletesViaWorkerOutsideTx() {
    var ids = projectFixture("f3ai1");
    UUID user = ids[1], project = ids[2];
    long tasksBefore = taskRepo.count();
    var job = ai.request(user, AiFeature.TASK_DRAFT, project, "draft please",
        List.of("title"), "f3ai-ok-" + tag());
    assertEquals("COMPLETED", job.getStatus());
    var sug = suggestions.findByJobId(job.getId()).orElseThrow();
    assertEquals("DRAFT", sug.getStatus());
    assertTrue(sug.getPayload().contains("Draft from AI"));
    assertEquals(tasksBefore, taskRepo.count(), "AI must never mutate tasks");
  }

  @Test
  void ollamaDownFailsGracefulCoreUnaffected() {
    var ids = projectFixture("f3ai2");
    UUID user = ids[1], project = ids[2];
    MOCK.setMode(MockOllamaClient.Mode.DOWN);
    var job = ai.request(user, AiFeature.TASK_DRAFT, project, "draft please",
        List.of("title"), "f3ai-down-" + tag());
    assertEquals("FAILED_OLLAMA", job.getStatus());
    // retryable: flip up, retry succeeds
    MOCK.setMode(MockOllamaClient.Mode.VALID);
    var retried = ai.retry(user, job.getId());
    assertEquals("COMPLETED", retried.getStatus());
    assertEquals(2, retried.getAttempt());
  }

  @Test
  void httpTimeoutViaFakeOllamaServer() throws Exception {
    var ids = projectFixture("f3ai3");
    UUID user = ids[1], project = ids[2];
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/api/generate", ex -> {
      ex.getRequestBody().readAllBytes();
      try {
        Thread.sleep(5000);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
      }
      byte[] body = "{\"response\":\"slow\",\"done\":true}".getBytes(StandardCharsets.UTF_8);
      ex.getResponseHeaders().set("Content-Type", "application/json");
      ex.sendResponseHeaders(200, body.length);
      try (OutputStream os = ex.getResponseBody()) {
        os.write(body);
      }
    });
    server.start();
    try {
      AiJob job = new AiJob(UuidV7.generate(), user, "TASK_DRAFT", "f3ai-tmo-" + tag());
      job.setProjectId(project);
      job.setInputHint("hi");
      job.setTimeoutMs(1500L);
      aiJobs.save(job);

      var http = new HttpOllamaClient(
          "http://localhost:" + server.getAddress().getPort(), "test-model", 2000L, 0);
      var httpRunner = new AiJobRunner(store, aiJobs, manifests, projectApi, http,
          objectMapper, clock);
      long start = System.currentTimeMillis();
      var done = httpRunner.execute(job.getId(), user);
      long elapsed = System.currentTimeMillis() - start;
      assertEquals("FAILED_TIMEOUT", done.getStatus());
      assertTrue(elapsed < 4500, "client timeout must fire before the 5s server sleep, took "
          + elapsed + "ms");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void httpValidDraftParsesThroughRealClient() throws Exception {
    var ids = projectFixture("f3ai4");
    UUID user = ids[1], project = ids[2];
    String draft = "{\"title\":\"HTTP draft\",\"description\":\"d\",\"priority\":\"HIGH\"}";
    String body = "{\"response\":" + jsonQuote(draft) + ",\"done\":true}";
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/api/generate", ex -> {
      ex.getRequestBody().readAllBytes();
      byte[] out = body.getBytes(StandardCharsets.UTF_8);
      ex.getResponseHeaders().set("Content-Type", "application/json");
      ex.sendResponseHeaders(200, out.length);
      try (OutputStream os = ex.getResponseBody()) {
        os.write(out);
      }
    });
    server.start();
    try {
      AiJob job = new AiJob(UuidV7.generate(), user, "TASK_DRAFT", "f3ai-http-" + tag());
      job.setProjectId(project);
      job.setInputHint("hi");
      job.setTimeoutMs(10000L);
      aiJobs.save(job);

      var http = new HttpOllamaClient(
          "http://localhost:" + server.getAddress().getPort(), "test-model", 2000L, 1);
      var httpRunner = new AiJobRunner(store, aiJobs, manifests, projectApi, http,
          objectMapper, clock);
      var done = httpRunner.execute(job.getId(), user);
      assertEquals("COMPLETED", done.getStatus());
      var sug = suggestions.findByJobId(job.getId()).orElseThrow();
      assertTrue(sug.getPayload().contains("HTTP draft"));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void revokeMidJobCancelsAndRedacts() {
    var ids = projectFixture("f3ai5");
    UUID mgr = ids[0], user = ids[1], project = ids[2];
    MOCK.setOnCallHook(() -> projects.removeMember(mgr, project, user));

    AppException thrown = assertThrows(AppException.class, () -> ai.request(user,
        AiFeature.TASK_DRAFT, project, "draft please", List.of("title"), "f3ai-rev-" + tag()));
    assertEquals(404, thrown.getHttpStatus().value());

    var job = aiJobs.findAll().stream()
        .filter(j -> user.equals(j.getPrincipalUserId()))
        .max((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt())).orElseThrow();
    assertEquals("CANCELLED_REVOKED", job.getStatus());
    var sug = suggestions.findByJobId(job.getId()).orElseThrow();
    assertEquals("REDACTED", sug.getStatus());
    assertEquals("{}", sug.getPayload());
    // read-after-revoke is 404; retry is forbidden
    AppException read = assertThrows(AppException.class, () -> ai.status(user, job.getId()));
    assertEquals(404, read.getHttpStatus().value());
    AppException retry = assertThrows(AppException.class, () -> ai.retry(user, job.getId()));
    assertEquals(409, retry.getHttpStatus().value());
  }

  private static String jsonQuote(String s) {
    return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
