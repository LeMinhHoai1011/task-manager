package vn.smarttask.admin;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.smarttask.admin.repo.SystemSettingRepository;
import vn.smarttask.admin.entity.SystemSetting;
import vn.smarttask.common.AppException;
import vn.smarttask.common.entity.AuditEvent;
import vn.smarttask.common.entity.BackgroundJob;
import vn.smarttask.common.repo.AuditEventRepository;
import vn.smarttask.common.repo.BackgroundJobRepository;
import vn.smarttask.security.GrantService;
import vn.smarttask.security.Principal;

/**
 * F1 admin/health real reads (offline, Mockito): audit rows redacted, jobs
 * + settings from repos, retry re-queues, readiness pings the datasource,
 * metrics stay a labelled stub.
 */
@ExtendWith(MockitoExtension.class)
class AdminHealthRealReadTest {

  @Mock AuditEventRepository audits;
  @Mock BackgroundJobRepository jobs;
  @Mock SystemSettingRepository settings;
  @Mock GrantService grants;
  @Mock DataSource dataSource;

  private AdminController admin;
  private final UUID actor = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    admin = new AdminController(audits, jobs, settings, grants);
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(new Principal(actor, UUID.randomUUID()), null,
            List.of()));
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void auditContentAlwaysRedacted() {
    AuditEvent e = new AuditEvent(UUID.randomUUID(), actor, "task.read", "task",
        UUID.randomUUID(), "ALLOWED", null);
    e.setPayload("{\"title\":\"secret\"}");
    // F2.5: admin audit pages newest-first from the DB (keyset over id).
    when(audits.findAllByOrderByIdDesc(any(org.springframework.data.domain.Pageable.class)))
        .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(e)));

    var res = admin.audit(null, 20);
    assertEquals(1, res.getBody().data().data().size());
    Map<?, ?> row = (Map<?, ?>) res.getBody().data().data().get(0);
    assertEquals("[REDACTED]", row.get("payload"));
    assertEquals("[REDACTED]", row.get("before"));
    assertEquals("[REDACTED]", row.get("after"));
  }

  @Test
  void jobsAndSettingsReadRepos() {
    BackgroundJob j = new BackgroundJob(UUID.randomUUID(), "REPORT_EXPORT", "{}");
    // F2.5: jobs list is newest-100, DB-ordered (table grows unboundedly).
    when(jobs.findAll(any(org.springframework.data.domain.Pageable.class)))
        .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(j)));
    when(settings.findAll()).thenReturn(
        List.of(new SystemSetting("file.max_upload_mb", "50")));

    assertEquals(1, admin.jobs().getBody().data().size());
    assertEquals(1, admin.settings().getBody().data().size());
  }

  @Test
  void retryOnlyFailedJobs() {
    UUID id = UUID.randomUUID();
    BackgroundJob failed = new BackgroundJob(id, "REPORT_EXPORT", "{}");
    failed.setStatus("FAILED");
    when(jobs.findById(id)).thenReturn(Optional.of(failed));

    var res = admin.retryJob(id.toString());
    assertEquals("QUEUED", ((Map<?, ?>) res.getBody().data()).get("status"));

    BackgroundJob queued = new BackgroundJob(UUID.randomUUID(), "REPORT_EXPORT", "{}");
    queued.setStatus("QUEUED");
    when(jobs.findById(queued.getId())).thenReturn(Optional.of(queued));
    AppException ex = assertThrows(AppException.class,
        () -> admin.retryJob(queued.getId().toString()));
    assertEquals(409, ex.getHttpStatus().value());
  }

  @Test
  void nonAdminBlocked() {
    org.mockito.Mockito.doThrow(AppException.forbidden("no"))
        .when(grants).requireGrant(any(), any(), any(), any(), any());
    assertThrows(AppException.class, () -> admin.audit(null, 20));
  }

  @Test
  void readinessDownWhenDbUnreachable() throws Exception {
    when(dataSource.getConnection()).thenThrow(new java.sql.SQLException("down"));
    var res = new HealthController(dataSource).readiness();
    assertEquals(503, res.getStatusCode().value());
    assertEquals(false, ((Map<?, ?>) res.getBody().data()).get("ready"));
  }

  @Test
  void readinessUpWhenDbValid() throws Exception {
    var conn = org.mockito.Mockito.mock(java.sql.Connection.class);
    when(conn.isValid(2)).thenReturn(true);
    when(dataSource.getConnection()).thenReturn(conn);
    var res = new HealthController(dataSource).readiness();
    assertEquals(200, res.getStatusCode().value());
    assertEquals(true, ((Map<?, ?>) res.getBody().data()).get("ready"));
  }

  @Test
  void metricsLabelledStub() {
    var res = new HealthController(dataSource).metrics("all");
    assertEquals("in-memory stub", ((Map<?, ?>) res.getBody().data()).get("note"));
  }
}
