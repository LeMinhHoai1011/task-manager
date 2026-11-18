package vn.smarttask.task.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import vn.smarttask.common.AppException;
import static vn.smarttask.task.service.TaskBatchService.*;

class TaskBatchServiceTest {
  @Test void rejectsInvalidRequestsBeforeAnyWrite() {
    var worker = mock(TaskBatchItemService.class);
    var service = new TaskBatchService(worker);
    UUID actor = UUID.randomUUID(), id = UUID.randomUUID();
    for (var request : List.of(
        new Request(List.of(), Action.START, null),
        new Request(List.of(new Item(id, 1L), new Item(id, 2L)), Action.START, null),
        new Request(List.of(new Item(id, 0L)), Action.START, null),
        new Request(List.of(new Item(id, 1L)), Action.PRIORITY, "INVALID"),
        new Request(List.of(new Item(id, 1L)), Action.ASSIGN, "wrong"),
        new Request(List.of(new Item(id, 1L)), Action.ADD_TAG, " "),
        new Request(java.util.stream.IntStream.range(0,101).mapToObj(i->new Item(UUID.randomUUID(),1L)).toList(), Action.START,null)))
      assertThrows(AppException.class, () -> service.apply(actor, request, "key"));
    assertThrows(AppException.class, () -> service.apply(actor, new Request(List.of(new Item(id,1L)),Action.START,null), ""));
    verifyNoInteractions(worker);
  }

  @Test void preservesOrderAndContinuesAfterPermissionConflictAndDatabaseErrors() {
    var worker = mock(TaskBatchItemService.class);
    var service = new TaskBatchService(worker);
    UUID actor = UUID.randomUUID();
    var items = java.util.stream.IntStream.range(0,5).mapToObj(i->new Item(UUID.randomUUID(),1L)).toList();
    var request = new Request(items,Action.START,null);
    when(worker.apply(eq(actor),eq(request),eq(items.get(0)),eq("key"))).thenThrow(AppException.forbidden("Denied"));
    when(worker.apply(eq(actor),eq(request),eq(items.get(1)),eq("key"))).thenThrow(AppException.versionConflict("Stale",3));
    when(worker.apply(eq(actor),eq(request),eq(items.get(2)),eq("key"))).thenThrow(new DataIntegrityViolationException("DB error"));
    for (int i=3;i<5;i++) when(worker.apply(actor,request,items.get(i),"key"))
        .thenReturn(new Result(items.get(i).id(),true,"IN_PROGRESS",2L,null,null,200));
    var result = service.apply(actor,request,"key");
    assertEquals(items.stream().map(Item::id).toList(),result.stream().map(Result::id).toList());
    assertEquals(403,result.get(0).httpStatus());
    assertEquals(3L,result.get(1).version());
    assertEquals(503,result.get(2).httpStatus());
    assertTrue(result.get(3).success()); assertTrue(result.get(4).success());
  }
}
