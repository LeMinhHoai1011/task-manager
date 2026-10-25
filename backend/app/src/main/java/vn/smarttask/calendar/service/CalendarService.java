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
    var t = tasks.findById(taskId).orElseThrow(() -> AppException.notFound("Task not found"));
    boolean dup = rules.findByTaskId(taskId).stream()
        .anyMatch(r -> r.getUserId().equals(actor) && r.getRemindAt().equals(remindAt)
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
    ReminderRule r = requireOwned(actor, ruleId);
    r.setPaused(false);
    r.setStatus("ACTIVE");
    rules.save(r);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    List<ReminderEngine.Rule> engineRules = rules.findByUserId(actor).stream()
        .map(x -> new ReminderEngine.Rule(x.getId(), x.getUserId(), x.getTaskId(),
            x.getRemindAt(), x.isPaused(), "FIRED".equals(x.getStatus())))
        .toList();
    var digests = new ReminderEngine().resumeDigests(engineRules);
    int sent = 0;
    // resume of this rule yields at most one digest for the actor if it missed its fire
    if (r.getRemindAt().isBefore(now) && !r.isDigestSent()
        && digests.getOrDefault(actor, 0) > 0) {
      notifications.send(actor, "DEADLINE_REMINDER", "NORMAL", "TASK", r.getTaskId(),
          "notify.reminder.digest.title", "notify.reminder.digest.body", null,
          "resume-digest-" + ruleId);
      r.setDigestSent(true);
      rules.save(r);
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
