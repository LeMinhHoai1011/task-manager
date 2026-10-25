package vn.smarttask.itg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import vn.smarttask.identity.entity.AppUser;
import vn.smarttask.project.entity.Project;
import vn.smarttask.task.entity.Task;

/**
 * F2.2/4 RbacIT: anti-oracle mapping over real HTTP + real policy.
 * No-read -&gt; 404 (existence hidden) with {@code DENIED_NOT_FOUND} audit;
 * visible-but-write-denied -&gt; 403. DELETED scope reads/writes as 404.
 * Assignee (self-approval) and submitter approvals -&gt; 403.
 */
class RbacIT extends ItgBase {

  record World(AppUser mgr, AppUser member, AppUser guest, AppUser outsider,
      UUID wsId, Project project, Task task) {}

  private World world(String prefix) {
    var mgr = activeUser(prefix + "m", "Secret123!");
    var member = activeUser(prefix + "b", "Secret123!");
    var guest = activeUser(prefix + "g", "Secret123!");
    var outsider = activeUser(prefix + "o", "Secret123!");
    UUID ws = mkWorkspace(mgr.getId(), prefix);
    addWsMember(ws, member.getId(), "MEMBER");
    addWsMember(ws, guest.getId(), "MEMBER");
    Project p = mkActiveProject(mgr.getId(), ws, prefix);
    addProjectMember(mgr.getId(), p.getId(), member.getId(), "MEMBER");
    addProjectMember(mgr.getId(), p.getId(), guest.getId(), "GUEST");
    Task task = mkTask(mgr.getId(), p.getId(), prefix + " task");
    return new World(mgr, member, guest, outsider, ws, p, task);
  }

  private String token(AppUser u) {
    return loginToken(u.getUsername(), "Secret123!");
  }

  @Test
  void workspaceOutsiderWithoutMembershipReadsProjectAs404() throws Exception {
    World w = world("itgr1");
    MvcResult r = doGet("/api/v1/projects/" + w.project().getId(), token(w.outsider()));
    assertEquals(404, r.getResponse().getStatus());
  }

  @Test
  void guestWriteProjectAndTaskIs403() throws Exception {
    World w = world("itgr2");
    String g = token(w.guest());
    Project p = projectRepo.findById(w.project().getId()).orElseThrow();

    MvcResult patch = doPatch("/api/v1/projects/" + p.getId(), g,
        Map.of("name", "hijacked"), Map.of("If-Match", String.valueOf(p.getVersion())));
    assertEquals(403, patch.getResponse().getStatus());

    MvcResult create = doPost("/api/v1/tasks", g,
        Map.of("project_id", p.getId().toString(), "title", "guest task"),
        Map.of("Idempotency-Key", idem()));
    assertEquals(403, create.getResponse().getStatus());
  }

  @Test
  void outsiderReadsTaskAs404WithDeniedNotFoundAudit() throws Exception {
    World w = world("itgr3");
    MvcResult r = doGet("/api/v1/tasks/" + w.task().getId(), token(w.outsider()));
    assertEquals(404, r.getResponse().getStatus());

    Integer n = jdbc.queryForObject(
        "select count(*) from audit_event where action='task.read' and entity_id=? "
            + "and outcome='DENIED' and reason='DENIED_NOT_FOUND' and actor_user_id=?",
        Integer.class, w.task().getId(), w.outsider().getId());
    assertTrue(n != null && n >= 1, "expected DENIED_NOT_FOUND audit for outsider task.read");
  }

  @Test
  void deletedProjectReadsAndWritesAs404() throws Exception {
    World w = world("itgr4");
    Project p = projectRepo.findById(w.project().getId()).orElseThrow();
    p.setStatus("DELETED");
    projectRepo.save(p);
    String m = token(w.mgr());

    MvcResult get = doGet("/api/v1/projects/" + p.getId(), m);
    assertEquals(404, get.getResponse().getStatus());

    MvcResult patch = doPatch("/api/v1/projects/" + p.getId(), m,
        Map.of("name", "x"), Map.of("If-Match", String.valueOf(p.getVersion())));
    assertEquals(404, patch.getResponse().getStatus());
  }

  @Test
  void assigneeAndSubmitterApprovalsAre403() throws Exception {
    String prefix = "itgr5";
    var mgr = activeUser(prefix + "m", "Secret123!");      // approver (designated)
    var submitter = activeUser(prefix + "s", "Secret123!"); // submitter (MANAGER)
    var assignee = activeUser(prefix + "a", "Secret123!");  // assignee (MEMBER)
    UUID ws = mkWorkspace(mgr.getId(), prefix);
    addWsMember(ws, submitter.getId(), "MEMBER");
    addWsMember(ws, assignee.getId(), "MEMBER");
    Project p = mkActiveProject(mgr.getId(), ws, prefix);
    addProjectMember(mgr.getId(), p.getId(), submitter.getId(), "MANAGER");
    addProjectMember(mgr.getId(), p.getId(), assignee.getId(), "MEMBER");
    enableApproval(mgr.getId(), p.getId());

    // submitter creates + assigns + starts + submits (T-02 opens a round for mgr)
    Task task = mkTask(submitter.getId(), p.getId(), prefix + " approval task");
    tasks.assign(submitter.getId(), task.getId(), assignee.getId(),
        taskRepo.findById(task.getId()).orElseThrow().getVersion());
    Task cur = taskRepo.findById(task.getId()).orElseThrow();
    workflow.start(submitter.getId(), task.getId(), cur.getVersion());
    cur = taskRepo.findById(task.getId()).orElseThrow();
    workflow.submit(submitter.getId(), task.getId(), cur.getVersion());
    cur = taskRepo.findById(task.getId()).orElseThrow();
    assertEquals("PENDING_APPROVAL", cur.getState());
    long v = cur.getVersion();

    // assignee (self-approval) -> 403
    MvcResult asg = doPost("/api/v1/tasks/" + task.getId() + "/workflow/approve",
        token(assignee), Map.of(),
        Map.of("If-Match", String.valueOf(v), "Idempotency-Key", idem()));
    assertEquals(403, asg.getResponse().getStatus());

    // submitter -> 403 (not an eligible approver for their own round)
    MvcResult sub = doPost("/api/v1/tasks/" + task.getId() + "/workflow/approve",
        token(submitter), Map.of(),
        Map.of("If-Match", String.valueOf(v), "Idempotency-Key", idem()));
    assertEquals(403, sub.getResponse().getStatus());
  }
}
