package vn.smarttask.db;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Testcontainers variant of the F2.1 integrity suite (PostgreSQLContainer
 * {@code postgres:16}, Flyway V1..V12 on boot). Runs automatically on environments
 * with a working Docker daemon (e.g. Linux / CI).
 */
@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class F21IntegrityContainerIT extends F21IntegrityBase {

  @Container
  @ServiceConnection
  static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16");
}
