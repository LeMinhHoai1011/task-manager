package vn.smarttask.itg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import vn.smarttask.common.UuidV7;
import vn.smarttask.common.service.TokenHash;
import vn.smarttask.files.entity.FileVersion;
import vn.smarttask.identity.entity.AppUser;
import vn.smarttask.project.entity.Project;
import vn.smarttask.workflow.entity.ApprovalDecision;

/**
 * F2.2/6 ConcurrencyIT: two threads, one barrier, two real transactions.
 * Approve race -&gt; exactly one 200 and one 409; the loser observes the
 * committed winner first, so under the row-lock serialization it reports
 * {@code VERSION_CONFLICT} (see comment). The
 * {@code APPROVAL_ALREADY_DECIDED} guard itself is proven deterministically
 * against a pre-decided OPEN round. Stream-ticket race -&gt; exactly one 200
 * (loser 410, single-use).
 */
class ConcurrencyIT extends ItgBase {

  record Race(AppUser submitter, AppUser approver1, AppUser approver2, Project project,
      vn.smarttask.task.entity.Task task) {}

  private Race race(String prefix) {
    var submitter = activeUser(prefix + "s", "Secret123!");
    var a1 = activeUser(prefix + "1", "Secret123!");
    var a2 = activeUser(prefix + "2", "Secret123!");
    UUID ws = mkWorkspace(submitter.getId(), prefix);
    addWsMember(ws, a1.getId(), "MEMBER");
    addWsMember(ws, a2.getId(), "MEMBER");
    Project p = mkActiveProject(submitter.getId(), ws, prefix);
    addProjectMember(submitter.getId(), p.getId(), a1.getId(), "MANAGER");
    addProjectMember(submitter.getId(), p.getId(), a2.getId(), "MANAGER");
    enableApproval(submitter.getId(), p.getId());
    var task = mkTask(submitter.getId(), p.getId(), prefix + " race task");
    return new Race(submitter, a1, a2, p, task);
  }

  private void toPending(Race r) {
    UUID taskId = r.task().getId();
    UUID s = r.submitter().getId();
    workflow.start(s, taskId, taskRepo.findById(taskId).orElseThrow().getVersion());
    workflow.submit(s, taskId, taskRepo.findById(taskId).orElseThrow().getVersion());
    assertEquals("PENDING_APPROVAL", taskRepo.findById(taskId).orElseThrow().getState());
  }

  private MvcResult approveHttp(String token, UUID taskId, long version) throws Exception {
    return doPost("/api/v1/tasks/" + taskId + "/workflow/approve", token, Map.of(),
        Map.of("If-Match", String.valueOf(version), "Idempotency-Key", idem()));
  }

