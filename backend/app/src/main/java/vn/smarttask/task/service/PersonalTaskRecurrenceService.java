package vn.smarttask.task.service;

import java.time.*;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.smarttask.common.AppException;
import vn.smarttask.task.entity.Task;
import vn.smarttask.task.repo.TaskRepository;

@Service
public class PersonalTaskRecurrenceService {
  private final JdbcTemplate jdbc;
  private final TaskRepository tasks;
  private final PersonalTaskService personal;

  public PersonalTaskRecurrenceService(JdbcTemplate jdbc, TaskRepository tasks, PersonalTaskService personal) {
    this.jdbc = jdbc;
    this.tasks = tasks;
    this.personal = personal;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> status(UUID actor, UUID id) {
    return jdbc.queryForList("""
        select interval_days, timezone, next_date, stopped_at
        from personal_task_recurrence where source_task_id=? and owner_user_id=?
        """, id, actor).stream().findFirst().orElseGet(Map::of);
  }

  @Transactional
  public Map<String, Object> start(UUID actor, UUID id, int intervalDays, long version) {
    if (intervalDays != 1 && intervalDays != 7)
      throw AppException.badRequest("RECURRENCE_INTERVAL_INVALID", "Choose daily or weekly");
    Task source = tasks.findByIdForUpdate(id)
        .filter(task -> "PERSONAL".equals(task.getScope()) && actor.equals(task.getOwnerUserId()) && task.getDeletedAt() == null)
        .orElseThrow(() -> AppException.notFound("Task not found"));
    var existing = status(actor, id);
    if (!existing.isEmpty()) {
      if (existing.get("stopped_at") == null && Integer.valueOf(intervalDays).equals(existing.get("interval_days")))
        return existing;
      if (existing.get("stopped_at") == null)
        throw AppException.stateConflict("RECURRENCE_EXISTS", "Stop the current schedule first");
    }
    if (source.getVersion() != version) throw AppException.versionConflict("Stale version", source.getVersion());
    if (source.getDueAt() == null)
      throw AppException.badRequest("RECURRENCE_DEADLINE_REQUIRED", "Set the first deadline before repeating");
    ZoneId zone = TaskOverviewService.zone(source.getDueTimezone());
    var due = source.getDueAt().atZoneSameInstant(zone);
    var next = due.toLocalDate().plusDays(intervalDays);
    var today = LocalDate.now(zone);
    if (!next.isAfter(today))
      throw AppException.badRequest("RECURRENCE_DEADLINE_PAST", "Set a current or future first deadline");
    jdbc.update("""
        insert into personal_task_recurrence
          (source_task_id,owner_user_id,title,description,notes,priority,interval_days,timezone,due_time,next_date,run_at)
        values (?,?,?,?,?,?,?,?,?,?,?)
        on conflict (source_task_id) do update set title=excluded.title,description=excluded.description,
          notes=excluded.notes,priority=excluded.priority,interval_days=excluded.interval_days,
          timezone=excluded.timezone,due_time=excluded.due_time,next_date=excluded.next_date,
          run_at=excluded.run_at,stopped_at=null
        where personal_task_recurrence.stopped_at is not null and
          excluded.next_date>=personal_task_recurrence.next_date and
          excluded.run_at>=personal_task_recurrence.run_at
        """, id, actor, source.getTitle(), source.getDescription(), source.getNotes(), source.getPriority(),
        intervalDays, zone.getId(), source.isDueIsDateOnly() ? null : due.toLocalTime(),
        next, next.atStartOfDay(zone).toOffsetDateTime());
    var updated = status(actor, id);
    if (updated.get("stopped_at") != null)
      throw AppException.stateConflict("RECURRENCE_OVERLAP", "Move the first deadline forward before restarting");
    return updated;
  }

  @Transactional
  public Map<String, Object> stop(UUID actor, UUID id) {
    jdbc.update("""
        update personal_task_recurrence set stopped_at=now()
        where source_task_id=? and owner_user_id=? and stopped_at is null
        """, id, actor);
    return status(actor, id);
  }

  private record Period(UUID id, UUID owner, String title, String description, String notes, String priority,
      int interval, ZoneId zone, LocalTime time, LocalDate date) {}

  @Transactional
  public void generateDue() {
    var periods = jdbc.query("""
        select r.* from personal_task_recurrence r join app_user u on u.id=r.owner_user_id
        where r.stopped_at is null and r.run_at<=now() and u.active and not u.locked and u.status='ACTIVE'
        order by r.run_at,r.source_task_id limit 50 for update of r skip locked
        """, (rs, row) -> new Period(rs.getObject("source_task_id", UUID.class),
        rs.getObject("owner_user_id", UUID.class), rs.getString("title"), rs.getString("description"),
        rs.getString("notes"), rs.getString("priority"), rs.getInt("interval_days"),
        ZoneId.of(rs.getString("timezone")), rs.getObject("due_time", LocalTime.class),
        rs.getObject("next_date", LocalDate.class)));
    for (var period : periods) {
      var task = personal.create(period.owner(), period.title());
      var deadline = deadline(period.date(), period.time(), period.zone());
      personal.edit(period.owner(), task.getId(), new PersonalTaskService.Edit(period.title(),
          period.description(), period.notes(), period.priority(), null, deadline,
          period.time() == null, period.zone().getId()), task.getVersion());
      var next = period.date().plusDays(period.interval());
      jdbc.update("update personal_task_recurrence set next_date=?,run_at=? where source_task_id=?",
          next, next.atStartOfDay(period.zone()).toOffsetDateTime(), period.id());
    }
  }

  static OffsetDateTime deadline(LocalDate date, LocalTime time, ZoneId zone) {
    return date.atTime(time == null ? LocalTime.MIDNIGHT : time).atZone(zone).toOffsetDateTime();
  }
}
