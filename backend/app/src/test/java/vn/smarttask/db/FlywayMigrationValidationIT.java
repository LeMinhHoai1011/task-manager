package vn.smarttask.db;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test validating that Flyway migrations execute successfully on clean
 * PostgreSQL 16, that Hibernate ddl-auto=validate succeeds without schema errors,
 * that critical tables (including activity_event) exist, and that migrations are idempotent.
 */
@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.flyway.enabled=true",
    "spring.flyway.baseline-on-migrate=true",
    "spring.flyway.baseline-version=0"
})
class FlywayMigrationValidationIT {

  @Container
  @ServiceConnection
  static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16");

  @Autowired private JdbcTemplate jdbc;
  @Autowired private Flyway flyway;

  @Test
  @DisplayName("Flyway reaches head version 12 and all migrations are successful")
  void flywayReachesHeadVersion12() {
    assertNotNull(flyway, "Flyway bean must be injected");
    MigrationInfo current = flyway.info().current();
    assertNotNull(current, "Current migration must not be null");
    assertEquals("12", current.getVersion().getVersion(), "Flyway head migration must be V12");

    MigrationInfo[] applied = flyway.info().applied();
    assertTrue(applied.length >= 12, "At least 12 migrations must have been applied");
    for (MigrationInfo info : applied) {
      assertTrue(info.getState().isApplied(), "Migration " + info.getScript() + " must be applied");
    }
  }

  @Test
  @DisplayName("activity_event table exists with all required columns and is usable")
  void activityEventTableExistsAndUsable() {
    Integer count = jdbc.queryForObject(
        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'activity_event'",
        Integer.class);
    assertEquals(1, count, "Table activity_event must exist in schema public");

    // Verify key columns added in V1 and V4 exist
    List<String> columns = jdbc.query(
        "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'activity_event'",
        (rs, rowNum) -> rs.getString("column_name"));

    assertTrue(columns.contains("id"), "Column id must exist in activity_event");
    assertTrue(columns.contains("task_id"), "Column task_id must exist in activity_event");
    assertTrue(columns.contains("project_id"), "Column project_id must exist in activity_event");
    assertTrue(columns.contains("actor_user_id"), "Column actor_user_id must exist in activity_event");
    assertTrue(columns.contains("kind"), "Column kind must exist in activity_event");
    assertTrue(columns.contains("payload"), "Column payload must exist in activity_event");
    assertTrue(columns.contains("created_at"), "Column created_at must exist in activity_event");
    assertTrue(columns.contains("target_type"), "Column target_type must exist in activity_event");
    assertTrue(columns.contains("target_id"), "Column target_id must exist in activity_event");
    assertTrue(columns.contains("workspace_id"), "Column workspace_id must exist in activity_event");
    assertTrue(columns.contains("summary_key"), "Column summary_key must exist in activity_event");
    assertTrue(columns.contains("occurred_at"), "Column occurred_at must exist in activity_event");
    assertTrue(columns.contains("correlation_id"), "Column correlation_id must exist in activity_event");

    // Insert test record
    UUID eventId = UUID.randomUUID();
    int inserted = jdbc.update(
        "INSERT INTO activity_event (id, kind, payload) VALUES (?, ?, ?::jsonb)",
        eventId, "TEST_EVENT", "{\"test\": true}");
    assertEquals(1, inserted, "Must be able to insert into activity_event");

    String kind = jdbc.queryForObject(
        "SELECT kind FROM activity_event WHERE id = ?",
        String.class, eventId);
    assertEquals("TEST_EVENT", kind);
  }

  @Test
  @DisplayName("Re-running Flyway migration is idempotent and does not corrupt data")
  void flywayMigrationIsIdempotent() {
    var result = flyway.migrate();
    assertEquals(0, result.migrationsExecuted, "Re-running migration should execute 0 new migrations");
    assertEquals("12", flyway.info().current().getVersion().getVersion(), "Version remains 12");
  }

  @Test
  @DisplayName("All 48 JPA Entity tables exist in PostgreSQL")
  void allJpaEntityTablesExist() {
    String[] expectedTables = {
        "activity_event", "ai_context_manifest", "ai_interaction", "ai_job",
        "ai_model_configuration", "ai_suggestion", "app_user", "approval_decision",
        "approval_designation", "approval_round", "attachment", "audit_event",
        "background_job", "checklist_item", "comment", "delivery_attempt",
        "device_push_registration", "file_access_event", "file_object", "file_version",
        "global_permission_grant", "idempotency_record", "milestone", "notification",
        "notification_preference", "outbox_dispatch", "outbox_event", "project",
        "project_membership", "realtime_ticket", "reauth_ticket", "recovery_code",
        "reminder_rule", "report_export_job", "stream_ticket", "system_setting",
        "task", "task_assignment", "task_dependency", "task_tag", "time_log",
        "upload_session", "user_refresh_token", "user_session", "workflow_event",
        "workspace", "workspace_invitation", "workspace_membership"
    };

    for (String table : expectedTables) {
      Integer count = jdbc.queryForObject(
          "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?",
          Integer.class, table);
      assertEquals(1, count, "Table " + table + " must exist in schema public");
    }
  }
}
