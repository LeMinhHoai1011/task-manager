package vn.smarttask.itg;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TaskResponsibilityIT extends ItgBase {
  @org.springframework.beans.factory.annotation.Autowired vn.smarttask.task.service.TaskResponsibilityService responsibility;
  @org.springframework.beans.factory.annotation.Autowired vn.smarttask.jobs.TaskAssignmentNotificationHandler notificationHandler;
  @org.springframework.beans.factory.annotation.Autowired vn.smarttask.common.repo.OutboxEventRepository outboxEvents;
  @org.springframework.beans.factory.annotation.Autowired vn.smarttask.notify.repo.NotificationRepository notifications;
  @Test void concurrentTransfersAcceptOnlyOneVersion() throws Exception {
    var manager=activeUser("raceowner","Password123!");
    var first=activeUser("raceone","Password123!");var second=activeUser("racetwo","Password123!");
    var ws=mkWorkspace(manager.getId(),"race");var project=mkActiveProject(manager.getId(),ws,"race");
    addProjectMember(manager.getId(),project.getId(),first.getId(),"MEMBER");addProjectMember(manager.getId(),project.getId(),second.getId(),"MEMBER");
    var task=mkTask(manager.getId(),project.getId(),"Concurrent transfer");
    var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var gate=new java.util.concurrent.CountDownLatch(1);
    try {
      java.util.List<java.util.concurrent.Future<Integer>> results=new java.util.ArrayList<>();
      for(var target:java.util.List.of(first,second))results.add(pool.submit(()->{gate.await();try{responsibility.transfer(manager.getId(),task.getId(),target.getId(),task.getVersion());return 200;}catch(vn.smarttask.common.AppException e){return e.getHttpStatus().value();}}));
      gate.countDown();var codes=new java.util.ArrayList<Integer>();for(var result:results)codes.add(result.get(20,java.util.concurrent.TimeUnit.SECONDS));
      java.util.Collections.sort(codes);assertEquals(java.util.List.of(200,409),codes);
      assertEquals(1,assignments.findByTaskIdAndStatus(task.getId(),"ACTIVE").stream().filter(vn.smarttask.task.entity.TaskAssignment::isPrimary).count());
    }finally{pool.shutdownNow();}
  }
  @Test void transferPreservesCollaboratorsAndRequiresManagerAndVersion() throws Exception {
    var manager=activeUser("responsible", "Password123!");
    var first=activeUser("first", "Password123!"); var second=activeUser("second", "Password123!");
    var ws=mkWorkspace(manager.getId(),"primary"); var project=mkActiveProject(manager.getId(),ws,"primary");
    addProjectMember(manager.getId(),project.getId(),first.getId(),"MEMBER");
    addProjectMember(manager.getId(),project.getId(),second.getId(),"MEMBER");
    var task=mkTask(manager.getId(),project.getId(),"Responsibility");
    String token=loginToken(manager.getUsername(),"Password123!"); String member=loginToken(first.getUsername(),"Password123!");
    String path="/api/v1/tasks/"+task.getId()+"/primary-assignee";
    var result=doPost(path,token,Map.of("userId",first.getId()),Map.of("If-Match",String.valueOf(task.getVersion())));
    assertEquals(200,result.getResponse().getStatus(),result.getResponse().getContentAsString());
    int version=json(result).path("data").path("version").asInt();
    assertEquals(403,doPost(path,member,Map.of("userId",second.getId()),Map.of("If-Match",String.valueOf(version))).getResponse().getStatus());
    assertEquals(409,doPost(path,token,Map.of("userId",second.getId()),Map.of("If-Match",String.valueOf(version-1))).getResponse().getStatus());
    assertEquals(200,doPost(path,token,Map.of("userId",second.getId()),Map.of("If-Match",String.valueOf(version))).getResponse().getStatus());
    var active=assignments.findByTaskIdAndStatus(task.getId(),"ACTIVE");
    assertEquals(2,active.size()); assertEquals(1,active.stream().filter(vn.smarttask.task.entity.TaskAssignment::isPrimary).count());
    assertTrue(active.stream().anyMatch(a -> a.getUserId().equals(second.getId()) && a.isPrimary()));
    var timeline=doGet("/api/v1/tasks/"+task.getId()+"/activity",token);
    assertEquals(200,timeline.getResponse().getStatus());
    assertTrue(timeline.getResponse().getContentAsString().contains("TASK_PRIMARY_TRANSFERRED"));
    assertEquals(200,doGet("/api/v1/tasks/"+task.getId()+"/assignment-candidates",token).getResponse().getStatus());
    assertEquals(second.getId().toString(),json(doGet(path,token)).path("data").path("primaryUserId").asText());
    var eventId=jdbc.queryForObject("select id from outbox_event where aggregate_id=? and type='task.reassigned' order by created_at desc,id desc limit 1",java.util.UUID.class,task.getId());
    var event=outboxEvents.findById(eventId).orElseThrow();
    notificationHandler.handle(event);notificationHandler.handle(event);
    for(var recipient:java.util.List.of(first,second)) {
      String dedup="assignment:"+eventId+":"+recipient.getId();
      assertTrue(notifications.findByDedupToken(dedup).isPresent());
      assertEquals(1,notifications.findByUserIdOrderByOccurredAtDesc(recipient.getId()).stream().filter(n->dedup.equals(n.getDedupToken())).count());
    }
    long unread=json(doGet("/api/v1/notifications/unread-count",member)).path("data").path("count").asLong();
    assertTrue(unread>=1 && unread<=2, "One notification per distinct transfer event, not per retry");
    var notification=notifications.findByDedupToken("assignment:"+eventId+":"+first.getId()).orElseThrow();
    assertEquals(200,doGet("/api/v1/notifications/"+notification.getId()+"/target",member).getResponse().getStatus());
    var membership=projectMembers.findMembership(project.getId(),first.getId()).orElseThrow();
    membership.setStatus("REMOVED");projectMembers.save(membership);
    assertEquals(404,doGet("/api/v1/notifications/"+notification.getId()+"/target",member).getResponse().getStatus());
  }
}
