package vn.smarttask.calendar;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.smarttask.calendar.entity.ReminderRule;
import vn.smarttask.calendar.repo.ReminderRuleRepository;
import vn.smarttask.calendar.service.CalendarService;
import vn.smarttask.common.AppException;
import vn.smarttask.notify.service.NotificationService;
import vn.smarttask.project.repo.ProjectMembershipRepository;
import vn.smarttask.reports.ReportController;
import vn.smarttask.reports.entity.ReportExportJob;
import vn.smarttask.reports.repo.ReportExportJobRepository;
import vn.smarttask.reports.service.ReportService;
import vn.smarttask.security.PolicyEnforcer;
import vn.smarttask.security.Principal;
import vn.smarttask.task.entity.Task;
import vn.smarttask.task.repo.TaskRepository;

/**
 * F1 calendar/reports real reads (offline, Mockito): reminders + ICS from
 * repos, export GET lists real jobs (no echo), dashboards are static
 * dictionary entries.
 */
@ExtendWith(MockitoExtension.class)
class CalendarReportRealReadTest {

  @Mock TaskRepository tasks;
  @Mock ReminderRuleRepository reminders;
  @Mock NotificationService notifications;
  @Mock vn.smarttask.common.service.AuditService audit;
  @Mock vn.smarttask.common.service.OutboxService outbox;
  @Mock PolicyEnforcer policy;
  @Mock ProjectMembershipRepository projectMembers;
  @Mock ReportService reports;
  @Mock ReportExportJobRepository exports;

  private CalendarController calendarController;
  private ReportController reportController;
  private final UUID actor = UUID.randomUUID();
  private final UUID pid = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    var calendarService = new CalendarService(tasks, reminders, notifications, audit, outbox);
    calendarController = new CalendarController(calendarService, policy, reminders, tasks,
        projectMembers);
    reportController = new ReportController(reports, policy, exports);
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(new Principal(actor, UUID.randomUUID()), null,
            List.of()));
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void remindersReadRepoForActor() {
    when(reminders.findByUserId(actor)).thenReturn(List.of(
        new ReminderRule(UUID.randomUUID(), UUID.randomUUID(), actor,
            OffsetDateTime.now(ZoneOffset.UTC).plusHours(1))));
    var res = calendarController.reminders();
    assertEquals(1, res.getBody().data().size());
  }

  @Test
  void icsContainsRealVevents() {
    Task t = new Task(UUID.randomUUID(), pid, "ship it", actor);
    t.setDueAt(OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));
    Task undated = new Task(UUID.randomUUID(), pid, "no date", actor);
    // F2.5: actor-scoped project ids + project-scoped task read.
    when(projectMembers.findActiveProjectIdsByUser(actor)).thenReturn(List.of(pid));
    when(tasks.findByProjectIdInAndDeletedAtIsNull(any())).thenReturn(List.of(t, undated));

    var res = calendarController.ics();
    String body = res.getBody();
    assertTrue(body.contains("BEGIN:VEVENT"));
    assertTrue(body.contains(t.getId().toString()));
    assertTrue(body.contains("SUMMARY:ship it"));
  }

  @Test
  void icsHidesOtherProjects() {
    // F2.5: no visible projects -> empty feed without touching the task table.
    when(projectMembers.findActiveProjectIdsByUser(actor)).thenReturn(List.of());

    assertFalse(calendarController.ics().getBody().contains("BEGIN:VEVENT"));
  }

  @Test
  void calendarUnknownViewIs400() {
    when(projectMembers.findActiveProjectIdsByUser(actor)).thenReturn(List.of());
    AppException ex = assertThrows(AppException.class,
        () -> calendarController.calendar(null, null, "decade", null, null));
    assertEquals(400, ex.getHttpStatus().value());
  }

  @Test
  void exportGetListsRealJobsNoEcho() {
    ReportExportJob job = new ReportExportJob(UUID.randomUUID(), "PROJECT_PROGRESS",
        "PROJECT", pid, LocalDate.now().minusDays(7), LocalDate.now(), "CSV", actor,
        "idem-1", OffsetDateTime.now(ZoneOffset.UTC).plusHours(24));
    ReportExportJob other = new ReportExportJob(UUID.randomUUID(), "PROJECT_PROGRESS",
        "PROJECT", pid, LocalDate.now().minusDays(7), LocalDate.now(), "CSV",
        UUID.randomUUID(), "idem-2", OffsetDateTime.now(ZoneOffset.UTC).plusHours(24));
    // F2.5: requester-scoped ordered read (no full scan + memory filter).
    when(exports.findByRequestedByOrderByCreatedAtDesc(actor)).thenReturn(List.of(job));

    var res = reportController.export(null, "csv");
    var items = (List<?>) ((Map<?, ?>) res.getBody().data()).get("items");
    assertEquals(1, items.size());
  }

  @Test
  void dashboardsAreStaticDictionary() {
    var res = reportController.dashboards();
    assertEquals(4, res.getBody().data().size());
  }
}
