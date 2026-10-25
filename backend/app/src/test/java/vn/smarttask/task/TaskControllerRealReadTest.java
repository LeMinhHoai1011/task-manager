package vn.smarttask.task;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.smarttask.common.AppException;
import vn.smarttask.project.repo.ProjectMembershipRepository;
import vn.smarttask.project.repo.ProjectRepository;
import vn.smarttask.security.PolicyEnforcer;
import vn.smarttask.security.Principal;
import vn.smarttask.task.entity.ChecklistItem;
import vn.smarttask.task.entity.Task;
import vn.smarttask.task.entity.TimeLog;
import vn.smarttask.task.repo.ChecklistItemRepository;
import vn.smarttask.task.repo.TaskAssignmentRepository;
import vn.smarttask.task.repo.TaskRepository;
import vn.smarttask.task.repo.TaskTagRepository;
import vn.smarttask.task.repo.TimeLogRepository;
import vn.smarttask.task.service.TaskService;

/**
 * F1 TaskController real reads (offline, Mockito): list filters over the
 * real repository, get returns the stored row (deleted -&gt; 404),
 * checklist/timelogs read their repos, policy entry stays read-&gt;404.
 */
@ExtendWith(MockitoExtension.class)
class TaskControllerRealReadTest {

  @Mock TaskService service;
  @Mock PolicyEnforcer policy;
  @Mock TaskRepository tasks;
  @Mock ChecklistItemRepository checklist;
  @Mock TimeLogRepository timelogs;
  @Mock TaskAssignmentRepository assignments;
  @Mock TaskTagRepository tags;
  @Mock ProjectMembershipRepository projectMembers;
  @Mock ProjectRepository projects;

  private TaskController controller;
  private final UUID actor = UUID.randomUUID();
  private final UUID pid = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    controller = new TaskController(service, policy, tasks, checklist, timelogs, assignments,
        tags, projectMembers, projects);
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(new Principal(actor, UUID.randomUUID()), null,
            List.of()));
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  private Task task(UUID id, String state) {
    Task t = new Task(id, pid, "t-" + id.toString().substring(0, 4), actor);
    t.setState(state);
    return t;
  }

  @Test
  void listReadsRepositoryWithStateFilter() {
    Task a = task(UUID.randomUUID(), "TODO");
    Task b = task(UUID.randomUUID(), "COMPLETED");
    when(tasks.findByProjectId(pid)).thenReturn(List.of(a, b));

    var res = controller.list(pid, null, "TODO", null, null, null, null, null, "updated_at",
        "desc", null, 20);

    assertEquals(200, res.getStatusCode().value());
    assertEquals(1, res.getBody().data().data().size());
    assertEquals(a.getId(), res.getBody().data().data().get(0).id());
  }

  @Test
  void listBlockedAndOverdueFilters() {
    Task t = task(UUID.randomUUID(), "IN_PROGRESS");
    t.setBlockedFlag(true);
    t.setDueAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
    Task other = task(UUID.randomUUID(), "IN_PROGRESS");
    when(tasks.findByProjectId(pid)).thenReturn(List.of(t, other));

    var blocked = controller.list(pid, null, null, null, null, true, null, null, "updated_at",
        "desc", null, 20);
    assertEquals(1, blocked.getBody().data().data().size());

    var overdue = controller.list(pid, null, null, null, null, null, null, true, "updated_at",
        "desc", null, 20);
    assertEquals(1, overdue.getBody().data().data().size());
    assertEquals(t.getId(), overdue.getBody().data().data().get(0).id());
  }

  @Test
  void listCursorPagination() {
    Task a = task(UUID.randomUUID(), "TODO");
    Task b = task(UUID.randomUUID(), "TODO");
    when(tasks.findByProjectId(pid)).thenReturn(List.of(a, b));

    var first = controller.list(pid, null, null, null, null, null, null, null, "updated_at",
        "desc", null, 1);
    assertTrue(first.getBody().data().hasMore());
    String cursor = first.getBody().data().nextCursor();
    assertNotNull(cursor);

    var second = controller.list(pid, null, null, null, null, null, null, null, "updated_at",
        "desc", cursor, 1);
    assertEquals(1, second.getBody().data().data().size());
    assertFalse(second.getBody().data().hasMore());
  }

  @Test
  void getReturnsStoredRow() {
    Task t = task(UUID.randomUUID(), "IN_PROGRESS");
    when(tasks.findById(t.getId())).thenReturn(Optional.of(t));

    var res = controller.get(t.getId());

    assertEquals(t.getId(), res.getBody().data().id());
    assertEquals("IN_PROGRESS", res.getBody().data().state());
    assertEquals(pid, res.getBody().data().projectId());
  }

  @Test
  void getDeletedIs404() {
    Task t = task(UUID.randomUUID(), "TODO");
    t.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
    when(tasks.findById(t.getId())).thenReturn(Optional.of(t));

    AppException ex = assertThrows(AppException.class, () -> controller.get(t.getId()));
    assertEquals(404, ex.getHttpStatus().value());
  }

  @Test
  void outsiderListIs404FromPolicy() {
    doThrow(AppException.notFound("Project not found"))
        .when(policy).requireProjectRead(actor, pid);

    AppException ex = assertThrows(AppException.class, () -> controller.list(pid, null, null,
        null, null, null, null, null, "updated_at", "desc", null, 20));
    assertEquals(404, ex.getHttpStatus().value());
  }

  @Test
  void checklistAndTimelogsReadRepos() {
    UUID tid = UUID.randomUUID();
    when(checklist.findByTaskIdOrderByPositionAsc(tid)).thenReturn(
        List.of(new ChecklistItem(UUID.randomUUID(), tid, "step", 0)));
    when(timelogs.findByTaskId(tid)).thenReturn(
        List.of(new TimeLog(UUID.randomUUID(), tid, actor, LocalDate.now(), 30)));

    assertEquals(1, controller.checklist(tid).getBody().data().size());
    assertEquals(1, controller.timelogs(tid).getBody().data().size());
  }

  @Test
  void tagFilter() {
    Task t = task(UUID.randomUUID(), "TODO");
    Task other = task(UUID.randomUUID(), "TODO");
    when(tasks.findByProjectId(pid)).thenReturn(List.of(t, other));
    // F2.5: tag filter is an EXISTS point query (no entity load per task).
    when(tags.existsByTaskIdAndTag(t.getId(), "urgent")).thenReturn(true);
    when(tags.existsByTaskIdAndTag(other.getId(), "urgent")).thenReturn(false);

    var res = controller.list(pid, null, null, null, "urgent", null, null, null,
        "updated_at", "desc", null, 20);
    assertEquals(1, res.getBody().data().data().size());
    assertEquals(t.getId(), res.getBody().data().data().get(0).id());
  }
}
