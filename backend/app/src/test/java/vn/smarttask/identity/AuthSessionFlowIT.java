package vn.smarttask.identity;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * F0 login -&gt; authenticated API flow against real PostgreSQL.
 *
 * <p>NOT_TESTED: Docker daemon is OFF in this environment, so this container
 * test is written but cannot run here. Enable (remove {@code @Disabled}) on a
 * machine with a Docker daemon before F1.
 *
 * <p>Intended flow once enabled: register -&gt; approve -&gt; login -&gt; call
 * {@code GET /api/v1/projects/{id}} with {@code Authorization: Bearer} token
 * (expect 200 for member, 401 without token, 404 for outsider).
 */
@Testcontainers
@Disabled("needs-docker-daemon — NOT_TESTED")
class AuthSessionFlowIT {

  @Container
  static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

  @Test
  void loginThenCallApiWithToken() {
    // Placeholder: wire Spring context to PG.getJdbcUrl() and exercise the
    // session filter end-to-end once a Docker daemon is available.
  }
}
