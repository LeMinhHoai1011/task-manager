package vn.smarttask.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.smarttask.ai.entity.AiModelConfiguration;
import vn.smarttask.common.AppException;

/** Ollama config: SA + OLLAMA_CONFIG_MANAGE grant + reauth required. */
class AiConfigTest {

  @Test
  void updateRequiresAdminGrantAndReauth() {
    AiFixture f = new AiFixture(MockOllamaClient.Mode.VALID);
    UUID sa = f.user;

    AppException noGrant = assertThrows(AppException.class,
        () -> f.service.updateConfig(sa, "ollama.model", "m", true, false, true));
    assertEquals(403, noGrant.getHttpStatus().value());

    AppException noAdmin = assertThrows(AppException.class,
        () -> f.service.updateConfig(sa, "ollama.model", "m", false, true, true));
    assertEquals(403, noAdmin.getHttpStatus().value());

    AppException noReauth = assertThrows(AppException.class,
        () -> f.service.updateConfig(sa, "ollama.model", "m", true, true, false));
    assertEquals(428, noReauth.getHttpStatus().value());
  }

  @Test
  void updateAndGetWithFullRights() {
    AiFixture f = new AiFixture(MockOllamaClient.Mode.VALID);
    when(f.configs.findById("ollama.model"))
        .thenReturn(Optional.of(new AiModelConfiguration("ollama.model", "old")));
    when(f.configs.save(any())).thenAnswer(i -> i.getArgument(0));

    f.service.updateConfig(f.user, "ollama.model", "llama3.1:8b", true, true, true);
    assertEquals("llama3.1:8b", f.service.getConfig(f.user, "ollama.model", true, true));
    verify(f.configs).save(any());
  }
}
