package vn.smarttask.itg;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

class AccountCalendarUxIT extends ItgBase {
  @Autowired vn.smarttask.calendar.service.CalendarService calendar;

  private org.springframework.test.web.servlet.MvcResult putJson(String path,String token,Object body) throws Exception {
    return mvc.perform(put(path).with(r->{r.setRemoteAddr(fwd());return r;}).header("Authorization","Bearer "+token)
        .contentType("application/json").content(om.writeValueAsString(body))).andReturn();
  }
  @Test void directoryIncludesActiveAccountsAndNeverReturnsSecrets() throws Exception {
    var admin=activeUser("uxadmin","Password123!");var member=activeUser("uxmember","Password123!");
    grants.grantBootstrap(admin.getId(),"SYSTEM_ADMIN");grants.grantBootstrap(admin.getId(),"ACCOUNT_APPROVE_GLOBAL");
    String a=loginToken(admin.getUsername(),"Password123!"),m=loginToken(member.getUsername(),"Password123!");
    assertEquals(403,doGet("/api/v1/accounts/directory",m).getResponse().getStatus());
    var response=doGet("/api/v1/accounts/directory?q="+member.getUsername()+"&status=ACTIVE",a);
    assertEquals(200,response.getResponse().getStatus());var data=json(response).path("data");
    assertEquals(1,data.path("totalElements").asInt());assertEquals(member.getId().toString(),data.path("content").get(0).path("user_id").asText());
    assertFalse(response.getResponse().getContentAsString().contains("passwordHash"));assertFalse(response.getResponse().getContentAsString().contains("password_hash"));
    assertEquals(403,doPost("/api/v1/accounts/"+admin.getId()+"/lock",a,Map.of()).getResponse().getStatus());
    assertEquals(409,doPost("/api/v1/accounts/"+member.getId()+"/reject",a,Map.of()).getResponse().getStatus());
    assertEquals(200,doPost("/api/v1/accounts/"+member.getId()+"/approve",a,Map.of()).getResponse().getStatus());
    assertEquals(403,putJson("/api/v1/accounts/"+member.getId()+"/password",m,Map.of("temporary_password","Temporary123!")).getResponse().getStatus());
    assertEquals(200,putJson("/api/v1/accounts/"+member.getId()+"/password",a,Map.of("temporary_password","Temporary123!")).getResponse().getStatus());
    assertTrue(users.findById(member.getId()).orElseThrow().isMustChangePassword());
    assertEquals(401,doGet("/api/v1/me/access",m).getResponse().getStatus());
    assertThrows(vn.smarttask.common.AppException.class,()->identity.login(member.getUsername(),"Password123!","test","test"));
    assertNotNull(identity.login(member.getUsername(),"Temporary123!","test","test"));
    var created=doPost("/api/v1/accounts",a,Map.of("username","created_"+tag(),"email","new_"+tag()+"@itg.io","display_name","Created user","temporary_password","Temporary123!"));
    assertEquals(200,created.getResponse().getStatus());assertTrue(json(created).path("data").path("must_change_password").asBoolean());
  }
  @Test void appointmentIsAtomicIdempotentPrivateAndCanBeChangedOrCancelled() throws Exception {
    var owner=activeUser("uxowner","Password123!");var other=activeUser("uxother","Password123!");
    String token=loginToken(owner.getUsername(),"Password123!"),outsider=loginToken(other.getUsername(),"Password123!");
    var input=Map.of("title","Private appointment","description","Bring the documents","due_at",OffsetDateTime.now().plusDays(2).toString(),"remind_at",OffsetDateTime.now().plusDays(1).toString(),"timezone","Asia/Bangkok");
    String key=idem();var headers=Map.of("Idempotency-Key",key);
    var first=doPost("/api/v1/reminders/personal",token,input,headers);assertEquals(200,first.getResponse().getStatus());
    String rule=json(first).path("data").path("id").asText(),task=json(first).path("data").path("task_id").asText();
    assertEquals(rule,json(doPost("/api/v1/reminders/personal",token,input,headers)).path("data").path("id").asText());
    assertEquals(1,jdbc.queryForObject("select count(*) from task where owner_user_id=? and title='Private appointment'",Integer.class,owner.getId()));
    assertEquals("Bring the documents",taskRepo.findById(UUID.fromString(task)).orElseThrow().getDescription());
    assertEquals(404,putJson("/api/v1/reminders/"+rule,outsider,Map.of("remind_at",OffsetDateTime.now().plusHours(4).toString())).getResponse().getStatus());
    assertEquals(200,putJson("/api/v1/reminders/"+rule,token,Map.of("remind_at",OffsetDateTime.now().plusHours(5).toString())).getResponse().getStatus());
    var own=json(doGet("/api/v1/reminders",token)).path("data");assertEquals("Private appointment",own.get(0).path("title").asText());
    assertEquals(0,json(doGet("/api/v1/reminders",outsider)).path("data").size());
    assertEquals(200,doPost("/api/v1/reminders/"+rule+"/cancel",token,Map.of()).getResponse().getStatus());
    assertEquals("CANCELLED",jdbc.queryForObject("select status from reminder_rule where id=?",String.class,UUID.fromString(rule)));
    assertEquals(409,putJson("/api/v1/reminders/"+rule,token,Map.of("remind_at",OffsetDateTime.now().plusHours(6).toString())).getResponse().getStatus());
    long count=taskRepo.count();
    var invalid=new java.util.HashMap<String,Object>(input);invalid.put("remind_at",OffsetDateTime.now().minusDays(1).toString());
    assertEquals(400,doPost("/api/v1/reminders/personal",token,invalid,Map.of("Idempotency-Key",idem())).getResponse().getStatus());assertEquals(count,taskRepo.count());
    var conflicting=new java.util.HashMap<String,Object>(input);conflicting.put("title","Different appointment");
    assertEquals(409,doPost("/api/v1/reminders/personal",token,conflicting,headers).getResponse().getStatus());
  }
}
