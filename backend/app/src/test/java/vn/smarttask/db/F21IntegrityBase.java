package vn.smarttask.db;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * F2.1 database-integrity assertions (shared by the real-PG and the
 * Testcontainers subclasses). Boots with Flyway V1..V10 migrated and
 * Hibernate ddl-auto=validate, so context startup itself proves the
 * migration chain and the JPA-vs-SQL alignment (CITEXT, BIGSERIAL seq,
 * dual-schema approval_decision, ...). Service-only guards (last-manager,
 * guest-check, dependency cycle, self-approval) stay service-tested and are
 * intentionally NOT covered here.
 */
abstract class F21IntegrityBase {

  @Autowired JdbcTemplate jdbc;
  @Autowired Flyway flyway;
  @Autowired vn.smarttask.task.repo.TaskRepository taskRepository;

  @Test
  void personalPrioritySortUsesBusinessOrderWithPagination() {
    UUID owner = newUser("sort_" + UUID.randomUUID().toString().substring(0, 8));
    for (String priority : java.util.List.of("LOW", "MEDIUM", "URGENT", "HIGH"))
      jdbc.update("insert into task(id,title,scope,owner_user_id,priority) values (?,?,'PERSONAL',?,?)", UUID.randomUUID(), priority, owner, priority);
    var service = new vn.smarttask.task.service.PersonalTaskService(taskRepository, null, null, null);
    var first = service.search(owner, 0, 2, null, null, null, false, "priority", null, false);
    var second = service.search(owner, 1, 2, null, null, null, false, "priority", null, false);
    assertEquals(4, first.getTotalElements());
    assertEquals(java.util.List.of("URGENT", "HIGH"), first.map(vn.smarttask.task.entity.Task::getPriority).getContent());
    assertEquals(java.util.List.of("MEDIUM", "LOW"), second.map(vn.smarttask.task.entity.Task::getPriority).getContent());
  }

  @Test
  void personalSummaryAndSearchAreScopedBeyondOnePage() {
    UUID owner = newUser("sum_" + UUID.randomUUID().toString().substring(0, 8));
    UUID other = newUser("other_" + UUID.randomUUID().toString().substring(0, 8));
    UUID tagged = null;
    for (int i = 0; i < 105; i++) {
      UUID id = UUID.randomUUID();
      jdbc.update("insert into task(id,title,scope,owner_user_id,due_at) values (?,?,'PERSONAL',?, now() - interval '1 day')", id, "Find me " + i, owner);
      if (i == 0) tagged = id;
    }
    jdbc.update("insert into task(id,title,scope,owner_user_id) values (?,?,'PERSONAL',?)", UUID.randomUUID(), "Other private", other);
    jdbc.update("insert into task_tag(id,task_id,tag) values (?,?,?)", UUID.randomUUID(), tagged, "urgent");
    var service = new vn.smarttask.task.service.PersonalTaskService(taskRepository, null, null, null);
    assertEquals(105L, service.summary(owner).get("total"));
    assertEquals(105L, service.summary(owner).get("overdue"));
    assertEquals(1L, service.summary(other).get("total"));
    var page = service.search(owner, 0, 20, "Find me", null, null, false, "createdAt", null, true);
    assertEquals(20, page.getContent().size());
    assertEquals(105, page.getTotalElements());
    var labelPage = service.search(owner, 0, 20, null, null, null, false, "createdAt", "urgent", false);
    assertEquals(1, labelPage.getTotalElements());
    assertEquals(tagged, labelPage.getContent().get(0).getId());
    assertEquals(0, service.search(other, 0, 20, null, null, null, false, "createdAt", "urgent", false).getTotalElements());
  }

  @Test
  void flywayValidatesEveryMigrationThroughV17() {
    assertEquals("17", flyway.info().current().getVersion().getVersion());
    assertEquals(17, flyway.info().applied().length);
    assertEquals(0, flyway.info().pending().length);
    assertTrue(flyway.validateWithResult().validationSuccessful);
  }

  // ---- fixtures ----

  @Test
  void personalTaskHasOwnerWithoutProject() {
    UUID owner = newUser("personal_" + UUID.randomUUID().toString().substring(0, 8));
    UUID id = UUID.randomUUID();
    assertEquals(1, jdbc.update("insert into task(id,title,scope,owner_user_id) values (?,?,?,?)",
        id, "Private", "PERSONAL", owner));
    assertNull(jdbc.queryForObject("select project_id from task where id=?", UUID.class, id));
  }

