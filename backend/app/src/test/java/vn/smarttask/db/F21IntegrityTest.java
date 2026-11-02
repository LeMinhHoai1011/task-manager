package vn.smarttask.db;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

/**
 * F2.1 integrity against the real PostgreSQL 16 provided by the harness
 * (localhost:5433, isolated {@code smarttask_f21} database; other volumes
 * untouched). Flyway migrates V1..V10 on boot; Hibernate validates mappings.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=${SMARTTASK_F21_DATABASE_URL:jdbc:postgresql://localhost:5433/smarttask_f21}",
    "spring.datasource.username=stm_test",
    "spring.datasource.password=stm_test_pw"
})
class F21IntegrityTest extends F21IntegrityBase {
}
