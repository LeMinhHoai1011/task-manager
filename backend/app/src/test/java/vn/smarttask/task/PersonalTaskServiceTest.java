package vn.smarttask.task;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import vn.smarttask.common.AppException;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.task.entity.Task;
import vn.smarttask.task.repo.TaskRepository;
import vn.smarttask.task.service.PersonalTaskService;

class PersonalTaskServiceTest {
  @Test
  void dateOnlyDeadlineUsesLocalEndOfDayAcrossDst() {
    var repo = mock(TaskRepository.class);
    var service = new PersonalTaskService(repo, mock(AuditService.class), mock(vn.smarttask.task.repo.ChecklistItemRepository.class), mock(vn.smarttask.task.repo.TaskTagRepository.class));
    UUID actor = UUID.randomUUID(), id = UUID.randomUUID();
    Task task = Task.personal(id, actor, "DST");
    when(repo.findByIdForUpdate(id)).thenReturn(Optional.of(task)); when(repo.save(task)).thenReturn(task);
    for (String date : java.util.List.of("2026-03-08", "2026-11-01")) {
      service.edit(actor, id, new PersonalTaskService.Edit("DST", null, null, "MEDIUM", null,
          java.time.OffsetDateTime.parse(date + "T00:00:00Z"), true, "America/New_York"), task.getVersion());
      var local = task.getDueAt().atZoneSameInstant(java.time.ZoneId.of("America/New_York"));
      assertEquals(date, local.toLocalDate().toString()); assertEquals(23, local.getHour());
      assertEquals(59, local.getMinute()); assertTrue(task.isDueIsDateOnly());
    }
  }
  @Test
  void startRequiresOwnerTodoAndCurrentVersion() {
    var repo = mock(TaskRepository.class);
    var service = new PersonalTaskService(repo, mock(AuditService.class), mock(vn.smarttask.task.repo.ChecklistItemRepository.class), mock(vn.smarttask.task.repo.TaskTagRepository.class));
    UUID actor = UUID.randomUUID(), id = UUID.randomUUID();
    Task task = Task.personal(id, actor, "Private");
    when(repo.findByIdForUpdate(id)).thenReturn(Optional.of(task));
    when(repo.save(task)).thenReturn(task);
    assertEquals(404, assertThrows(AppException.class, () -> service.start(UUID.randomUUID(), id, 1)).getHttpStatus().value());
    assertEquals(409, assertThrows(AppException.class, () -> service.start(actor, id, 5)).getHttpStatus().value());
    service.start(actor, id, 1);
    assertEquals("IN_PROGRESS", task.getState());
    assertEquals(2, task.getVersion());
    assertEquals(409, assertThrows(AppException.class, () -> service.start(actor, id, 2)).getHttpStatus().value());
  }
  @Test
  void checklistCannotCompleteTaskOrCrossTaskBoundary() {
    var repo = mock(TaskRepository.class);
    var checklist = mock(vn.smarttask.task.repo.ChecklistItemRepository.class);
    var tags = mock(vn.smarttask.task.repo.TaskTagRepository.class);
    var service = new PersonalTaskService(repo, mock(AuditService.class), checklist, tags);
    UUID actor = UUID.randomUUID(), id = UUID.randomUUID(), itemId = UUID.randomUUID();
    Task task = Task.personal(id, actor, "Private");
    var item = new vn.smarttask.task.entity.ChecklistItem(itemId, id, "Subitem", 0);
    when(repo.findByIdForUpdate(id)).thenReturn(Optional.of(task));
    when(repo.save(task)).thenReturn(task);
    when(checklist.findById(itemId)).thenReturn(Optional.of(item));
    service.checklist(actor, id, itemId, null, true, 1);
    assertTrue(item.isDone()); assertEquals("TODO", task.getState());
    assertEquals(404, assertThrows(AppException.class,
        () -> service.checklist(UUID.randomUUID(), id, itemId, null, false, 2)).getHttpStatus().value());
    var foreign = new vn.smarttask.task.entity.ChecklistItem(itemId, UUID.randomUUID(), "Foreign", 0);
    when(checklist.findById(itemId)).thenReturn(Optional.of(foreign));
    assertEquals(404, assertThrows(AppException.class,
        () -> service.checklist(actor, id, itemId, null, true, 2)).getHttpStatus().value());
    assertFalse(foreign.isDone()); assertEquals(2, task.getVersion());
  }
  @Test
  void editsAndTrashRespectOwnerAndVersion() {
    var repo = mock(TaskRepository.class);
    var service = new PersonalTaskService(repo, mock(AuditService.class), mock(vn.smarttask.task.repo.ChecklistItemRepository.class), mock(vn.smarttask.task.repo.TaskTagRepository.class));
    UUID owner = UUID.randomUUID(), id = UUID.randomUUID();
    Task task = Task.personal(id, owner, "Before");
    when(repo.findByIdForUpdate(id)).thenReturn(Optional.of(task));
    when(repo.save(task)).thenReturn(task);
    var edit = new PersonalTaskService.Edit("After", "Description", "Notes", "HIGH", null, null);
    assertEquals(404, assertThrows(AppException.class,
        () -> service.edit(UUID.randomUUID(), id, edit, 1)).getHttpStatus().value());
    service.edit(owner, id, edit, 1);
    assertEquals("After", task.getTitle()); assertEquals("Notes", task.getNotes());
    assertEquals(409, assertThrows(AppException.class,
        () -> service.deletion(owner, id, true, 1)).getHttpStatus().value());
    service.deletion(owner, id, true, 2);
    assertNotNull(task.getDeletedAt());
    assertEquals(404, assertThrows(AppException.class,
        () -> service.complete(owner, id, true, 3)).getHttpStatus().value());
    assertEquals(404, assertThrows(AppException.class,
        () -> service.deletion(UUID.randomUUID(), id, false, 3)).getHttpStatus().value());
    service.deletion(owner, id, false, 3);
    assertNull(task.getDeletedAt()); assertNull(task.getDeletedBy());
    assertEquals(4, task.getVersion());
  }
  @Test
  void ownershipVersionAndCompletionAreEnforced() {
    var repo = mock(TaskRepository.class);
    var audit = mock(AuditService.class);
    var service = new PersonalTaskService(repo, audit, mock(vn.smarttask.task.repo.ChecklistItemRepository.class), mock(vn.smarttask.task.repo.TaskTagRepository.class));
    UUID owner = UUID.randomUUID(), id = UUID.randomUUID();
    Task task = Task.personal(id, owner, "Private");
    when(repo.findByIdForUpdate(id)).thenReturn(Optional.of(task));
    when(repo.save(task)).thenReturn(task);
    assertEquals(404, assertThrows(AppException.class,
        () -> service.complete(UUID.randomUUID(), id, true, 1)).getHttpStatus().value());
    assertEquals(409, assertThrows(AppException.class,
        () -> service.complete(owner, id, true, 9)).getHttpStatus().value());
    service.complete(owner, id, true, 1);
    assertEquals("COMPLETED", task.getState());
    assertNotNull(task.getCompletedAt());
    service.complete(owner, id, false, 2);
    assertEquals("TODO", task.getState());
    assertNull(task.getCompletedAt());
    assertEquals(3, task.getVersion());
    verify(audit, times(2)).append(eq(owner), eq("personal.completion"), eq("task"), eq(id), eq("ALLOWED"), isNull(), anyString());
  }
  @Test
  void invalidTitlesDoNotCreateTasks() {
    var repo = mock(TaskRepository.class);
    var service = new PersonalTaskService(repo, mock(AuditService.class), mock(vn.smarttask.task.repo.ChecklistItemRepository.class), mock(vn.smarttask.task.repo.TaskTagRepository.class));
    for (String title : new String[]{" ", "a".repeat(201)})
      assertThrows(AppException.class, () -> service.create(UUID.randomUUID(), title));
    verifyNoInteractions(repo);
  }
}
