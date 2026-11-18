package vn.smarttask.task.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.smarttask.common.*;
import vn.smarttask.common.entity.IdempotencyRecord;
import vn.smarttask.common.repo.IdempotencyRecordRepository;
import vn.smarttask.common.service.TokenHash;
import vn.smarttask.security.PolicyEnforcer;
import vn.smarttask.task.entity.Task;
import vn.smarttask.task.repo.TaskRepository;
import vn.smarttask.workflow.service.WorkflowService;

@Service
public class TaskBatchItemService {
  private final TaskRepository tasks;
  private final TaskService service;
  private final WorkflowService workflow;
  private final PolicyEnforcer policy;
  private final IdempotencyRecordRepository records;
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public TaskBatchItemService(TaskRepository tasks, TaskService service, WorkflowService workflow,
      PolicyEnforcer policy, IdempotencyRecordRepository records, JdbcTemplate jdbc, ObjectMapper json) {
    this.tasks = tasks; this.service = service; this.workflow = workflow; this.policy = policy;
    this.records = records; this.jdbc = jdbc; this.json = json;
  }

  /** Bind a request key to the complete payload, including batches with no overlapping IDs. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void reserve(UUID actor, TaskBatchService.Request request, String key) {
    String scope = "task.batch:" + actor + ":" + TokenHash.sha256(key);
    jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))", Object.class, scope);
    try {
      String hash = TokenHash.sha256(json.writeValueAsString(request));
      var previous = records.findByKey(scope);
      if (previous.isPresent()) {
        if (!hash.equals(json.readTree(previous.get().getResponseBody()).path("hash").asText()))
          throw AppException.stateConflict("IDEMPOTENCY_CONFLICT", "Key already used with different input");
        return;
      }
      var record = new IdempotencyRecord(UuidV7.generate(), scope, actor);
      record.setResponseBody(json.writeValueAsString(Map.of("hash", hash)));
      records.save(record);
    } catch (JsonProcessingException e) { throw new IllegalStateException("Invalid batch request record", e); }
  }

  /** Mutation and replay record commit together, independently for each task. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public TaskBatchService.Result apply(UUID actor, TaskBatchService.Request request,
      TaskBatchService.Item item, String key) {
    String scope = "task.batch:" + actor + ":" + TokenHash.sha256(key) + ":" + item.id();
    jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))", Object.class, scope);
    // Lock before policy reads populate the persistence context. Otherwise a waiter
    // can retain an old managed version even after SELECT FOR UPDATE obtains its lock.
    Task task = tasks.findByIdForUpdate(item.id()).orElseThrow(() -> AppException.notFound("Task not found"));
    authorize(actor, item.id(), request.action());
    try {
      String hash = TokenHash.sha256(json.writeValueAsString(request));
      var previous = records.findByKey(scope);
      if (previous.isPresent()) {
        var saved = json.readTree(previous.get().getResponseBody());
        if (!hash.equals(saved.path("hash").asText()))
          throw AppException.stateConflict("IDEMPOTENCY_CONFLICT", "Key already used with different input");
        return json.treeToValue(saved.path("result"), TaskBatchService.Result.class);
      }
      if (task.getProjectId() == null || task.getDeletedAt() != null)
        throw AppException.notFound("Task not found");
      if (task.getVersion() != item.version())
        throw AppException.versionConflict("Stale version", task.getVersion());
      task = switch (request.action()) {
        case START -> workflow.start(actor, item.id(), item.version());
        case SUBMIT -> workflow.submit(actor, item.id(), item.version()).task();
        case ASSIGN -> {
          service.assign(actor, item.id(), UUID.fromString(request.value()), item.version());
          yield task;
        }
        case PRIORITY, ADD_TAG, REMOVE_TAG -> service.batchMetadata(actor, item.id(),
            request.action(), request.value(), item.version());
        case DELETE -> service.softDelete(actor, item.id());
      };
      var result = new TaskBatchService.Result(item.id(), true, task.getState(),
          (long) task.getVersion(), null, null, 200);
      var record = new IdempotencyRecord(UuidV7.generate(), scope, actor);
      record.setStatusCode(200);
      record.setResponseBody(json.writeValueAsString(Map.of("hash", hash, "result", result)));
      records.save(record);
      return result;
    } catch (JsonProcessingException e) { throw new IllegalStateException("Invalid batch replay record", e); }
  }

  private void authorize(UUID actor, UUID id, TaskBatchService.Action action) {
    // Write policy also applies on replay; DELETE replay can refer to a tombstone.
    switch (action) {
      case START -> policy.requireTaskExecution(actor, id, "workflow.start");
      case SUBMIT -> policy.requireTaskExecution(actor, id, "workflow.submit");
      case ASSIGN -> policy.requireTaskManagerWrite(actor, id, "task.assign", "task:write");
      case PRIORITY, ADD_TAG, REMOVE_TAG -> policy.requireTaskManagerWrite(actor, id, "task.update", "task:write");
      case DELETE -> policy.requireTaskWrite(actor, id);
    }
  }
}