  @Test
  void personalTaskWithoutOwnerIsRejected() {
    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
        "insert into task(id,title,scope) values (?,?,?)", UUID.randomUUID(), "Private", "PERSONAL"));
  }

  private UUID newUser(String tag) {
    UUID id = UUID.randomUUID();
    assertEquals(1, jdbc.update(
        "insert into app_user(id,username,email,password_hash,display_name) values (?,?,?,?,?)",
        id, "u_" + tag, tag + "@x.io", "h", tag));
    return id;
  }

  private UUID newWorkspace(UUID owner, String type) {
    UUID id = UUID.randomUUID();
    assertEquals(1, jdbc.update(
        "insert into workspace(id,name,owner_user_id,type) values (?,?,?,?)",
        id, "ws-" + type + "-" + id.toString().substring(0, 8), owner, type));
    return id;
  }

  private UUID newProject(UUID ws) {
    UUID id = UUID.randomUUID();
    assertEquals(1, jdbc.update(
        "insert into project(id,workspace_id,name) values (?,?,?)", id, ws, "p"));
    return id;
  }

  private UUID newTask(UUID project) {
    UUID id = UUID.randomUUID();
    assertEquals(1, jdbc.update(
        "insert into task(id,project_id,title) values (?,?,?)", id, project, "t"));
    return id;
  }

  // ---- V10: personal workspace 1-1 ----

  @Test
  void personalWorkspace_secondLiveRejected_teamDuplicateAllowed() {
    UUID owner = newUser("personal" + UUID.randomUUID().toString().substring(0, 6));
    newWorkspace(owner, "TEAM");
    newWorkspace(owner, "TEAM"); // TEAM duplicates stay allowed
    newWorkspace(owner, "PERSONAL");
    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
        "insert into workspace(id,name,owner_user_id,type) values (?,?,?,?)",
        UUID.randomUUID(), "dup", owner, "PERSONAL"));
  }

  // ---- V1: single-owner / single-session (SQL-only, service has no lock) ----

  @Test
  void workspace_secondOwnerRejected() {
    UUID owner = newUser("owner" + UUID.randomUUID().toString().substring(0, 6));
    UUID ws = newWorkspace(owner, "TEAM");
    assertEquals(1, jdbc.update(
        "insert into workspace_membership(id,workspace_id,user_id,role) values (?,?,?,?)",
        UUID.randomUUID(), ws, owner, "OWNER"));
    UUID other = newUser("owner2" + UUID.randomUUID().toString().substring(0, 6));
    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
        "insert into workspace_membership(id,workspace_id,user_id,role) values (?,?,?,?)",
        UUID.randomUUID(), ws, other, "OWNER"));
  }

  @Test
  void session_secondActiveRejected() {
    UUID u = newUser("sess" + UUID.randomUUID().toString().substring(0, 6));
    String tag = UUID.randomUUID().toString().substring(0, 8);
    assertEquals(1, jdbc.update(
        "insert into user_session(id,user_id,session_token_hash,expires_at) values (?,?,?,now()+interval '1 day')",
        UUID.randomUUID(), u, "h1-" + tag));
    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
        "insert into user_session(id,user_id,session_token_hash,expires_at) values (?,?,?,now()+interval '1 day')",
        UUID.randomUUID(), u, "h2-" + tag));
  }

  // ---- V10: checklist position (was JPA-only) ----

  @Test
  void checklist_duplicatePositionRejected() {
    UUID u = newUser("chk" + UUID.randomUUID().toString().substring(0, 6));
    UUID t = newTask(newProject(newWorkspace(u, "TEAM")));
    assertEquals(1, jdbc.update(
        "insert into checklist_item(id,task_id,title,position) values (?,?,?,?)",
        UUID.randomUUID(), t, "a", 0));
    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
        "insert into checklist_item(id,task_id,title,position) values (?,?,?,?)",
        UUID.randomUUID(), t, "b", 0));
  }

  // ---- V1/V3: task depth, terminal decision, assignment ----

  @Test
  void task_depthAbove1Rejected() {
    UUID u = newUser("depth" + UUID.randomUUID().toString().substring(0, 6));
    UUID p = newProject(newWorkspace(u, "TEAM"));
    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
        "insert into task(id,project_id,title,depth) values (?,?,?,?)",
        UUID.randomUUID(), p, "deep", 2));
  }

  @Test
  void approval_secondTerminalDecisionRejected() {
    UUID u = newUser("appr" + UUID.randomUUID().toString().substring(0, 6));
    UUID t = newTask(newProject(newWorkspace(u, "TEAM")));
    UUID round = UUID.randomUUID();
    assertEquals(1, jdbc.update(
        "insert into approval_round(id,task_id,round_no,submitter_user_id,submitted_by) values (?,?,?,?,?)",
        round, t, 1, u, u));
    assertEquals(1, jdbc.update(
        "insert into approval_decision(id,round_id,decider_user_id,decision) values (?,?,?,?)",
        UUID.randomUUID(), round, u, "APPROVED"));
    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
        "insert into approval_decision(id,round_id,decider_user_id,decision) values (?,?,?,?)",
        UUID.randomUUID(), round, u, "REJECTED"));
  }

  @Test
  void assignment_duplicateRejected() {
    UUID u = newUser("asg" + UUID.randomUUID().toString().substring(0, 6));
    UUID t = newTask(newProject(newWorkspace(u, "TEAM")));
    assertEquals(1, jdbc.update(
        "insert into task_assignment(id,task_id,user_id) values (?,?,?)",
        UUID.randomUUID(), t, u));
    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
        "insert into task_assignment(id,task_id,user_id) values (?,?,?)",
        UUID.randomUUID(), t, u));
  }

  // ---- V9: live grant 1-1, revoked history kept ----

  @Test
  void grant_liveDuplicateRejected_revokedHistoryAllowed() {
    UUID u = newUser("grt" + UUID.randomUUID().toString().substring(0, 6));
    assertEquals(1, jdbc.update(
        "insert into global_permission_grant(id,user_id,code) values (?,?,?)",
        UUID.randomUUID(), u, "X_READ_GLOBAL"));
    // second live row on same (user, code, scope) -> rejected
    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
        "insert into global_permission_grant(id,user_id,code) values (?,?,?)",
        UUID.randomUUID(), u, "X_READ_GLOBAL"));
    // same code on a different scope stays allowed
    assertEquals(1, jdbc.update(
        "insert into global_permission_grant(id,user_id,code,scope_type,scope_id) values (?,?,?,?,?)",
        UUID.randomUUID(), u, "X_READ_GLOBAL", "PROJECT", UUID.randomUUID()));
    // revoke the first row, then a new live row is allowed (history kept)
    assertEquals(1, jdbc.update(
        "update global_permission_grant set revoked_at=now() where user_id=? and code=? and scope_type='GLOBAL'",
        u, "X_READ_GLOBAL"));
    assertEquals(1, jdbc.update(
        "insert into global_permission_grant(id,user_id,code) values (?,?,?)",
        UUID.randomUUID(), u, "X_READ_GLOBAL"));
  }

  // ---- V10: outbox / background-job idempotency ----

  @Test
  void outbox_duplicateIdempotencyKeyRejected_nullKeysAllowed() {
    UUID agg = UUID.randomUUID();
    String key = "k-" + UUID.randomUUID();
    assertEquals(1, jdbc.update(
        "insert into outbox_event(id,aggregate_type,aggregate_id,type) values (?,?,?,?)",
        UUID.randomUUID(), "task", agg, "task.created"));
    assertEquals(1, jdbc.update(
        "insert into outbox_event(id,aggregate_type,aggregate_id,type,idempotency_key) values (?,?,?,?,?)",
        UUID.randomUUID(), "task", agg, "task.created", key));
    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
        "insert into outbox_event(id,aggregate_type,aggregate_id,type,idempotency_key) values (?,?,?,?,?)",
        UUID.randomUUID(), "task", agg, "task.created", key));
  }

  @Test
  void bgJob_duplicateIdempotencyKeyRejected() {
    String key = "job-" + UUID.randomUUID();
    assertEquals(1, jdbc.update(
        "insert into background_job(id,kind,idempotency_key) values (?,?,?)",
        UUID.randomUUID(), "EXPORT", key));
    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
        "insert into background_job(id,kind,idempotency_key) values (?,?,?)",
        UUID.randomUUID(), "EXPORT", key));
  }

  // ---- V10: ai_job principal FK ----

  @Test
  void aiJob_unknownPrincipalRejected() {
    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
        "insert into ai_job(id,principal_user_id,feature,idempotency_key) values (?,?,?,?)",
        UUID.randomUUID(), UUID.randomUUID(), "TASK_DRAFT", "ai-" + UUID.randomUUID()));
  }

  @Test
  void aiJob_knownPrincipalAccepted() {
    UUID u = newUser("ai" + UUID.randomUUID().toString().substring(0, 6));
    assertEquals(1, jdbc.update(
        "insert into ai_job(id,principal_user_id,feature,idempotency_key) values (?,?,?,?)",
        UUID.randomUUID(), u, "TASK_DRAFT", "ai-" + UUID.randomUUID()));
  }

  // ---- V10: ticket expiry CHECK ----

  private UUID newAttachment(UUID task) {
    UUID id = UUID.randomUUID();
    assertEquals(1, jdbc.update(
        "insert into attachment(id,task_id,file_name,mime_type,size_bytes) values (?,?,?,?,?)",
        id, task, "f.pdf", "application/pdf", 10L));
    return id;
  }

  @Test
  void streamTicket_invertedExpiryRejected_validAccepted() {
    UUID u = newUser("tick" + UUID.randomUUID().toString().substring(0, 6));
    UUID att = newAttachment(newTask(newProject(newWorkspace(u, "TEAM"))));
    assertEquals(1, jdbc.update(
        "insert into stream_ticket(id,attachment_id,ticket_hash,expires_at) values (?,?,?,now()+interval '30 seconds')",
        UUID.randomUUID(), att, "valid-" + UUID.randomUUID()));
    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
        "insert into stream_ticket(id,attachment_id,ticket_hash,created_at,expires_at) values (?,?,?,now(),now()-interval '1 second')",
        UUID.randomUUID(), att, "bad-" + UUID.randomUUID()));
  }

  @Test
  void realtimeTicket_invertedExpiryRejected() {
    UUID u = newUser("rtick" + UUID.randomUUID().toString().substring(0, 6));
    UUID sess = UUID.randomUUID();
    assertEquals(1, jdbc.update(
        "insert into user_session(id,user_id,session_token_hash,expires_at) values (?,?,?,now()+interval '1 day')",
        sess, u, "rt-" + UUID.randomUUID()));
    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
        "insert into realtime_ticket(id,session_id,ticket_hash,created_at,expires_at) values (?,?,?,now(),now())",
        UUID.randomUUID(), sess, "rtbad-" + UUID.randomUUID()));
  }

  // ---- V10: export scope domain ----

  @Test
  void export_unknownScopeRejected() {
    UUID u = newUser("exp" + UUID.randomUUID().toString().substring(0, 6));
    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
        "insert into report_export_job(id,report_code,scope_type,period_from,period_to,format,requested_by,expires_at,idempotency_key)"
            + " values (?,?,?,?,?,?,?,?,?)",
        UUID.randomUUID(), "PERSONAL_SUMMARY", "NOPE",
        java.sql.Date.valueOf("2026-01-01"), java.sql.Date.valueOf("2026-01-31"),
        "PDF", u, java.time.OffsetDateTime.now().plusHours(24), "exp-" + UUID.randomUUID()));
  }

  // ---- V12: outbox relay + job lease + export worker columns ----

  @Test
  void v12_outboxRelayColumnsPresent() {
    Integer status = jdbc.queryForObject(
        "select count(*) from information_schema.columns where table_name='outbox_event'"
            + " and column_name in "
            + "('status','handler','attempts','next_attempt_at','claimed_by','claimed_until','last_error')",
        Integer.class);
    assertEquals(7, status);
    Integer dispatch = jdbc.queryForObject(
        "select count(*) from information_schema.tables where table_name='outbox_dispatch'",
        Integer.class);
    assertEquals(1, dispatch);
  }

  @Test
  void v12_bgJobWorkerColumnsPresent() {
    Integer n = jdbc.queryForObject(
        "select count(*) from information_schema.columns where table_name='background_job'"
            + " and column_name in "
            + "('owner','lease_expires_at','max_attempts','error_code','started_at',"
            + "'finished_at','next_attempt_at','timeout_ms')",
        Integer.class);
    assertEquals(8, n);
  }

  @Test
  void v12_exportWorkerColumnsPresent() {
    Integer n = jdbc.queryForObject(
        "select count(*) from information_schema.columns where table_name='report_export_job'"
            + " and column_name in ('object_key','file_size_bytes','error_code','updated_at')",
        Integer.class);
    assertEquals(4, n);
  }

  @Test
  void v12_dispatchLedgerDedupsEventHandler() {
    UUID agg = UUID.randomUUID();
    UUID ev = UUID.randomUUID();
    assertEquals(1, jdbc.update(
        "insert into outbox_event(id,aggregate_type,aggregate_id,type,handler) values (?,?,?,?,?)",
        ev, "task", agg, "task.created", "task.created"));
    assertEquals(1, jdbc.update(
        "insert into outbox_dispatch(event_id,handler) values (?,?)", ev, "task.created"));
    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
        "insert into outbox_dispatch(event_id,handler) values (?,?)", ev, "task.created"));
  }

  // ---- soft-delete keeps the row; V5 notification seq exists ----

  @Test
  void softDelete_taskRowRetained() {
    UUID u = newUser("soft" + UUID.randomUUID().toString().substring(0, 6));
    UUID t = newTask(newProject(newWorkspace(u, "TEAM")));
    assertEquals(1, jdbc.update("update task set deleted_at=now() where id=?", t));
    assertEquals(1, (int) jdbc.queryForObject(
        "select count(*) from task where id=?", Integer.class, t));
  }

  @Test
  void notification_seqAssignedByDb() {
    UUID u = newUser("notif" + UUID.randomUUID().toString().substring(0, 6));
    UUID id = UUID.randomUUID();
    assertEquals(1, jdbc.update(
        "insert into notification(id,user_id,kind) values (?,?,?)", id, u, "TASK_ASSIGNED"));
    Long seq = jdbc.queryForObject("select seq from notification where id=?", Long.class, id);
    assertNotNull(seq);
  }
}
