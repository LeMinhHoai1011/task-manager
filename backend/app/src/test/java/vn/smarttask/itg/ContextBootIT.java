package vn.smarttask.itg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * F2.2/1 ContextBootIT: the Spring context boots against the real PG16
 * scratch DB with the full Flyway chain applied and Hibernate validation on.
 */
class ContextBootIT extends ItgBase {

  @Autowired Flyway flyway;

  @Test
  void contextLoadsWithMockMvc() {
    assertTrue(mvc != null, "MockMvc must be wired");
    assertTrue(identity != null, "IdentityService must be wired");
  }

  @Test
  void flywayVersionIs17() {
    assertEquals("17", flyway.info().current().getVersion().getVersion());
    assertEquals(17, flyway.info().applied().length);
    assertEquals(0, flyway.info().pending().length);
    assertTrue(flyway.validateWithResult().validationSuccessful);
    assertTrue(flyway.validateWithResult().validationSuccessful);
  }

  @Test
  void hibernateValidatePassesAndRepositoriesWork() {
    // If ddl-auto=validate had failed, the context would not exist.
    // A live repository round-trip proves the EMF + mappings are usable.
    long n = users.count();
    assertTrue(n >= 0, "user count must be readable");
  }
}
