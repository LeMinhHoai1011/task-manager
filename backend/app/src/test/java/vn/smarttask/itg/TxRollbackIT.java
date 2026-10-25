package vn.smarttask.itg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MvcResult;
import vn.smarttask.common.entity.OutboxEvent;
import vn.smarttask.common.repo.OutboxEventRepository;
import vn.smarttask.common.service.OutboxService;
import vn.smarttask.identity.entity.AppUser;
import vn.smarttask.project.entity.Project;

/**
 * F2.2/8 TxRollbackIT: a failure late in the approve transaction (forced at
 * the outbox emit for {@code task.workflow.t_04}, the last write in
 * {@code WorkflowService.move}) must roll back everything: no decision row,
 * task state unchanged, round still OPEN.
 *
 * <p>The fault is injected with a delegating {@link OutboxService} override
 * scoped to this class's Spring context only (a natural runtime exception,
 * no DB tampering, no Mockito).
 */
class TxRollbackIT extends ItgBase {

  @TestConfiguration
  static class FailT04Outbox {
    @Bean
    @Primary
    OutboxService outboxService(OutboxEventRepository repo) {
      OutboxService real = new OutboxService(repo);
      return new OutboxService(repo) {
        @Override
        public OutboxEvent emit(String aggregateType, UUID aggregateId, String type,
            String payloadJson) {
          if ("task.workflow.t_04".equals(type)) {
            throw new IllegalStateException("ITG forced outbox failure mid-approve");
          }
          return real.emit(aggregateType, aggregateId, type, payloadJson);
        }
      };
    }
  }

  @Test
  void approveFailureMidTransactionRollsBackDecisionAndState() throws Exception {
    String prefix = "itgt1";
    var submitter = activeUser(prefix + "s", "Secret123!");
    var approver = activeUser(prefix + "a", "Secret123!");
    UUID ws = mkWorkspace(submitter.getId(), prefix);
    addWsMember(ws, approver.getId(), "MEMBER");
    Project p = mkActiveProject(submitter.getId(), ws, prefix);
    addProjectMember(submitter.getId(), p.getId(), approver.getId(), "MANAGER");
    enableApproval(submitter.getId(), p.getId());

    var task = mkTask(submitter.getId(), p.getId(), prefix + " rollback task");
    UUID taskId = task.getId();
    workflow.start(submitter.getId(), taskId,
        taskRepo.findById(taskId).orElseThrow().getVersion());
    workflow.submit(submitter.getId(), taskId,
        taskRepo.findById(taskId).orElseThrow().getVersion());
    UUID roundId = rounds.findFirstByTaskIdOrderByRoundNoDesc(taskId).orElseThrow().getId();
    long v = taskRepo.findById(taskId).orElseThrow().getVersion();
    assertEquals("PENDING_APPROVAL", taskRepo.findById(taskId).orElseThrow().getState());

    MvcResult r;
    try {
      r = doPost("/api/v1/tasks/" + taskId + "/workflow/approve",
          loginToken(approver.getUsername(), "Secret123!"), Map.of(),
          Map.of("If-Match", String.valueOf(v), "Idempotency-Key", idem()));
    } catch (jakarta.servlet.ServletException ex) {
      // Unhandled IllegalStateException from the forced outbox failure bubbles
      // through MockMvc (no ErrorEnvelope mapping): the tx still must roll back.
      assertTrue(ex.getCause() instanceof IllegalStateException
          && ex.getCause().getMessage().contains("ITG forced outbox failure"),
          "expected the forced outbox failure, got " + ex);
      r = null;
    }
    if (r != null) {
      assertEquals(500, r.getResponse().getStatus());
    }

    // rolled back: no decision, state and round untouched
    assertTrue(decisions.findByRoundId(roundId).isEmpty(),
        "decision row must not survive the rollback");
    assertEquals("PENDING_APPROVAL", taskRepo.findById(taskId).orElseThrow().getState());
    assertEquals("OPEN", rounds.findById(roundId).orElseThrow().getStatus());
  }
}
