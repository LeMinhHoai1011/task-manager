package vn.smarttask.task.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.*;
import org.springframework.stereotype.Service;
import vn.smarttask.common.AppException;

/** No outer transaction: a failed item must not roll back successful siblings. */
@Service
public class TaskBatchService {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TaskBatchService.class);
  public enum Action { START, SUBMIT, ASSIGN, PRIORITY, ADD_TAG, REMOVE_TAG, DELETE }
  public record Item(UUID id, Long version) {}
  public record Request(List<Item> items, Action action, String value) {}
  public record Result(UUID id, boolean success, String state, Long version,
      String code, @JsonProperty("reason_code") String reasonCode,
      @JsonProperty("http_status") int httpStatus) {}

  private final TaskBatchItemService worker;
  public TaskBatchService(TaskBatchItemService worker) { this.worker = worker; }

  public List<Result> apply(UUID actor, Request request, String key) {
    validate(request, key);
    worker.reserve(actor, request, key);
    List<Result> results = new ArrayList<>();
    for (Item item : request.items()) {
      try { results.add(worker.apply(actor, request, item, key)); }
      catch (AppException e) {
        results.add(new Result(item.id(), false, null, e.getResourceVersion(),
            e.getCode(), e.getReasonCode(), e.getHttpStatus().value()));
      } catch (org.springframework.dao.DataAccessException e) {
        log.warn("Batch item {} failed ({})", item.id(), e.getClass().getSimpleName());
        results.add(new Result(item.id(), false, null, null, "STORAGE_UNAVAILABLE", "BATCH_ITEM_FAILED", 503));
      }
    }
    return List.copyOf(results);
  }

  static void validate(Request request, String key) {
    if (key == null || key.isBlank() || key.length() > 200)
      throw AppException.badRequest("IDEMPOTENCY_KEY_REQUIRED", "Key must contain 1–200 characters");
    if (request == null || request.action() == null || request.items() == null
        || request.items().isEmpty() || request.items().size() > 100)
      throw AppException.badRequest("BATCH_INVALID", "Choose 1–100 tasks and an action");
    Set<UUID> ids = new HashSet<>();
    for (Item item : request.items()) {
      if (item == null || item.id() == null || item.version() == null || item.version() < 1
          || !ids.add(item.id()))
        throw AppException.badRequest("BATCH_INVALID", "Unique task IDs and positive versions required");
    }
    String value = request.value();
    switch (request.action()) {
      case PRIORITY -> {
        if (value == null || !Set.of("LOW", "MEDIUM", "HIGH", "URGENT").contains(value))
          throw AppException.badRequest("PRIORITY_INVALID", "Invalid priority");
      }
      case ADD_TAG, REMOVE_TAG -> {
        if (value == null || value.isBlank() || value.length() > 50)
          throw AppException.badRequest("TAG_INVALID", "Tag must contain 1–50 characters");
      }
      case ASSIGN -> {
        try { UUID.fromString(value == null ? "" : value); }
        catch (IllegalArgumentException e) { throw AppException.badRequest("ASSIGNEE_INVALID", "Invalid assignee ID"); }
      }
      default -> {
        if (value != null && !value.isBlank())
          throw AppException.badRequest("BATCH_INVALID", "This action does not accept a value");
      }
    }
  }
}
