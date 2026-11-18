package vn.smarttask.calendar.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.smarttask.calendar.CalendarView;
import vn.smarttask.calendar.DeadlineResolver;
import vn.smarttask.calendar.ReminderEngine;
import vn.smarttask.calendar.entity.ReminderRule;
import vn.smarttask.calendar.repo.ReminderRuleRepository;
import vn.smarttask.common.AppException;
import vn.smarttask.common.UuidV7;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.common.service.OutboxService;
import vn.smarttask.notify.service.NotificationService;
import vn.smarttask.task.repo.TaskRepository;

/**
 * PH-4 CalendarService: views + reminder rule/schedule with pause/resume recompute.
 */
@Service
public class CalendarService {
  @org.springframework.beans.factory.annotation.Autowired(required=false) private org.springframework.jdbc.core.JdbcTemplate jdbc;
  private final TaskRepository tasks;
  private final ReminderRuleRepository rules;
  private final NotificationService notifications;
  private final AuditService audit;
  private final OutboxService outbox;
  @org.springframework.beans.factory.annotation.Autowired private vn.smarttask.task.service.PersonalTaskService personal;
  @org.springframework.beans.factory.annotation.Autowired private vn.smarttask.common.repo.IdempotencyRecordRepository records;
  @org.springframework.beans.factory.annotation.Autowired private com.fasterxml.jackson.databind.ObjectMapper json;

  public record Appointment(String title, String description, OffsetDateTime due_at, OffsetDateTime remind_at, String timezone) {}

