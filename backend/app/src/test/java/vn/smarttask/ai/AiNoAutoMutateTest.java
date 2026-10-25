package vn.smarttask.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.smarttask.task.entity.Task;

/** Suggestion alone must never mutate tasks; only explicit accept() creates via TaskService. */
class AiNoAutoMutateTest {

  @Test
  void suggestionDoesNotTouchTasksUntilAccept() {
    AiFixture f = new AiFixture(MockOllamaClient.Mode.VALID);
    var job = f.requestOk();
    assertEquals("COMPLETED", job.getStatus());
    assertNotNull(f.sugRef.get());
    assertEquals("DRAFT", f.sugRef.get().getStatus());
    // Core untouched: no TaskService call at all before accept.
    verifyNoInteractions(f.taskService);
  }

  @Test
  void acceptCreatesThroughStandardUseCaseWithActorUser() {
    AiFixture f = new AiFixture(MockOllamaClient.Mode.VALID);
    var job = f.requestOk();
    Task created = new Task(UUID.randomUUID(), f.projectId, "Draft from AI", f.user);
    when(f.taskService.create(any(), any(), anyString(), any(), anyString(), any()))
        .thenReturn(created);

    Task out = f.service.accept(f.user, job.getId());

    assertEquals(created.getId(), out.getId());
    verify(f.taskService, times(1)).create(eq(f.user), eq(f.projectId), eq("Draft from AI"),
        any(), eq("HIGH"), isNull());
    assertEquals("ACCEPTED", f.sugRef.get().getStatus());
  }
}
