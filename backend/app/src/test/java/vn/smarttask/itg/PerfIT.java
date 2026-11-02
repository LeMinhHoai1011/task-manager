package vn.smarttask.itg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import vn.smarttask.common.UuidV7;
import vn.smarttask.identity.entity.AppUser;
import vn.smarttask.reports.entity.ReportExportJob;
import vn.smarttask.reports.repo.ReportExportJobRepository;
import vn.smarttask.reports.service.ReportService;
import vn.smarttask.security.GrantService;
import vn.smarttask.task.entity.TaskAssignment;
import vn.smarttask.task.entity.TaskTag;
import vn.smarttask.task.repo.TaskAssignmentRepository;
import vn.smarttask.task.repo.TaskTagRepository;

/**
 * F2.5 Performance Foundation live verification: every rewritten list path
 * executes against real PostgreSQL (scoped WHERE + keyset/EXISTS queries,
 * new repository methods) with authorization semantics intact (404/403,
 * sort/order params unchanged, legacy id cursors still accepted).
 */
class PerfIT extends ItgBase {

  @Autowired ReportService reports;
  @Autowired TaskAssignmentRepository taskAssigns;
  @Autowired TaskTagRepository taskTags;
  @Autowired ReportExportJobRepository exports;
  @Autowired GrantService grantService;

  record Flow(AppUser mgr, AppUser member, String mgrToken, String memberToken,
      vn.smarttask.project.entity.Project project, vn.smarttask.task.entity.Task task) {}

  private Flow flow(String prefix) {
    var mgr = activeUser(prefix + "mgr", "Secret123!");
    var member = activeUser(prefix + "mbr", "Secret123!");
    UUID ws = mkWorkspace(mgr.getId(), prefix);
    addWsMember(ws, member.getId(), "MEMBER");
    var p = mkActiveProject(mgr.getId(), ws, prefix);
    addProjectMember(mgr.getId(), p.getId(), member.getId(), "MEMBER");
    var t = mkTask(mgr.getId(), p.getId(), prefix + " perf task");
    taskAssigns.save(new TaskAssignment(UuidV7.generate(),t.getId(),member.getId()));
    return new Flow(mgr, member, loginToken(mgr.getUsername(), "Secret123!"),
        loginToken(member.getUsername(), "Secret123!"), p, t);
  }

  private static boolean containsTask(MvcResult r, UUID id, ItgBase b) throws Exception {
    return b.om.readTree(r.getResponse().getContentAsString())
        .path("data").path("data").toString().contains(id.toString());
  }

  @Test
  void unscopedTaskListIsScopedAndKeysetPaged() throws Exception {
    Flow f = flow("pf1");
    var another=mkTask(f.mgr().getId(),f.project().getId(),"pf1 second assigned task");
    taskAssigns.save(new TaskAssignment(UuidV7.generate(),another.getId(),f.member().getId()));
    // unscoped: visibility via indexed membership/assignment queries (no full scan)
    MvcResult all = doGet("/api/v1/tasks", f.memberToken());
    assertEquals(200, all.getResponse().getStatus());
    assertTrue(containsTask(all, f.task().getId(), this));

    MvcResult first = doGet("/api/v1/tasks?limit=1", f.memberToken());
    assertEquals(200, first.getResponse().getStatus());
    String cursor = json(first).path("data").path("next_cursor").asText();
    assertTrue(cursor != null && !cursor.isBlank());
    MvcResult second = doGet("/api/v1/tasks?limit=1&cursor="
        + java.net.URLEncoder.encode(cursor, java.nio.charset.StandardCharsets.UTF_8),
        f.memberToken());
    assertEquals(200, second.getResponse().getStatus());
  }

  @Test
  void taskFiltersKeepSemantics() throws Exception {
    Flow f = flow("pf2");
    taskTags.save(new TaskTag(UuidV7.generate(), f.task().getId(), "pftag"));

    MvcResult byAssignee = doGet("/api/v1/tasks?project_id=" + f.project().getId()
        + "&assignee_id=" + f.member().getId(), f.mgrToken());
    assertEquals(200, byAssignee.getResponse().getStatus());
    assertTrue(containsTask(byAssignee, f.task().getId(), this));

    MvcResult byTag = doGet("/api/v1/tasks?project_id=" + f.project().getId() + "&tag=pftag",
        f.mgrToken());
    assertEquals(200, byTag.getResponse().getStatus());
    assertTrue(containsTask(byTag, f.task().getId(), this));

    // outsider still hidden as 404
    var outsider = activeUser("pf2out", "Secret123!");
    MvcResult denied = doGet("/api/v1/tasks?project_id=" + f.project().getId(),
        loginToken(outsider.getUsername(), "Secret123!"));
    assertEquals(404, denied.getResponse().getStatus());
  }

