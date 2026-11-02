package vn.smarttask.itg;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.http.MediaType;
import static org.junit.jupiter.api.Assertions.*;

class CustomRoleIT extends ItgBase {
  private int role(UUID project,UUID target,String token,String name,Set<String> permissions)throws Exception {
    return mvc.perform(MockMvcRequestBuilders.put("/api/v1/projects/"+project+"/members/"+target+"/custom-role").header("Authorization","Bearer "+token).header("X-Forwarded-For",fwd()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(Map.of("name",name,"permissions",permissions)))).andReturn().getResponse().getStatus();
  }
  @Test void grantsAreScopedRevocableAndCannotDelegateApprovalOrRoleAdministration()throws Exception {
    var manager=activeUser("roleowner","Password123!");var member=activeUser("roleuser","Password123!");var guest=activeUser("roleguest","Password123!");
    var ws=mkWorkspace(manager.getId(),"roles");var p=mkActiveProject(manager.getId(),ws,"roles");var other=mkActiveProject(manager.getId(),ws,"otherroles");
    addProjectMember(manager.getId(),p.getId(),member.getId(),"MEMBER");addProjectMember(manager.getId(),p.getId(),guest.getId(),"GUEST");addProjectMember(manager.getId(),other.getId(),member.getId(),"MEMBER");
    var task=mkTask(manager.getId(),p.getId(),"Managed");var privateTask=mkTask(manager.getId(),other.getId(),"Not delegated");
    String ownerToken=loginToken(manager.getUsername(),"Password123!"),token=loginToken(member.getUsername(),"Password123!");
    assertEquals(404,doGet("/api/v1/tasks/"+task.getId(),token).getResponse().getStatus());
    assertEquals(200,role(p.getId(),member.getId(),ownerToken,"Coordinator",Set.of("TASK_METADATA_WRITE","TASK_ASSIGNMENT_WRITE")));
    assertEquals(200,doGet("/api/v1/tasks/"+task.getId(),token).getResponse().getStatus());
    assertEquals(404,doGet("/api/v1/tasks/"+privateTask.getId(),token).getResponse().getStatus());
    assertEquals(200,role(p.getId(),member.getId(),ownerToken,"Editor",Set.of("TASK_METADATA_WRITE")));
    var edited=doPatch("/api/v1/tasks/"+task.getId(),token,Map.of("title","Delegated metadata"),Map.of("If-Match","1","Idempotency-Key",idem()));assertEquals(200,edited.getResponse().getStatus());
    long version=json(edited).path("data").path("version").asLong();
    assertEquals(403,doPost("/api/v1/tasks/"+task.getId()+"/primary-assignee",token,Map.of("userId",manager.getId()),Map.of("If-Match",String.valueOf(version))).getResponse().getStatus());
    assertEquals(200,role(p.getId(),member.getId(),ownerToken,"Dispatcher",Set.of("TASK_ASSIGNMENT_WRITE")));
    assertEquals(403,doPatch("/api/v1/tasks/"+task.getId(),token,Map.of("title","Forbidden metadata"),Map.of("If-Match",String.valueOf(version),"Idempotency-Key",idem())).getResponse().getStatus());
    assertEquals(200,doPost("/api/v1/tasks/"+task.getId()+"/primary-assignee",token,Map.of("userId",manager.getId()),Map.of("If-Match",String.valueOf(version))).getResponse().getStatus());
    assertEquals(403,role(p.getId(),guest.getId(),ownerToken,"Guest escalation",Set.of("TASK_METADATA_WRITE")));
    assertEquals(403,role(p.getId(),guest.getId(),token,"Escalation",Set.of("TASK_METADATA_WRITE")));
    assertEquals(403,doPost("/api/v1/tasks/"+task.getId()+"/workflow/approve",token,Map.of(),Map.of("If-Match","1","Idempotency-Key",idem())).getResponse().getStatus());
    assertEquals(200,role(p.getId(),member.getId(),ownerToken,"Member",Set.of()));
    assertEquals(404,doGet("/api/v1/tasks/"+task.getId(),token).getResponse().getStatus());
  }
}
