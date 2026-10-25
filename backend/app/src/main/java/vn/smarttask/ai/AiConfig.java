package vn.smarttask.ai;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import vn.smarttask.identity.repo.AppUserRepository;
import vn.smarttask.project.repo.ProjectMembershipRepository;

/** Manual AI wiring: read-only ProjectApi, real HTTP Ollama client, injectable clock. */
@Configuration
public class AiConfig {
  @Bean
  ProjectApi aiProjectApi(ProjectMembershipRepository members, AppUserRepository users) {
    return new RepositoryProjectApi(members, users);
  }

  @Bean
  OllamaClient ollamaClient(
      @Value("${smarttask.ollama.base-url:http://localhost:11434}") String baseUrl,
      @Value("${smarttask.ollama.model:llama3.1:8b}") String model,
      @Value("${smarttask.ollama.connect-timeout-ms:5000}") long connectTimeoutMs,
      @Value("${smarttask.ollama.max-retries:1}") int maxRetries) {
    return new HttpOllamaClient(baseUrl, model, connectTimeoutMs, maxRetries);
  }

  @Bean
  java.time.Clock clock() {
    return java.time.Clock.systemUTC();
  }

  /** Short-tx template for the F3 enqueue blocks (validate + enqueue, never held over I/O). */
  @Bean
  org.springframework.transaction.support.TransactionTemplate aiShortTxTemplate(
      org.springframework.transaction.PlatformTransactionManager txm) {
    return new org.springframework.transaction.support.TransactionTemplate(txm);
  }

  /** Seedable default manifests per feature (manual allow-list, deny by default). */
  public static Map<String, List<String>> defaultAllowedFields() {
    return Map.of(
        "TASK_DRAFT", List.of("title", "description", "priority"),
        "SUBTASK_SUGGEST", List.of("title", "parent_task_id"),
        "PRIORITY_SUGGEST", List.of("title", "priority"),
        "DEADLINE_SUGGEST", List.of("title", "due_at"),
        "ASSIGNEE_SUGGEST", List.of("title", "assignee_user_id"),
        "NATURAL_SEARCH", List.of("title"),
        "DAILY_PLANNER", List.of("title", "due_at", "priority"),
        "WEEKLY_REVIEW", List.of("title", "state"));
  }

  public static List<String> defaultForbiddenPatterns() {
    return List.of("password*", "secret*", "*token*", "salary*", "ssn*");
  }
}