  @Test
  void concurrentApproveYieldsSingleWinnerAndOne409() throws Exception {
    Race r = race("itgc1");
    toPending(r);
    UUID taskId = r.task().getId();
    long v = taskRepo.findById(taskId).orElseThrow().getVersion();
    String t1 = loginToken(r.approver1().getUsername(), "Secret123!");
    String t2 = loginToken(r.approver2().getUsername(), "Secret123!");

    CyclicBarrier gate = new CyclicBarrier(2);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      List<Callable<MvcResult>> jobs = List.of(
          () -> { gate.await(10, TimeUnit.SECONDS); return approveHttp(t1, taskId, v); },
          () -> { gate.await(10, TimeUnit.SECONDS); return approveHttp(t2, taskId, v); });
      List<Future<MvcResult>> futures = pool.invokeAll(jobs, 60, TimeUnit.SECONDS);
      List<Integer> statuses = new ArrayList<>();
      List<String> reasons = new ArrayList<>();
      for (var f : futures) {
        MvcResult res = f.get(10, TimeUnit.SECONDS);
        statuses.add(res.getResponse().getStatus());
        reasons.add(res.getResponse().getStatus() == 200 ? "OK" : reasonCode(res));
      }
      assertEquals(1, statuses.stream().filter(s -> s == 200).count(),
          "exactly one approve must win, got " + statuses + " reasons=" + reasons);
      assertEquals(1, statuses.stream().filter(s -> s == 409).count(),
          "exactly one approve must conflict, got " + statuses + " reasons=" + reasons);
      // Row-lock serialization: the loser blocks on the task lock, then sees the
      // winner's version bump, so it reports VERSION_CONFLICT (409). A second
      // approve with a fresh version would then hit TRANSITION_FORBIDDEN because
      // the task already left PENDING_APPROVAL.
      assertTrue(reasons.contains("VERSION_CONFLICT") || reasons.contains("APPROVAL_ALREADY_DECIDED"),
          "loser must report a 409 conflict reason, got " + reasons);
      assertEquals("COMPLETED", taskRepo.findById(taskId).orElseThrow().getState());
      assertEquals(1, decisions.findAll().stream()
          .filter(d -> rounds.findById(d.getRoundId())
              .map(x -> x.getTaskId().equals(taskId)).orElse(false))
          .count(), "exactly one terminal decision row must exist");
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void approveOnAlreadyDecidedOpenRoundIs409AlreadyDecided() throws Exception {
    Race r = race("itgc2");
    toPending(r);
    UUID taskId = r.task().getId();
    var round = rounds.findFirstByTaskIdOrderByRoundNoDesc(taskId).orElseThrow();
    // Natural mid-state seeded without touching the task row: an OPEN round
    // that already carries a terminal decision (the race window the service
    // guard exists for).
    decisions.save(new ApprovalDecision(UuidV7.generate(), round.getId(), "APPROVED",
        r.approver1().getId(), null));

    MvcResult res = approveHttp(
        loginToken(r.approver2().getUsername(), "Secret123!"), taskId,
        taskRepo.findById(taskId).orElseThrow().getVersion());
    assertEquals(409, res.getResponse().getStatus());
    assertEquals("APPROVAL_ALREADY_DECIDED", reasonCode(res));
  }

  @Test
  void concurrentStreamConsumeYieldsSingleWinner() throws Exception {
    String prefix = "itgc3";
    var user = activeUser(prefix + "u", "Secret123!");
    UUID ws = mkWorkspace(user.getId(), prefix);
    Project p = mkActiveProject(user.getId(), ws, prefix);
    var task = mkTask(user.getId(), p.getId(), prefix + " file task");

    // attachment via the production entity path (Attachment has @PrePersist)
    UUID attId = UuidV7.generate();
    attachments.save(new vn.smarttask.files.entity.Attachment(attId, task.getId(),
        "payload.bin", "text/plain", 11L, user.getId()));

    // version row via the entity (has @PrePersist) + real bytes under the local gateway root
    String key = "itg/" + UUID.randomUUID() + "/payload.bin";
    byte[] payload = "hello-itg-3".getBytes();
    var fv = new FileVersion(UuidV7.generate(), attId, 1, key, payload.length);
    fv.setMimeType("text/plain");
    fv.setCreatedBy(user.getId());
    fileVersions.save(fv);
    Path local = Paths.get("var", "files", key.replace("/", java.io.File.separator));
    Files.createDirectories(local.getParent());
    Files.write(local, payload);

    // mint through the production preview endpoint (per-request authz + evidence rows)
    String token = loginToken(user.getUsername(), "Secret123!");
    MvcResult preview = doGet("/api/v1/files/" + attId + "/preview", token);
    String previewBody = preview.getResponse().getContentAsString();
    assertEquals(200, preview.getResponse().getStatus(), "preview mint failed: " + previewBody);
    String raw = json(preview).path("data").path("ticket").asText();
    assertTrue(raw != null && !raw.isBlank());

    CyclicBarrier gate = new CyclicBarrier(2);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Callable<MvcResult> job = () -> {
        gate.await(10, TimeUnit.SECONDS);
        var req = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .get("/api/v1/files/stream/" + raw)
            .header("X-Forwarded-For", fwd())
            .header("Authorization", "Bearer " + token);
        return mvc.perform(req).andReturn();
      };
      List<Future<MvcResult>> futures = pool.invokeAll(List.of(job, job), 60, TimeUnit.SECONDS);
      List<Integer> statuses = new ArrayList<>();
      for (var f : futures) {
        statuses.add(f.get(10, TimeUnit.SECONDS).getResponse().getStatus());
      }
      assertEquals(1, statuses.stream().filter(s -> s == 200).count(),
          "exactly one stream consume must win, got " + statuses);
      assertEquals(1, statuses.stream().filter(s -> s == 410).count(),
          "loser must see single-use 410, got " + statuses);
      // winner really got the bytes
      for (var f : futures) {
        MvcResult res = f.get(10, TimeUnit.SECONDS);
        if (res.getResponse().getStatus() == 200) {
          assertEquals(payload.length, res.getResponse().getContentAsByteArray().length);
        }
      }
    } finally {
      pool.shutdownNow();
      Files.deleteIfExists(local);
    }
  }

  @Autowired vn.smarttask.files.repo.FileVersionRepository fileVersions;
}
