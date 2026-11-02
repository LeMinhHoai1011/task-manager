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
    ReminderRule r = requireOwned(actor, ruleId);
    if("CANCELLED".equals(r.getStatus()))throw AppException.stateConflict("REMINDER_TERMINAL","Reminder is cancelled");
    boolean wasPaused=r.isPaused();
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    var missed=rules.findByUserId(actor).stream().filter(x->x.isPaused()&&"PAUSED".equals(x.getStatus())&&!x.isDigestSent()&&!x.getRemindAt().isAfter(now)).toList();
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
    ReminderRule r = rules.findById(ruleId)
        .orElseThrow(() -> AppException.notFound("Reminder rule not found"));
    if (!r.getUserId().equals(actor)) throw AppException.notFound("Reminder rule not found");
    return r;
  }

  public DeadlineResolver resolver() {
    return new DeadlineResolver();
  }
}
