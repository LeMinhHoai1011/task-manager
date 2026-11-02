package vn.smarttask.itg;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import static org.junit.jupiter.api.Assertions.*;

class V2LifecycleIT extends ItgBase {
  @Autowired vn.smarttask.jobs.TaskAssignmentNotificationHandler notificationHandler;
  @Autowired vn.smarttask.common.repo.OutboxEventRepository events;
  @Autowired vn.smarttask.task.service.TaskCreationService creation;
  @Autowired vn.smarttask.task.service.TaskService taskService;
  private Map<String,String> headers(long version){return Map.of("If-Match",String.valueOf(version),"Idempotency-Key",idem());}
  @Test void invitationThroughApprovalAndReloginUsesOnlyPublicApi() throws Exception {
    var owner=activeUser("v2owner","Password123!");var member=activeUser("v2member","Password123!");var outsider=activeUser("v2other","Password123!");
    String manager=loginToken(owner.getUsername(),"Password123!"),executor=loginToken(member.getUsername(),"Password123!"),other=loginToken(outsider.getUsername(),"Password123!");
    var personal=json(doPost("/api/v1/me/tasks",manager,Map.of("title","Personal lifecycle"),headers(1))).path("data");
    assertEquals(200,doPatch("/api/v1/me/tasks/"+personal.path("id").asText()+"/completion",manager,Map.of("completed",true),headers(1)).getResponse().getStatus());
    var ws=json(doPost("/api/v1/workspaces",manager,Map.of("name","V2 lifecycle","type","TEAM"))).path("data").path("id").asText();
    var invitation=doPost("/api/v1/workspaces/"+ws+"/invites",manager,Map.of("email",member.getEmail(),"role","MEMBER"));
    assertEquals(200,invitation.getResponse().getStatus());String invite=json(invitation).path("data").path("invitation_id").asText();
    assertEquals(404,doPost("/api/v1/me/invitations/"+invite+"/accept",other,Map.of()).getResponse().getStatus());
    assertEquals(1,json(doGet("/api/v1/me/invitations",executor)).path("data").size());
    assertEquals(200,doPost("/api/v1/me/invitations/"+invite+"/accept",executor,Map.of()).getResponse().getStatus());
    assertEquals(200,doPost("/api/v1/me/invitations/"+invite+"/accept",executor,Map.of()).getResponse().getStatus());
    String team=json(doPost("/api/v1/workspaces/"+ws+"/teams",manager,Map.of("name","V2 team"))).path("data").path("id").asText();
    assertEquals(200,doPost("/api/v1/teams/"+team+"/members",manager,Map.of("userId",member.getId(),"role","MEMBER"),headers(1)).getResponse().getStatus());
    String project=json(doPost("/api/v1/projects",manager,Map.of("workspace_id",ws,"name","V2 Project"))).path("data").path("id").asText();
    assertEquals(200,doPost("/api/v1/projects/"+project+"/members",manager,Map.of("user_id",member.getId(),"role","MEMBER")).getResponse().getStatus());
    assertEquals(403,doPost("/api/v1/projects/"+project+"/activate",executor,Map.of(),headers(1)).getResponse().getStatus());
    assertEquals(200,doPost("/api/v1/projects/"+project+"/activate",manager,Map.of(),headers(1)).getResponse().getStatus());
    assertEquals(409,doPost("/api/v1/projects/"+project+"/activate",manager,Map.of(),headers(1)).getResponse().getStatus());
    long pv=json(doGet("/api/v1/projects/"+project+"/settings",manager)).path("data").path("version").asLong();
    assertEquals(200,doPatch("/api/v1/projects/"+project+"/approval",manager,Map.of("enabled",true,"version",pv),headers(pv)).getResponse().getStatus());
    var createHeaders=headers(1);var createBody=Map.of("project_id",project,"title","V2 review");
    var created=json(doPost("/api/v1/tasks",manager,createBody,createHeaders)).path("data");String task=created.path("id").asText();
    assertEquals(task,json(doPost("/api/v1/tasks",manager,createBody,createHeaders)).path("data").path("id").asText());
    assertEquals(409,doPost("/api/v1/tasks",manager,Map.of("project_id",project,"title","Changed payload"),createHeaders).getResponse().getStatus());
    assertEquals(200,doPost("/api/v1/tasks/"+task+"/primary-assignee",manager,Map.of("userId",member.getId()),headers(1)).getResponse().getStatus());
    long version=json(doGet("/api/v1/tasks/"+task,manager)).path("data").path("version").asLong();
    assertEquals(200,mvc.perform(MockMvcRequestBuilders.put("/api/v1/tasks/"+task+"/team").header("Authorization","Bearer "+manager).header("If-Match",version).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(Map.of("teamId",team)))).andReturn().getResponse().getStatus());
    assertEquals(200,doPost("/api/v1/tasks/"+task+"/checklist",manager,Map.of("title","Acceptance checklist"),headers(1)).getResponse().getStatus());
    String item=json(doGet("/api/v1/tasks/"+task+"/checklist",manager)).path("data").get(0).path("id").asText();
    version=json(doGet("/api/v1/tasks/"+task,executor)).path("data").path("version").asLong();
    var tick=doPatch("/api/v1/tasks/"+task+"/checklist/"+item,executor,Map.of("done",true),headers(version));assertEquals(200,tick.getResponse().getStatus());
    version=json(tick).path("data").path("version").asLong();
    assertEquals(200,doPost("/api/v1/tasks/"+task+"/comments",executor,Map.of("body","Ready for review"),headers(version)).getResponse().getStatus());
    assertEquals(403,doPatch("/api/v1/tasks/"+task,executor,Map.of("title","Forbidden rename"),headers(version)).getResponse().getStatus());
    assertEquals(200,doPost("/api/v1/tasks/"+task+"/subtasks",manager,Map.of("title","Persistent child"),headers(version)).getResponse().getStatus());
    assertEquals(1,json(doGet("/api/v1/tasks/"+task+"/subtasks",manager)).path("data").size());
    var event=jdbc.queryForObject("select id from outbox_event where aggregate_id=? and type='task.reassigned' order by created_at desc limit 1",UUID.class,UUID.fromString(task));
    notificationHandler.handle(events.findById(event).orElseThrow());
    assertTrue(json(doGet("/api/v1/notifications",executor)).path("data").toString().contains("TASK_REASSIGNED"));
    var start=doPost("/api/v1/tasks/"+task+"/workflow/start",executor,Map.of(),headers(version));assertEquals(200,start.getResponse().getStatus());version=json(start).path("data").path("version").asLong();
    var blockedSubmit=doPost("/api/v1/tasks/"+task+"/workflow/submit",executor,Map.of(),headers(version));assertEquals(409,blockedSubmit.getResponse().getStatus());assertEquals("SUBTASKS_INCOMPLETE",reasonCode(blockedSubmit));
    var child=json(doGet("/api/v1/tasks/"+task+"/subtasks",manager)).path("data").get(0);
    assertEquals(200,doPost("/api/v1/tasks/"+child.path("id").asText()+"/workflow/cancel",manager,Map.of("reason","Covered in parent acceptance"),headers(child.path("version").asLong())).getResponse().getStatus());
    var submit=doPost("/api/v1/tasks/"+task+"/workflow/submit",executor,Map.of(),headers(version));assertEquals(200,submit.getResponse().getStatus());version=json(submit).path("data").path("version").asLong();
    assertEquals("PENDING_APPROVAL",json(submit).path("data").path("to_state").asText());
    assertEquals(423,doPatch("/api/v1/tasks/"+task+"/checklist/"+item,executor,Map.of("done",false),headers(version)).getResponse().getStatus());
    assertEquals(200,doPost("/api/v1/tasks/"+task+"/workflow/approve",manager,Map.of(),headers(version)).getResponse().getStatus());
    assertEquals(1,json(doGet("/api/v1/dashboard?projectId="+project,manager)).path("data").path("completed").asInt());
    assertEquals(200,doPost("/api/v1/auth/logout",executor,Map.of()).getResponse().getStatus());
    assertEquals(401,doGet("/api/v1/tasks/"+task,executor).getResponse().getStatus());
    String relogin=loginToken(member.getUsername(),"Password123!");
    assertEquals("COMPLETED",json(doGet("/api/v1/tasks/"+task,relogin)).path("data").path("state").asText());
  }
  @Test void acceptedInvitationCannotRestoreRevokedMembershipAndExpiredInviteCannotJoin() throws Exception {
    var owner=activeUser("inviteowner","Password123!");var target=activeUser("invitetarget","Password123!");var ws=mkWorkspace(owner.getId(),"invites");
    String token=loginToken(target.getUsername(),"Password123!");var invitation=workspaces.invite(owner.getId(),ws,target.getEmail(),"MEMBER",7);
    workspaces.acceptInvitation(target.getId(),invitation.getId());
    jdbc.update("update workspace_membership set status='REMOVED' where workspace_id=? and user_id=?",ws,target.getId());
    assertEquals(409,doPost("/api/v1/me/invitations/"+invitation.getId()+"/accept",token,Map.of()).getResponse().getStatus());
    var expired=workspaces.invite(owner.getId(),ws,target.getEmail(),"MEMBER",7);jdbc.update("update workspace_invitation set expires_at=now()-interval '1 minute' where id=?",expired.getId());
    assertEquals(409,doPost("/api/v1/me/invitations/"+expired.getId()+"/accept",token,Map.of()).getResponse().getStatus());
  }
  @Test void concurrentProjectTaskCreationWithSameKeyCreatesOnlyOneTask() throws Exception {
    var owner=activeUser("createowner","Password123!");var ws=mkWorkspace(owner.getId(),"create");var project=mkActiveProject(owner.getId(),ws,"create");
    var input=new vn.smarttask.task.service.TaskCreationService.Input(project.getId(),"Concurrent task",null,"MEDIUM",null,null,null,null);String key=idem();
    var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var gate=new java.util.concurrent.CountDownLatch(1);
    try{var a=pool.submit(()->{gate.await();return creation.create(owner.getId(),input,key).getId();});var b=pool.submit(()->{gate.await();return creation.create(owner.getId(),input,key).getId();});gate.countDown();assertEquals(a.get(15,java.util.concurrent.TimeUnit.SECONDS),b.get(15,java.util.concurrent.TimeUnit.SECONDS));assertEquals(1,jdbc.queryForObject("select count(*) from task where project_id=?",Integer.class,project.getId()));}finally{pool.shutdownNow();}
  }
  @Test void registrationAfterAnEmailInviteRequiresAReissuedInAppInvitation() {
    var owner=activeUser("oldinviteowner","Password123!");var ws=mkWorkspace(owner.getId(),"oldinvite");String email="invite_"+tag()+"@itg.io";
    var original=workspaces.invite(owner.getId(),ws,email,"MEMBER",7);
    var recipient=identity.register("invite_"+tag(),email,"Password123!","New recipient");
    var denied=assertThrows(vn.smarttask.common.AppException.class,()->workspaces.acceptInvitation(recipient.getId(),original.getId()));assertEquals("INVITATION_REISSUE_REQUIRED",denied.getReasonCode());
    var reissued=workspaces.invite(owner.getId(),ws,email,"MEMBER",7);assertEquals(ws,workspaces.acceptInvitation(recipient.getId(),reissued.getId()));
  }
  @Test void concurrentMetadataWritesWithSameVersionAcceptExactlyOne() throws Exception {
    var owner=activeUser("metadataowner","Password123!");var ws=mkWorkspace(owner.getId(),"metadata");var project=mkActiveProject(owner.getId(),ws,"metadata");
    var task=creation.create(owner.getId(),new vn.smarttask.task.service.TaskCreationService.Input(project.getId(),"Before",null,"MEDIUM",null,null,null,null),idem());
    var gate=new java.util.concurrent.CountDownLatch(1);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
    java.util.concurrent.Callable<String> edit=()->{gate.await();try{taskService.updateContent(owner.getId(),task.getId(),"After",null,task.getVersion());return "OK";}catch(vn.smarttask.common.AppException e){return e.getReasonCode();}};
    try{var a=pool.submit(edit);var b=pool.submit(edit);gate.countDown();assertEquals(Set.of("OK","VERSION_CONFLICT"),new HashSet<>(List.of(a.get(15,java.util.concurrent.TimeUnit.SECONDS),b.get(15,java.util.concurrent.TimeUnit.SECONDS))));}finally{pool.shutdownNow();}
  }
}
