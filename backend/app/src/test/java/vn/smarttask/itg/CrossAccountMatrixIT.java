package vn.smarttask.itg;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Visible-resource write denials and anti-enumeration across all core scopes. */
class CrossAccountMatrixIT extends ItgBase {
  @Test void outsiderCannotReadResourcesAndGuestCannotMutateThem() throws Exception {
    var owner=activeUser("matrixowner","Password123!");var guest=activeUser("matrixguest","Password123!");var outsider=activeUser("matrixoutside","Password123!");
    var ws=mkWorkspace(owner.getId(),"matrix");addWsMember(ws,guest.getId(),"GUEST");
    var p=mkActiveProject(owner.getId(),ws,"matrix");addProjectMember(owner.getId(),p.getId(),guest.getId(),"GUEST");
    var visible=mkTask(owner.getId(),p.getId(),"Shared task");var hidden=mkTask(owner.getId(),p.getId(),"Hidden task");
    tasks.assign(owner.getId(),visible.getId(),guest.getId(),visible.getVersion());
    String o=loginToken(owner.getUsername(),"Password123!"),g=loginToken(guest.getUsername(),"Password123!"),x=loginToken(outsider.getUsername(),"Password123!");
    var note=doPost("/api/v1/notes",o,Map.of("scope","PROJECT","projectId",p.getId(),"title","Shared note","content","Test"));
    assertEquals(200,note.getResponse().getStatus());String nid=json(note).path("data").path("id").asText();
    for(String path:List.of("/workspaces/"+ws,"/projects/"+p.getId(),"/tasks/"+visible.getId(),"/notes/"+nid,"/files/tasks/"+visible.getId()+"/attachments","/reports/dashboard?projectId="+p.getId()))
      assertEquals(404,doGet("/api/v1"+path,x).getResponse().getStatus(),path);
    assertEquals(404,doGet("/api/v1/tasks/"+hidden.getId(),g).getResponse().getStatus());
    assertEquals(200,doGet("/api/v1/tasks/"+visible.getId(),g).getResponse().getStatus());
    assertEquals(0,json(doGet("/api/v1/dashboard?scope=project&projectId="+p.getId(),x)).path("data").path("total").asInt());
    assertEquals(1,json(doGet("/api/v1/dashboard?scope=project&projectId="+p.getId(),g)).path("data").path("total").asInt());
    assertEquals(403,doPost("/api/v1/projects",g,Map.of("workspace_id",ws,"name","Denied")).getResponse().getStatus());
    assertEquals(403,doPost("/api/v1/workspaces/"+ws+"/invites",g,Map.of("email","nobody@test.invalid","role","ADMIN")).getResponse().getStatus());
    assertEquals(403,doPatch("/api/v1/tasks/"+visible.getId(),g,Map.of("title","Denied"),Map.of("If-Match","2","Idempotency-Key",idem())).getResponse().getStatus());
    assertEquals(403,doPost("/api/v1/files/upload",g,Map.of("task_id",visible.getId(),"file_name","x.txt","mime_type","text/plain","declared_size",1),Map.of("Idempotency-Key",idem())).getResponse().getStatus());
    assertEquals(403,doPost("/api/v1/tasks/"+visible.getId()+"/subtasks",g,Map.of("title","Denied"),Map.of("Idempotency-Key",idem())).getResponse().getStatus());
    assertEquals(403,doPost("/api/v1/auth/approve",g,Map.of("target_id",outsider.getId(),"approve",true)).getResponse().getStatus());
  }
}
