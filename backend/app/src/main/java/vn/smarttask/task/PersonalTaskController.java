package vn.smarttask.task;

import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;
import vn.smarttask.common.ApiResult;
import vn.smarttask.security.CurrentActor;
import vn.smarttask.task.service.PersonalTaskService;

@RestController
@RequestMapping("/api/v1/me/tasks")
public class PersonalTaskController {
  private final PersonalTaskService service;
  private final vn.smarttask.task.service.PersonalTaskCreationService creation;
  public PersonalTaskController(PersonalTaskService service, vn.smarttask.task.service.PersonalTaskCreationService creation) { this.service = service; this.creation = creation; }
  public record Create(@NotBlank @Size(max = 200) String title) {}
  public record Completion(boolean completed) {}
  public record Checklist(String title, Boolean done) {}
  public record Labels(java.util.List<String> labels) {}
  @GetMapping("/summary")
  public ApiResult<?> summary() {
    return reply(service.summary(CurrentActor.userId()), "personal.summary.success");
  }
  @GetMapping("/{id}")
  public ApiResult<?> detail(@PathVariable UUID id) {
    return reply(service.details(CurrentActor.userId(), id), "personal.detail.success");
  }
  @PostMapping("/{id}/checklist")
  public ApiResult<?> addChecklist(@PathVariable UUID id, @RequestBody Checklist body, @RequestHeader("If-Match") TaskVersion version) {
    return reply(service.checklist(CurrentActor.userId(), id, null, body.title(), null, version.value()), "personal.checklist.success");
  }
  @PatchMapping("/{id}/checklist/{itemId}")
  public ApiResult<?> tickChecklist(@PathVariable UUID id, @PathVariable UUID itemId, @RequestBody Checklist body, @RequestHeader("If-Match") TaskVersion version) {
    return reply(service.checklist(CurrentActor.userId(), id, itemId, null, body.done(), version.value()), "personal.checklist.success");
  }
  @PutMapping("/{id}/labels")
  public ApiResult<?> labels(@PathVariable UUID id, @RequestBody Labels body, @RequestHeader("If-Match") TaskVersion version) {
    return reply(service.labels(CurrentActor.userId(), id, body.labels(), version.value()), "personal.labels.success");
  }
  @GetMapping
  public ApiResult<?> list(@RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String q, @RequestParam(required = false) String state,
      @RequestParam(required = false) String priority, @RequestParam(defaultValue = "false") boolean deleted,
      @RequestParam(defaultValue = "createdAt") String sort,
      @RequestParam(required = false) String tag, @RequestParam(defaultValue = "false") boolean overdue) {
    return reply(service.search(CurrentActor.userId(), page, size, q, state, priority, deleted, sort, tag, overdue), "personal.list.success");
  }
  @PostMapping("/{id}/start")
  public ApiResult<?> start(@PathVariable UUID id, @RequestHeader("If-Match") TaskVersion version) {
    return reply(service.start(CurrentActor.userId(), id, version.value()), "personal.start.success");
  }
  @PatchMapping("/{id}")
  public ApiResult<?> edit(@PathVariable UUID id, @RequestBody PersonalTaskService.Edit body,
      @RequestHeader("If-Match") TaskVersion version) {
    return reply(service.edit(CurrentActor.userId(), id, body, version.value()), "personal.update.success");
  }
  @DeleteMapping("/{id}")
  public ApiResult<?> delete(@PathVariable UUID id, @RequestHeader("If-Match") TaskVersion version) {
    return reply(service.deletion(CurrentActor.userId(), id, true, version.value()), "personal.delete.success");
  }
  @PostMapping("/{id}/restore")
  public ApiResult<?> restore(@PathVariable UUID id, @RequestHeader("If-Match") TaskVersion version) {
    return reply(service.deletion(CurrentActor.userId(), id, false, version.value()), "personal.restore.success");
  }
  @PostMapping
  public ApiResult<?> create(@Valid @RequestBody Create body, @RequestHeader("Idempotency-Key") String key) {
    return reply(creation.create(CurrentActor.userId(), body.title(), key), "personal.create.success");
  }
  @PatchMapping("/{id}/completion")
  public ApiResult<?> complete(@PathVariable UUID id, @RequestBody Completion body,
      @RequestHeader("If-Match") TaskVersion version) {
    return reply(service.complete(CurrentActor.userId(), id, body.completed(), version.value()),
        "personal.completion.success");
  }

  public record TaskView(UUID id, UUID projectId, String scope, UUID ownerUserId, String title,
      String description, String notes, String state, String priority,
      java.time.OffsetDateTime startAt, java.time.OffsetDateTime dueAt, boolean dueIsDateOnly,
      String dueTimezone, int version, java.time.OffsetDateTime completedAt,
      java.time.OffsetDateTime createdAt, java.time.OffsetDateTime updatedAt, java.time.OffsetDateTime deletedAt) {}
  private static Object view(Object value) {
    if (value instanceof vn.smarttask.task.entity.Task t) return new TaskView(t.getId(), t.getProjectId(),
        t.getScope(), t.getOwnerUserId(), t.getTitle(), t.getDescription(), t.getNotes(), t.getState(),
        t.getPriority(), t.getStartAt(), t.getDueAt(), t.isDueIsDateOnly(), t.getDueTimezone(),
        t.getVersion(), t.getCompletedAt(), t.getCreatedAt(), t.getUpdatedAt(), t.getDeletedAt());
    if (value instanceof org.springframework.data.domain.Page<?> page) return page.map(PersonalTaskController::view);
    if (value instanceof java.util.Map<?, ?> map) {
      var result = new java.util.LinkedHashMap<String, Object>();
      map.forEach((key, item) -> result.put(key.toString(), view(item)));
      return result;
    }
    if (value instanceof java.util.List<?> list) return list.stream().map(PersonalTaskController::view).toList();
    return value;
  }
  private static ApiResult<?> reply(Object value, String message) { return ApiResult.ok(view(value), message); }
}
