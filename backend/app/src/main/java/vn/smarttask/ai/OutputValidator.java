package vn.smarttask.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Output gate: raw model text must be a JSON draft with mandatory title,
 * enum priority, subtask depth &lt;= 1, and assignee with project participation.
 * Anything else -&gt; FAILED_VALIDATION (no retry).
 */
public final class OutputValidator {
  private OutputValidator() {}

  private static final Set<String> PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH", "URGENT");

  public record Draft(String title, String description, String priority, UUID assigneeUserId,
      List<String> subtasks) {}

  public static Draft validate(String rawJson, UUID projectId, ProjectApi projectApi) {
    return validate(rawJson, projectId, projectApi, new ObjectMapper());
  }

  public static Draft validate(String rawJson, UUID projectId, ProjectApi projectApi, ObjectMapper om) {
    JsonNode root;
    try {
      root = om.readTree(rawJson == null ? "" : rawJson);
    } catch (Exception e) {
      throw AiErrors.validationFailed("AI output is not valid JSON");
    }
    if (root == null || !root.isObject()) {
      throw AiErrors.validationFailed("AI output must be a JSON object draft");
    }
    String title = root.has("title") ? root.get("title").asText("").trim() : "";
    if (title.isEmpty()) {
      throw AiErrors.validationFailed("AI draft requires non-blank title");
    }
    String priority = root.has("priority") ? root.get("priority").asText("MEDIUM").trim().toUpperCase() : "MEDIUM";
    if (!PRIORITIES.contains(priority)) {
      throw AiErrors.validationFailed("AI draft priority must be one of " + PRIORITIES);
    }
    String description = root.has("description") ? root.get("description").asText("") : "";
    UUID assignee = null;
    if (root.has("assignee_user_id") && !root.get("assignee_user_id").isNull()) {
      String raw = root.get("assignee_user_id").asText("").trim();
      if (!raw.isEmpty()) {
        try {
          assignee = UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
          throw AiErrors.validationFailed("AI draft assignee_user_id must be a UUID");
        }
        if (projectId == null || projectApi == null || !projectApi.hasParticipation(projectId, assignee)) {
          throw AiErrors.validationFailed("AI draft assignee lacks project participation");
        }
      }
    }
    List<String> subtasks = new ArrayList<>();
    if (root.has("subtasks") && !root.get("subtasks").isNull()) {
      JsonNode subs = root.get("subtasks");
      if (!subs.isArray()) {
        throw AiErrors.validationFailed("AI draft subtasks must be an array");
      }
      for (JsonNode s : subs) {
        if (!s.isObject() || !s.has("title") || s.get("title").asText("").trim().isEmpty()) {
          throw AiErrors.validationFailed("AI draft subtask requires non-blank title");
        }
        // Depth <= 1: nested subtasks forbidden.
        if (s.has("subtasks") && !s.get("subtasks").isNull()
            && s.get("subtasks").isArray() && !s.get("subtasks").isEmpty()) {
          throw AiErrors.validationFailed("AI draft subtask depth must be <= 1");
        }
        subtasks.add(s.get("title").asText("").trim());
      }
    }
    return new Draft(title, description, priority, assignee, List.copyOf(subtasks));
  }
}
