package vn.smarttask.task.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.smarttask.common.AppException;
import vn.smarttask.common.UuidV7;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.task.entity.Task;
import vn.smarttask.task.repo.TaskRepository;

@Service
public class PersonalTaskService {
  private final TaskRepository tasks;
  private final AuditService audit;
  private final vn.smarttask.task.repo.ChecklistItemRepository checklist;
  private final vn.smarttask.task.repo.TaskTagRepository tags;
  public PersonalTaskService(TaskRepository tasks, AuditService audit,
      vn.smarttask.task.repo.ChecklistItemRepository checklist, vn.smarttask.task.repo.TaskTagRepository tags) {
    this.tasks = tasks; this.audit = audit; this.checklist = checklist; this.tags = tags;
  }
  @Transactional(readOnly = true)
  public java.util.Map<String, Long> summary(UUID actor) {
    var result = new java.util.LinkedHashMap<String, Long>();
    for (String state : java.util.List.of("TODO", "IN_PROGRESS", "COMPLETED")) result.put(state, 0L);
    for (Object[] row : tasks.personalCounts(actor)) result.put((String) row[0], ((Number) row[1]).longValue());
    result.put("total", result.values().stream().mapToLong(Long::longValue).sum());
    result.put("overdue", tasks.personalOverdue(actor, OffsetDateTime.now(ZoneOffset.UTC)));
    return result;
  }
  @Transactional
  public java.util.Map<String, Object> details(UUID actor, UUID id) {
    Task task = owned(actor, id);
    return java.util.Map.of("task", task, "checklist", checklist.findByTaskIdOrderByPositionAsc(id),
        "tags", tags.findByTaskId(id));
  }
  @Transactional
  public Task checklist(UUID actor, UUID id, UUID itemId, String title, Boolean done, long expected) {
    Task task = owned(actor, id);
    if (itemId == null && (title == null || title.isBlank() || title.trim().length() > 200))
      throw AppException.badRequest("CHECKLIST_TITLE_INVALID", "Checklist title must contain 1–200 characters");
    var item = itemId == null ? null : checklist.findById(itemId)
        .filter(i -> id.equals(i.getTaskId())).orElseThrow(() -> AppException.notFound("Checklist item not found"));
    if (item != null && done == null) throw AppException.badRequest("DONE_REQUIRED", "done required");
    version(task, expected);
    if (item == null) {
      int position = checklist.findByTaskIdOrderByPositionAsc(id).stream()
          .mapToInt(vn.smarttask.task.entity.ChecklistItem::getPosition).max().orElse(-1) + 1;
      item = new vn.smarttask.task.entity.ChecklistItem(UuidV7.generate(), id, title.trim(), position);
    } else item.setDone(done);
    checklist.save(item);
    audit.append(actor, "personal.checklist", "task", id, "ALLOWED", null, "{}");
    return tasks.save(task);
  }
  @Transactional
  public Task labels(UUID actor, UUID id, java.util.List<String> labels, long expected) {
    Task task = owned(actor, id);
    if (labels == null || labels.size() > 20 || labels.stream().anyMatch(s -> s == null || s.isBlank() || s.trim().length() > 50))
      throw AppException.badRequest("LABELS_INVALID", "At most 20 labels, 1–50 characters each");
    version(task, expected);
    tags.deleteAll(tags.findByTaskId(id)); tags.flush();
    labels.stream().map(String::trim).distinct().forEach(label -> tags.save(
        new vn.smarttask.task.entity.TaskTag(UuidV7.generate(), id, label)));
    audit.append(actor, "personal.labels", "task", id, "ALLOWED", null, "{}");
    return tasks.save(task);
  }
  @Transactional(readOnly = true)
  public Page<Task> list(UUID actor, int page, int size) {
    return tasks.findByOwnerUserIdAndScopeAndDeletedAtIsNull(actor, "PERSONAL",
        PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)),
            Sort.by(Sort.Direction.DESC, "createdAt")));
  }
  @Transactional
  public Task create(UUID actor, String title) {
    if (title == null || title.isBlank() || title.trim().length() > 200)
      throw AppException.badRequest("TITLE_INVALID", "Title must contain 1–200 characters");
    Task task = tasks.save(Task.personal(UuidV7.generate(), actor, title.trim()));
    audit.append(actor, "personal.create", "task", task.getId(), "ALLOWED", null, "{}");
    return task;
  }
  private Task owned(UUID actor, UUID id) {
    return owned(actor, id, false);
  }
  private Task owned(UUID actor, UUID id, boolean includeDeleted) {
    Task task = tasks.findByIdForUpdate(id)
        .orElseThrow(() -> AppException.notFound("Task not found"));
    if (!"PERSONAL".equals(task.getScope()) || !actor.equals(task.getOwnerUserId())
        || (!includeDeleted && task.getDeletedAt() != null)) throw AppException.notFound("Task not found");
    return task;
  }
  @Transactional(readOnly = true)
  public Page<Task> search(UUID actor, int page, int size, String query, String state,
      String priority, boolean deleted, String sort, String tag, boolean overdue) {
    String property = switch (sort) {
      case "dueAt", "priority", "title" -> sort;
      default -> "createdAt";
    };
    return tasks.findAll((root, criteria, cb) -> {
      var conditions = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
      conditions.add(cb.equal(root.get("scope"), "PERSONAL"));
      conditions.add(cb.equal(root.get("ownerUserId"), actor));
      conditions.add(deleted ? cb.isNotNull(root.get("deletedAt")) : cb.isNull(root.get("deletedAt")));
      if (overdue) {
        conditions.add(cb.lessThan(root.get("dueAt"), OffsetDateTime.now(ZoneOffset.UTC)));
        conditions.add(root.get("state").in("TODO", "IN_PROGRESS"));
      }
      if (tag != null && !tag.isBlank()) {
        var sub = criteria.subquery(UUID.class);
        var label = sub.from(vn.smarttask.task.entity.TaskTag.class);
        sub.select(label.get("taskId")).where(cb.equal(label.get("taskId"), root.get("id")), cb.equal(label.get("tag"), tag.trim()));
        conditions.add(cb.exists(sub));
      }
      if (state != null && !state.isBlank()) conditions.add(cb.equal(root.get("state"), state));
      if (priority != null && !priority.isBlank()) conditions.add(cb.equal(root.get("priority"), priority));
      if (query != null && !query.isBlank()) {
        String escaped = query.toLowerCase(java.util.Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        conditions.add(cb.or(cb.like(cb.lower(root.get("title")), "%" + escaped + "%", '\\'),
            cb.like(cb.lower(root.get("description")), "%" + escaped + "%", '\\')));
      }
      if ("priority".equals(property) && !Long.class.equals(criteria.getResultType())) {
        var rank = cb.<Integer>selectCase().when(cb.equal(root.get("priority"), "URGENT"), 0)
            .when(cb.equal(root.get("priority"), "HIGH"), 1)
            .when(cb.equal(root.get("priority"), "MEDIUM"), 2).otherwise(3);
        criteria.orderBy(cb.asc(rank), cb.asc(root.get("id")));
      }
      return cb.and(conditions.toArray(jakarta.persistence.criteria.Predicate[]::new));
    }, PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)),
        "priority".equals(property) ? Sort.unsorted() :
            Sort.by("createdAt".equals(property) ? Sort.Direction.DESC : Sort.Direction.ASC, property)
                .and(Sort.by("id"))));
  }
  public record Edit(String title, String description, String notes, String priority,
      OffsetDateTime startAt, OffsetDateTime dueAt, Boolean dueIsDateOnly, String dueTimezone) {
    public Edit(String title, String description, String notes, String priority, OffsetDateTime startAt, OffsetDateTime dueAt) {
      this(title, description, notes, priority, startAt, dueAt, false, "UTC");
    }
  }
  @Transactional
  public Task start(UUID actor, UUID id, long expected) {
    Task task = owned(actor, id);
    if (!"TODO".equals(task.getState()))
      throw AppException.stateConflict("TRANSITION_FORBIDDEN", "Start requires TODO");
    version(task, expected);
    task.setState("IN_PROGRESS");
    audit.append(actor, "personal.start", "task", id, "ALLOWED", null, "{}");
    return tasks.save(task);
  }
  private void version(Task task, long expected) {
    if (task.getVersion() != expected) throw AppException.versionConflict("Stale version", task.getVersion());
    task.setVersion(task.getVersion() + 1);
  }
  @Transactional
  public Task edit(UUID actor, UUID id, Edit edit, long expected) {
    Task task = owned(actor, id);
    if (edit.title() == null || edit.title().isBlank() || edit.title().trim().length() > 200)
      throw AppException.badRequest("TITLE_INVALID", "Title must contain 1–200 characters");
    if (edit.priority() == null || !java.util.Set.of("LOW", "MEDIUM", "HIGH", "URGENT").contains(edit.priority()))
      throw AppException.badRequest("PRIORITY_INVALID", "Invalid priority");
    java.time.ZoneId zone;
    try { zone = java.time.ZoneId.of(edit.dueTimezone() == null ? "UTC" : edit.dueTimezone()); }
    catch (java.time.DateTimeException e) { throw AppException.badRequest("TIMEZONE_INVALID", "Invalid timezone"); }
    boolean dateOnly = Boolean.TRUE.equals(edit.dueIsDateOnly());
    OffsetDateTime deadline = edit.dueAt();
    if (dateOnly && deadline != null) deadline = deadline.toLocalDate().plusDays(1)
        .atStartOfDay(zone).minusNanos(1000).toOffsetDateTime();
    if (edit.startAt() != null && deadline != null && edit.startAt().isAfter(deadline))
      throw AppException.badRequest("SCHEDULE_INVALID", "Start must not follow deadline");
    version(task, expected);
    task.setTitle(edit.title().trim()); task.setDescription(edit.description()); task.setNotes(edit.notes());
    task.setPriority(edit.priority()); task.setStartAt(edit.startAt()); task.setDueAt(deadline);
    task.setDueIsDateOnly(dateOnly && deadline != null); task.setDueTimezone(zone.getId());
    audit.append(actor, "personal.update", "task", id, "ALLOWED", null, "{}");
    return tasks.save(task);
  }
  @Transactional
  public Task deletion(UUID actor, UUID id, boolean deleted, long expected) {
    Task task = owned(actor, id, true);
    version(task, expected);
    task.setDeletedAt(deleted ? OffsetDateTime.now(ZoneOffset.UTC) : null);
    task.setDeletedBy(deleted ? actor : null);
    audit.append(actor, deleted ? "personal.delete" : "personal.restore", "task", id, "ALLOWED", null, "{}");
    return tasks.save(task);
  }
  @Transactional
  public Task complete(UUID actor, UUID id, boolean completed, long version) {
    Task task = owned(actor, id);
    if (task.getVersion() != version)
      throw AppException.versionConflict("Stale version", task.getVersion());
    task.setState(completed ? "COMPLETED" : "TODO");
    task.setCompletedAt(completed ? OffsetDateTime.now(ZoneOffset.UTC) : null);
    task.setCompletedBy(completed ? actor : null);
    task.setVersion(task.getVersion() + 1);
    audit.append(actor, "personal.completion", "task", id, "ALLOWED", null,
        "{\"completed\":" + completed + "}");
    return tasks.save(task);
  }
}
