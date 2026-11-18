package vn.smarttask.itg;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.smarttask.task.service.TaskBatchService;
import static vn.smarttask.task.service.TaskBatchService.*;

class TaskBatchIT extends ItgBase {
  @Autowired TaskBatchService batch;

  @Test void partialSuccessChecksEachPermissionAndVersionOverHttp() throws Exception {
    var manager = activeUser("batchpm","Passw0rd!");
    var stranger = activeUser("batchother","Passw0rd!");
    var project = mkActiveProject(manager.getId(),mkWorkspace(manager.getId(),"batch"),"batch");
    var otherProject = mkActiveProject(stranger.getId(),mkWorkspace(stranger.getId(),"other"),"other");
    var valid = mkTask(manager.getId(),project.getId(),"Valid");
    var stale = mkTask(manager.getId(),project.getId(),"Stale");
    var forbidden = mkTask(stranger.getId(),otherProject.getId(),"Hidden");
    String token = loginToken(manager.getUsername(),"Passw0rd!");
    var request = new Request(List.of(new Item(valid.getId(),1L),new Item(stale.getId(),99L),
        new Item(forbidden.getId(),1L)),Action.PRIORITY,"HIGH");
    var response = doPatch("/api/v1/tasks/batch",token,request,Map.of("Idempotency-Key",idem()));
    assertEquals(200,response.getResponse().getStatus(),response.getResponse().getContentAsString());
    var result = json(response).path("data");
    assertTrue(result.get(0).path("success").asBoolean());
    assertEquals("VERSION_CONFLICT",result.get(1).path("code").asText());
    assertEquals(404,result.get(2).path("http_status").asInt());
    assertEquals("HIGH",taskRepo.findById(valid.getId()).orElseThrow().getPriority());
    assertEquals(1,taskRepo.findById(stale.getId()).orElseThrow().getVersion());
    assertEquals("MEDIUM",taskRepo.findById(forbidden.getId()).orElseThrow().getPriority());
  }

  @Test void retryReplaysSuccessAndRejectsChangedInputIncludingAfterDelete() {
    var manager = activeUser("batchretry","Passw0rd!");
    var project = mkActiveProject(manager.getId(),mkWorkspace(manager.getId(),"retry"),"retry");
    var task = mkTask(manager.getId(),project.getId(),"Retry");
    String key = idem();
    var request = new Request(List.of(new Item(task.getId(),1L)),Action.ADD_TAG,"release");
    var first = batch.apply(manager.getId(),request,key);
    assertTrue(first.get(0).success());
    assertEquals(first,batch.apply(manager.getId(),request,key));
    assertEquals(2,taskRepo.findById(task.getId()).orElseThrow().getVersion());
    assertEquals(1,jdbc.queryForObject("select count(*) from task_tag where task_id=?",Integer.class,task.getId()));
    var changed = assertThrows(vn.smarttask.common.AppException.class,
        ()->batch.apply(manager.getId(),new Request(request.items(),Action.ADD_TAG,"other"),key));
    assertEquals("IDEMPOTENCY_CONFLICT",changed.getReasonCode());
    var deletion = new Request(List.of(new Item(task.getId(),2L)),Action.DELETE,null);
    String deleteKey = idem();
    var deleted = batch.apply(manager.getId(),deletion,deleteKey);
    assertTrue(deleted.get(0).success());
    assertEquals(deleted,batch.apply(manager.getId(),deletion,deleteKey));
    assertEquals(3,taskRepo.findById(task.getId()).orElseThrow().getVersion());
  }

  @Test void submitUsesApprovalAndLockedMetadataDoesNotChangeTask() {
    var manager = activeUser("batchflow","Passw0rd!");
    var project = mkActiveProject(manager.getId(),mkWorkspace(manager.getId(),"flow"),"flow");
    enableApproval(manager.getId(),project.getId());
    var task = mkTask(manager.getId(),project.getId(),"Approval");
    var started = batch.apply(manager.getId(),new Request(List.of(new Item(task.getId(),1L)),Action.START,null),idem());
    assertEquals("IN_PROGRESS",started.get(0).state());
    var submitted = batch.apply(manager.getId(),new Request(List.of(new Item(task.getId(),2L)),Action.SUBMIT,null),idem());
    assertEquals("PENDING_APPROVAL",submitted.get(0).state());
    assertTrue(rounds.findFirstByTaskIdOrderByRoundNoDesc(task.getId()).isPresent());
    var locked = batch.apply(manager.getId(),new Request(List.of(new Item(task.getId(),3L)),Action.PRIORITY,"HIGH"),idem());
    assertEquals("EXECUTION_LOCKED",locked.get(0).code());
    var unchanged = taskRepo.findById(task.getId()).orElseThrow();
    assertEquals(3,unchanged.getVersion()); assertEquals("MEDIUM",unchanged.getPriority());
  }

  @Test void concurrentWritesWithSameVersionHaveOnlyOneWinner() throws Exception {
    var manager = activeUser("batchrace","Passw0rd!");
    var project = mkActiveProject(manager.getId(),mkWorkspace(manager.getId(),"race"),"race");
    var task = mkTask(manager.getId(),project.getId(),"Race");
    var input = new Request(List.of(new Item(task.getId(),1L)),Action.PRIORITY,"HIGH");
    try (var pool = Executors.newFixedThreadPool(2)) {
      var gate = new CountDownLatch(1);
      Callable<Result> write = ()->{gate.await();return batch.apply(manager.getId(),input,idem()).get(0);};
      var a = pool.submit(write); var b = pool.submit(write); gate.countDown();
      var results = List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
      assertEquals(1,results.stream().filter(Result::success).count());
      assertEquals(1,results.stream().filter(r->"VERSION_CONFLICT".equals(r.code())).count());
    }
    assertEquals(2,taskRepo.findById(task.getId()).orElseThrow().getVersion());
  }

  @Test void assignmentAndTagRemovalReuseExistingTaskRules() {
    var manager = activeUser("batchassign","Passw0rd!");
    var member = activeUser("batchmember","Passw0rd!");
    var workspace = mkWorkspace(manager.getId(),"assign");
    addWsMember(workspace,member.getId(),"MEMBER");
    var project = mkActiveProject(manager.getId(),workspace,"assign");
    addProjectMember(manager.getId(),project.getId(),member.getId(),"MEMBER");
    var task = mkTask(manager.getId(),project.getId(),"Assigned");
    assertTrue(batch.apply(manager.getId(),new Request(List.of(new Item(task.getId(),1L)),Action.ASSIGN,member.getId().toString()),idem()).get(0).success());
    assertTrue(assignments.findByTaskIdAndUserId(task.getId(),member.getId()).isPresent());
    var denied = batch.apply(member.getId(),new Request(List.of(new Item(task.getId(),2L)),Action.PRIORITY,"HIGH"),idem());
    assertEquals("FORBIDDEN",denied.get(0).code());
    assertTrue(batch.apply(manager.getId(),new Request(List.of(new Item(task.getId(),2L)),Action.ADD_TAG,"qa"),idem()).get(0).success());
    assertTrue(batch.apply(manager.getId(),new Request(List.of(new Item(task.getId(),3L)),Action.REMOVE_TAG,"qa"),idem()).get(0).success());
    assertEquals(0,jdbc.queryForObject("select count(*) from task_tag where task_id=?",Integer.class,task.getId()));
  }
}
