package vn.smarttask.db;

import org.junit.jupiter.api.Disabled;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Testcontainers variant of the F2.1 integrity suite (PostgreSQLContainer
 * {@code postgres:16}, Flyway V1..V10 on boot). For CI / maintainers where a
 * Docker daemon is reachable from the JVM.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Disabled("env-blocked: docker-java 3.3.x (TC 1.19.8) gets HTTP 400 from the"
    + " Docker Desktop 4.93 API proxy while raw npipe calls return 200;"
    + " same assertions run in F21IntegrityTest against real PG16."
    + " Re-enable where the JVM can drive Docker.")
class F21IntegrityContainerIT extends F21IntegrityBase {

  @Container
  @ServiceConnection
  static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16");
}