  @Test
  void projectAndWorkspaceLists() throws Exception {
    Flow f = flow("pf3");
    MvcResult p = doGet("/api/v1/projects", f.memberToken());
    assertEquals(200, p.getResponse().getStatus());
    assertTrue(p.getResponse().getContentAsString().contains(f.project().getId().toString()));

    MvcResult w = doGet("/api/v1/workspaces", f.memberToken());
    assertEquals(200, w.getResponse().getStatus());
  }

  @Test
  void calendarAndIcsScoped() throws Exception {
    Flow f = flow("pf4");
    var t = taskRepo.findById(f.task().getId()).orElseThrow();
    t.setDueAt(OffsetDateTime.now(ZoneOffset.UTC).plusDays(2));
    taskRepo.save(t);

    MvcResult cal = doGet("/api/v1/calendar?view=month&timezone=UTC", f.memberToken());
    assertEquals(200, cal.getResponse().getStatus());
    assertTrue(cal.getResponse().getContentAsString().contains(f.task().getId().toString()));

    MvcResult ics = mvc.perform(MockMvcRequestBuilders.get("/api/v1/calendar/ics")
        .header("X-Forwarded-For", fwd())
        .header("Authorization", "Bearer " + f.memberToken())).andReturn();
    assertEquals(200, ics.getResponse().getStatus());
    assertTrue(ics.getResponse().getContentAsString()
        .contains("UID:" + f.task().getId() + "@smarttask"));
  }

  @Test
  void exportListScopedToRequester() throws Exception {
    Flow f = flow("pf5");
    ReportExportJob mine = new ReportExportJob(UuidV7.generate(), "PROJECT_PROGRESS",
        "PROJECT", f.project().getId(), LocalDate.now().minusDays(7), LocalDate.now(),
        "CSV", f.mgr().getId(), "perf-" + UUID.randomUUID(),
        OffsetDateTime.now(ZoneOffset.UTC).plusHours(24));
    mine.setStatus("QUEUED");
    exports.save(mine);

    MvcResult r = doGet("/api/v1/reports/export", f.mgrToken());
    assertEquals(200, r.getResponse().getStatus());
    String body = r.getResponse().getContentAsString();
    assertTrue(body.contains(mine.getId().toString()));

    // another requester must not see it
    MvcResult other = doGet("/api/v1/reports/export", f.memberToken());
    assertEquals(200, other.getResponse().getStatus());
    assertTrue(!other.getResponse().getContentAsString().contains(mine.getId().toString()));
  }

  @Test
  void dashboardsExecuteLive() {
    Flow f = flow("pf6");
    var personal = reports.personalDashboard(f.member().getId());
    assertTrue(personal.containsKey("metrics"));
    var project = reports.projectDashboard(f.mgr().getId(), f.project().getId());
    assertTrue(project.containsKey("metrics"));
    assertTrue(project.toString().contains("TASK_BY_STATE_TODO"));
  }

  @Test
  void adminAuditAndJobsPaged() throws Exception {
    var sa = activeUser("pf7sa", "Secret123!");
    grantService.grantBootstrap(sa.getId(), GrantService.SYSTEM_ADMIN);
    String token = loginToken(sa.getUsername(), "Secret123!");

    MvcResult audit = doGet("/api/v1/admin/audit?limit=5", token);
    assertEquals(200, audit.getResponse().getStatus());
    assertTrue(audit.getResponse().getContentAsString().contains("payload"));

    MvcResult jobs = doGet("/api/v1/admin/jobs", token);
    assertEquals(200, jobs.getResponse().getStatus());

    // non-admin stays forbidden
    Flow f = flow("pf7");
    MvcResult denied = doGet("/api/v1/admin/audit?limit=5", f.memberToken());
    assertEquals(403, denied.getResponse().getStatus());
  }

  @Test
  void legacyIdCursorStillAccepted() throws Exception {
    Flow f = flow("pf8");
    var another=mkTask(f.mgr().getId(), f.project().getId(), "pf8 second task");
    taskAssigns.save(new TaskAssignment(UuidV7.generate(),another.getId(),f.member().getId()));
    MvcResult first = doGet("/api/v1/tasks?project_id=" + f.project().getId() + "&limit=1",
        f.memberToken());
    assertEquals(200, first.getResponse().getStatus());
    String idCursor = json(first).path("data").path("data").get(0).path("id").asText();
    MvcResult second = doGet("/api/v1/tasks?project_id=" + f.project().getId()
        + "&limit=1&cursor=" + idCursor, f.memberToken());
    assertEquals(200, second.getResponse().getStatus());
    assertEquals(1, json(second).path("data").path("data").size());
  }
}
