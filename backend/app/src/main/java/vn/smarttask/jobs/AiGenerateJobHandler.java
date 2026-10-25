package vn.smarttask.jobs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.smarttask.ai.entity.AiJob;
import vn.smarttask.ai.service.AiJobRunner;
import vn.smarttask.common.AppException;
import vn.smarttask.common.entity.BackgroundJob;

/**
 * AI_GENERATE -> AiJobRunner (Ollama outside any tx). A revoked scope leaves
 * the AI job CANCELLED_REVOKED and cancels this background job (no retry).
 */
@Component
public class AiGenerateJobHandler implements JobHandler {
  private final AiJobRunner runner;
  private final ObjectMapper om;

  public AiGenerateJobHandler(AiJobRunner runner, ObjectMapper om) {
    this.runner = runner; this.om = om;
  }

  @Override
  public String kind() { return "AI_GENERATE"; }

  @Override
  public void handle(BackgroundJob job, String owner) {
    JsonNode payload = payloadOf(job);
    if (!payload.hasNonNull("ai_job_id")) {
      throw AppException.badRequest("AI_JOB_ID_MISSING", "AI_GENERATE needs ai_job_id");
    }
    UUID aiJobId = UUID.fromString(payload.get("ai_job_id").asText());
    try {
      AiJob current = runner.loadAny(aiJobId);
      runner.execute(aiJobId, current.getPrincipalUserId());
    } catch (AppException ae) {
      // Revoked mid-job surfaces as 404 after CANCELLED_REVOKED: cancel, never retry.
      try {
        AiJob cur = runner.loadAny(aiJobId);
        if ("CANCELLED_REVOKED".equals(cur.getStatus())) {
          throw new JobRevokedException("AI_REVOKED");
        }
      } catch (JobRevokedException rev) {
        throw rev;
      } catch (AppException ignored) {
        // row gone: fall through to the original failure mapping
      }
      throw ae;
    }
  }

  private JsonNode payloadOf(BackgroundJob job) {
    try {
      return om.readTree(job.getPayload() == null ? "{}" : job.getPayload());
    } catch (Exception e) {
      return om.createObjectNode();
    }
  }
}