  /** Store the personal task and its reminder together; retries return the same rule. */
  @Transactional
  public ReminderRule createAppointment(UUID actor, Appointment input, String key) {
    if (key == null || key.isBlank() || key.length() > 200)
      throw AppException.badRequest("IDEMPOTENCY_KEY_REQUIRED", "Key must contain 1–200 characters");
    String scoped = "appointment:" + actor + ":" + key;
    jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))", Object.class, scoped);
    try {
      String hash = vn.smarttask.common.service.TokenHash.sha256(json.writeValueAsString(input));
      var previous = records.findByKey(scoped);
      if (previous.isPresent()) {
        var saved = json.readTree(previous.get().getResponseBody());
        if (!hash.equals(saved.path("hash").asText()))
          throw AppException.stateConflict("IDEMPOTENCY_CONFLICT", "Key was used with another appointment");
        return requireOwned(actor, UUID.fromString(saved.path("ruleId").asText()));
      }
      if (input.due_at() == null || !input.due_at().isAfter(OffsetDateTime.now(ZoneOffset.UTC))
          || input.remind_at() == null || input.remind_at().isAfter(input.due_at()))
        throw AppException.badRequest("SCHEDULE_INVALID", "Appointment must be in the future and reminder must not follow it");
      if (input.description() != null && input.description().length() > 10000)
        throw AppException.badRequest("DESCRIPTION_INVALID", "Description must not exceed 10000 characters");
      var task = personal.create(actor, input.title());
      task = personal.edit(actor, task.getId(), new vn.smarttask.task.service.PersonalTaskService.Edit(
          input.title(), input.description(), null, "MEDIUM", null, input.due_at(), false, input.timezone()), task.getVersion());
      var rule = createRule(actor, task.getId(), input.remind_at());
      var record = new vn.smarttask.common.entity.IdempotencyRecord(UuidV7.generate(), scoped, actor);
      record.setStatusCode(200);
      record.setResponseBody(json.writeValueAsString(java.util.Map.of("hash", hash, "ruleId", rule.getId())));
      records.save(record);
      return rule;
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException("Invalid appointment record", e);
    }
  }

  @Transactional
  public ReminderRule updateReminder(UUID actor, UUID id, OffsetDateTime time, boolean cancel) {
    var rule = rules.findByIdForUpdate(id).filter(r -> actor.equals(r.getUserId()))
        .orElseThrow(() -> AppException.notFound("Reminder rule not found"));
    if (cancel && "CANCELLED".equals(rule.getStatus())) return rule;
    if (java.util.Set.of("FIRED", "CANCELLED").contains(rule.getStatus()))
      throw AppException.stateConflict("REMINDER_TERMINAL", "Reminder is already terminal");
    if (cancel) { rule.setStatus("CANCELLED"); rule.setPaused(false); }
    else {
      if (time == null || !time.isAfter(OffsetDateTime.now(ZoneOffset.UTC)))
        throw AppException.badRequest("REMINDER_TIME_INVALID", "Reminder must be in the future");
      rule.setRemindAt(time);
    }
    audit.append(actor, cancel ? "reminder.cancel" : "reminder.update", "task", rule.getTaskId(), "ALLOWED", null, "{}");
    return rules.save(rule);
  }

  public CalendarService(TaskRepository tasks, ReminderRuleRepository rules,
      NotificationService notifications, AuditService audit, OutboxService outbox) {
    this.tasks = tasks; this.rules = rules; this.notifications = notifications;
    this.audit = audit; this.outbox = outbox;
  }

  @Transactional(readOnly = true, timeout = 15)
  public List<CalendarView.TaskDue> view(String viewName, LocalDate anchor, String tz,
      List<UUID> projectIds) {
    CalendarView.View v = switch (viewName == null ? "month" : viewName.toLowerCase()) {
      case "day" -> CalendarView.View.DAY;
      case "week" -> CalendarView.View.WEEK;
      case "unscheduled" -> CalendarView.View.UNSCHEDULED;
      case "month" -> CalendarView.View.MONTH;
      default -> throw AppException.badRequest("CALENDAR_VIEW_UNKNOWN", "Unknown view");
    };
    ZoneId zone;
    try {
      zone = ZoneId.of(tz == null ? "UTC" : tz);
    } catch (Exception e) {
      throw AppException.badRequest("TIMEZONE_UNKNOWN", "Unknown timezone");
    }
    var all = (projectIds == null ? tasks.findAll()
        : projectIds.isEmpty() ? List.<vn.smarttask.task.entity.Task>of()
        : tasks.findByProjectIdInAndDeletedAtIsNull(projectIds)).stream()
        .filter(t -> t.getDeletedAt() == null)
        .filter(t -> projectIds == null || projectIds.contains(t.getProjectId()))
        .map(t -> new CalendarView.TaskDue(t.getId(), t.getProjectId(), t.getDueAt()))
        .toList();
    return CalendarView.query(all, v, anchor == null ? LocalDate.now() : anchor, zone);
  }

  @Transactional
  public ReminderRule createRule(UUID actor, UUID taskId, OffsetDateTime remindAt) {
    var t = tasks.findByIdForUpdate(taskId).orElseThrow(() -> AppException.notFound("Task not found"));
    if(t.getDeletedAt()!=null)throw AppException.notFound("Task not found");
    if(remindAt==null||!remindAt.isAfter(OffsetDateTime.now(ZoneOffset.UTC)))throw AppException.badRequest("REMINDER_TIME_INVALID","Reminder must be in the future");
    boolean dup = rules.findByTaskId(taskId).stream()
        .anyMatch(r -> r.getUserId().equals(actor) && r.getRemindAt().toInstant().equals(remindAt.toInstant())
            && !"CANCELLED".equals(r.getStatus()));
    if (dup) throw AppException.stateConflict("REMINDER_DUPLICATE_RULE", "Duplicate reminder rule");
    ReminderRule r = new ReminderRule(UuidV7.generate(), taskId, actor, remindAt);
    rules.save(r);
    audit.append(actor, "reminder.create", "task", taskId, "ALLOWED", null, "{}");
    return r;
  }

  @Transactional
  public ReminderRule pause(UUID actor, UUID ruleId) {
    ReminderRule r = requireOwned(actor, ruleId);
    if(java.util.Set.of("FIRED","CANCELLED").contains(r.getStatus()))throw AppException.stateConflict("REMINDER_TERMINAL","Reminder is already terminal");
    r.setPaused(true);
    r.setStatus("PAUSED");
    rules.save(r);
    audit.append(actor, "reminder.pause", "task", r.getTaskId(), "ALLOWED", null, "{}");
    return r;
  }

  /**
   * Resume: recompute schedule; missed-while-paused fires collapse into ONE digest
   * notification per user (no bulk catch-up).
   */
  @Transactional
  public int resume(UUID actor, UUID ruleId) {
    if(jdbc!=null)jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,83640913))",Object.class,actor.toString());
    // Lock the digest's entire write set in one order, including the requested rule.
    var owned = rules.findByUserIdForUpdate(actor);
    ReminderRule r = owned.stream().filter(rule -> ruleId.equals(rule.getId())).findFirst()
        .orElseThrow(() -> AppException.notFound("Reminder rule not found"));
    if("CANCELLED".equals(r.getStatus()))throw AppException.stateConflict("REMINDER_TERMINAL","Reminder is cancelled");
    boolean wasPaused=r.isPaused();
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    var missed=owned.stream().filter(x->x.isPaused()&&"PAUSED".equals(x.getStatus())&&!x.isDigestSent()&&!x.getRemindAt().isAfter(now)).toList();
    r.setPaused(false);
    if(!"FIRED".equals(r.getStatus()))r.setStatus("ACTIVE");
    rules.save(r);
    int sent = 0;
    if (wasPaused && !missed.isEmpty()) {
      for(var missedRule:missed){missedRule.setPaused(false);missedRule.setStatus("FIRED");missedRule.setDigestSent(true);rules.save(missedRule);}
      outbox.emit("app_user",actor,"notification.reminder.digest","{\"user_id\":\""+actor+"\",\"type\":\"DEADLINE_REMINDER\",\"scope_type\":\"SYSTEM\",\"title_key\":\"notify.reminder.digest.title\",\"body_key\":\"notify.reminder.digest.body\",\"dedup_token\":\"resume-digest-"+ruleId+"\"}");
      sent = 1;
    }
    outbox.emit("task", r.getTaskId(), "task.reminder.recomputed", "{}");
    audit.append(actor, "reminder.resume", "task", r.getTaskId(), "ALLOWED", null, "{}");
    return sent;
  }

  private ReminderRule requireOwned(UUID actor, UUID ruleId) {
    ReminderRule r = rules.findByIdForUpdate(ruleId)
        .orElseThrow(() -> AppException.notFound("Reminder rule not found"));
    if (!r.getUserId().equals(actor)) throw AppException.notFound("Reminder rule not found");
    return r;
  }

  public DeadlineResolver resolver() {
    return new DeadlineResolver();
  }
}
