package vn.smarttask.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.smarttask.common.AppException;

/** AI-02: another user's job (even SA-like) reads as 404; history is owner-scoped. */
class AiOtherUserTest {

  @Test
  void otherUserReads404() {
    AiFixture f = new AiFixture(MockOllamaClient.Mode.VALID);
    var job = f.requestOk();
    UUID stranger = UUID.randomUUID();
    f.activeUsers.add(stranger);
    f.participants.add(stranger); // even a privileged in-scope non-owner...

    AppException s = assertThrows(AppException.class, () -> f.service.status(stranger, job.getId()));
    assertEquals(404, s.getHttpStatus().value());
    AppException g = assertThrows(AppException.class,
        () -> f.service.suggestionOf(stranger, job.getId()));
    assertEquals(404, g.getHttpStatus().value());
    AppException a = assertThrows(AppException.class, () -> f.service.accept(stranger, job.getId()));
    assertEquals(404, a.getHttpStatus().value());

    // Owner still fine.
    assertEquals("COMPLETED", f.service.status(f.user, job.getId()).getStatus());
  }

  @Test
  void historyIsOwnerScoped() {
    AiFixture f = new AiFixture(MockOllamaClient.Mode.VALID);
    var job = f.requestOk();
    when(f.jobs.findByPrincipalUserIdOrderByCreatedAtDesc(f.user))
        .thenReturn(java.util.List.of(job));
    assertEquals(1, f.service.history(f.user).size());
    assertTrue(f.service.history(UUID.randomUUID()).isEmpty());
  }
}
