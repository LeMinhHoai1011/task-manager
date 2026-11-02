package vn.smarttask.task.service;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import vn.smarttask.common.AppException;
import vn.smarttask.common.UuidV7;
import vn.smarttask.common.entity.IdempotencyRecord;
import vn.smarttask.common.repo.IdempotencyRecordRepository;
import vn.smarttask.task.entity.Task;
import vn.smarttask.task.repo.TaskRepository;

/** Uses the existing idempotency store; transaction lock serializes concurrent retries. */
@Service
public class PersonalTaskCreationService {
  private final PersonalTaskService personal;
  private final IdempotencyRecordRepository records;
  private final TaskRepository tasks;
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  public PersonalTaskCreationService(PersonalTaskService personal, IdempotencyRecordRepository records,
      TaskRepository tasks, JdbcTemplate jdbc, ObjectMapper json) {
    this.personal = personal; this.records = records; this.tasks = tasks; this.jdbc = jdbc; this.json = json;
  }
  @Transactional
  public Task create(UUID actor, String title, String key) {
    if (key == null || key.isBlank() || key.length() > 200)
      throw AppException.badRequest("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key of 1–200 characters required");
    String scoped = "personal.create:" + actor + ":" + key;
    jdbc.execute(connection -> {
      var statement = connection.prepareStatement("select pg_advisory_xact_lock(hashtextextended(?, 0))");
      statement.setString(1, scoped); return statement;
    }, (org.springframework.jdbc.core.PreparedStatementCallback<Void>) statement -> { statement.execute(); return null; });
    var existing = records.findByKey(scoped);
    if (existing.isPresent()) {
      try {
        var payload = json.readTree(existing.get().getResponseBody());
        if (!payload.path("title").asText().equals(title))
          throw AppException.stateConflict("IDEMPOTENCY_CONFLICT", "Key already used with different input");
        return tasks.findById(UUID.fromString(payload.path("taskId").asText()))
            .filter(t -> actor.equals(t.getOwnerUserId()))
            .orElseThrow(() -> AppException.notFound("Task not found"));
      } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("Invalid idempotency record", e); }
    }
    Task task = personal.create(actor, title);
    var record = new IdempotencyRecord(UuidV7.generate(), scoped, actor);
    record.setStatusCode(200);
    try { record.setResponseBody(json.writeValueAsString(java.util.Map.of("taskId", task.getId(), "title", title))); }
    catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException(e); }
    records.save(record);
    return task;
  }
}
