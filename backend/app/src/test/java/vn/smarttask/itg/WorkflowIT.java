package vn.smarttask.itg;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import vn.smarttask.identity.entity.AppUser;
import vn.smarttask.project.entity.Project;

/**
 * F2.2/5 WorkflowIT: start -&gt; submit (T-02) -&gt; approve (T-04) over real
 * HTTP ends in COMPLETED; reject requires a reason; reopen requires a
 * project manager plus a reason.
 */
class WorkflowIT extends ItgBase {

  record Wf(AppUser submitter, AppUser approver, AppUser member, Project project) {}

  private Wf setup(String prefix) {
    var submitter = activeUser(prefix + "s", "Secret123!");
    var approver = activeUser(prefix + "a", "Secret123!");
    var member = activeUser(prefix + "w", "Secret123!");
    UUID ws = mkWorkspace(submitter.getId(), prefix);
    addWsMember(ws, approver.getId(), "MEMBER");
    addWsMember(ws, member.getId(), "MEMBER");
    Project p = mkActiveProject(submitter.getId(), ws, prefix);
    addProjectMember(submitter.getId(), p.getId(), approver.getId(), "MANAGER");
    addProjectMember(submitter.getId(), p.getId(), member.getId(), "MEMBER");
    enableApproval(submitter.getId(), p.getId());
    return new Wf(submitter, approver, member, p);
  }

  private String token(AppUser u) {
    return loginToken(u.getUsername(), "Secret123!");
  }

  private long version(UUID taskId) {
    return taskRepo.findById(taskId).orElseThrow().getVersion();
  }

  private MvcResult transition(String token, UUID taskId, String verb, Object body, long v)
      throws Exception {
    return doPost("/api/v1/tasks/" + taskId + "/workflow/" + verb, token, body,
        Map.of("If-Match", String.valueOf(v), "Idempotency-Key", idem()));
  }

  @Test
  void startSubmitApproveEndsCompleted() throws Exception {
    Wf w = setup("itgw1");
    String sTok = token(w.submitter());
    String aTok = token(w.approver());
    var task = mkTask(w.submitter().getId(), w.project().getId(), "itgw1 flow");

    MvcResult start = transition(sTok, task.getId(), "start", Map.of(), version(task.getId()));
    assertEquals(200, start.getResponse().getStatus());

    MvcResult submit = transition(sTok, task.getId(), "submit", Map.of(), version(task.getId()));
    assertEquals(200, submit.getResponse().getStatus());
    assertEquals("PENDING_APPROVAL",
        taskRepo.findById(task.getId()).orElseThrow().getState());

    MvcResult approve = transition(aTok, task.getId(), "approve", Map.of(), version(task.getId()));
    assertEquals(200, approve.getResponse().getStatus());
    assertEquals("COMPLETED", taskRepo.findById(task.getId()).orElseThrow().getState());

    MvcResult get = doGet("/api/v1/tasks/" + task.getId(), aTok);
    assertEquals(200, get.getResponse().getStatus());
    assertEquals("COMPLETED", json(get).path("data").path("state").asText());
  }

  @Test
  void rejectRequiresReason() throws Exception {
    Wf w = setup("itgw2");
    String sTok = token(w.submitter());
    String aTok = token(w.approver());
    var task = mkTask(w.submitter().getId(), w.project().getId(), "itgw2 reject");
    workflow.start(w.submitter().getId(), task.getId(), version(task.getId()));
    workflow.submit(w.submitter().getId(), task.getId(), version(task.getId()));

    MvcResult noReason = transition(aTok, task.getId(), "reject", Map.of(), version(task.getId()));
    assertEquals(400, noReason.getResponse().getStatus());
    assertEquals("REJECT_REASON_REQUIRED", reasonCode(noReason));

    MvcResult withReason = transition(aTok, task.getId(), "reject",
        Map.of("reason", "needs more work"), version(task.getId()));
    assertEquals(200, withReason.getResponse().getStatus());
    assertEquals("IN_PROGRESS", taskRepo.findById(task.getId()).orElseThrow().getState());
  }

  @Test
  void reopenRequiresManagerAndReason() throws Exception {
    Wf w = setup("itgw3");
    String sTok = token(w.submitter());
    String aTok = token(w.approver());
    String mTok = token(w.member());
    var task = mkTask(w.submitter().getId(), w.project().getId(), "itgw3 reopen");
    workflow.start(w.submitter().getId(), task.getId(), version(task.getId()));
    workflow.submit(w.submitter().getId(), task.getId(), version(task.getId()));
    workflow.approve(w.approver().getId(), task.getId(), version(task.getId()));
    assertEquals("COMPLETED", taskRepo.findById(task.getId()).orElseThrow().getState());

    // plain member (non-manager) cannot reopen even with a reason
    MvcResult memberTry = transition(mTok, task.getId(), "reopen",
        Map.of("reason", "please reopen"), version(task.getId()));
    assertEquals(403, memberTry.getResponse().getStatus());

    // manager without reason -> 400
    MvcResult noReason = transition(sTok, task.getId(), "reopen", Map.of(), version(task.getId()));
    assertEquals(400, noReason.getResponse().getStatus());

    // manager with reason -> 200 back to IN_PROGRESS
    MvcResult ok = transition(sTok, task.getId(), "reopen",
        Map.of("reason", "customer asked to revisit"), version(task.getId()));
    assertEquals(200, ok.getResponse().getStatus());
    assertEquals("IN_PROGRESS", taskRepo.findById(task.getId()).orElseThrow().getState());
  }
}
