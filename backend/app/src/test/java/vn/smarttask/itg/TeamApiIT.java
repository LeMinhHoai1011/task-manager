package vn.smarttask.itg;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TeamApiIT extends ItgBase {
  @Test void teamManagerManagesOnlyTheirTeamAndCannotPromoteManagers() {
    var owner=activeUser("delegateowner","Password123!");var manager=activeUser("delegatemanager","Password123!");var member=activeUser("delegatemember","Password123!");
    var ws=mkWorkspace(owner.getId(),"delegate");addWsMember(ws,manager.getId(),"MEMBER");addWsMember(ws,member.getId(),"MEMBER");
    var team=teams.create(owner.getId(),ws,"Delegated");var other=teams.create(owner.getId(),ws,"Other team");
    teams.membership(owner.getId(),team.id(),manager.getId(),"MANAGER",false,1);
    teams.membership(manager.getId(),team.id(),member.getId(),"MEMBER",false,2);
    assertEquals(403,assertThrows(vn.smarttask.common.AppException.class,()->teams.membership(manager.getId(),team.id(),member.getId(),"MANAGER",false,3)).getHttpStatus().value());
    assertEquals(403,assertThrows(vn.smarttask.common.AppException.class,()->teams.membership(manager.getId(),other.id(),member.getId(),"MEMBER",false,1)).getHttpStatus().value());
  }
  @org.springframework.beans.factory.annotation.Autowired vn.smarttask.workspace.TeamService teams;
  @org.springframework.beans.factory.annotation.Autowired vn.smarttask.task.service.TaskResponsibilityService responsibility;
  @Test void primaryMustTransferBeforeLeavingTeamWithoutChangingProjectRole() throws Exception {
    var owner=activeUser("leaveowner","Password123!");var first=activeUser("leaveone","Password123!");var second=activeUser("leavetwo","Password123!");
    var ws=mkWorkspace(owner.getId(),"leave");addWsMember(ws,first.getId(),"MEMBER");addWsMember(ws,second.getId(),"MEMBER");
    var project=mkActiveProject(owner.getId(),ws,"leave");addProjectMember(owner.getId(),project.getId(),first.getId(),"MEMBER");addProjectMember(owner.getId(),project.getId(),second.getId(),"MEMBER");
    var team=teams.create(owner.getId(),ws,"Leave flow");teams.membership(owner.getId(),team.id(),first.getId(),"MEMBER",false,1);teams.membership(owner.getId(),team.id(),second.getId(),"MEMBER",false,2);
    var task=mkTask(owner.getId(),project.getId(),"Team task");var assigned=responsibility.transfer(owner.getId(),task.getId(),first.getId(),task.getVersion());
    teams.linkTask(owner.getId(),team.id(),task.getId(),assigned.version());
    var denied=assertThrows(vn.smarttask.common.AppException.class,()->teams.membership(owner.getId(),team.id(),first.getId(),null,true,3));
    assertEquals("STATE_CONFLICT",denied.getCode());
    assertEquals("PRIMARY_TRANSFER_REQUIRED",denied.getReasonCode());
    int version=taskRepo.findById(task.getId()).orElseThrow().getVersion();
    responsibility.transfer(owner.getId(),task.getId(),second.getId(),version);
    teams.membership(owner.getId(),team.id(),first.getId(),null,true,3);
    assertEquals("MEMBER",projectMembers.findMembership(project.getId(),first.getId()).orElseThrow().getRole());
    assertEquals("REMOVED",assignments.findByTaskIdAndUserId(task.getId(),first.getId()).orElseThrow().getStatus());
    String token=loginToken(first.getUsername(),"Password123!");
    assertEquals(404,doGet("/api/v1/tasks/"+task.getId(),token).getResponse().getStatus());
  }
  @Test void teamsEnforceWorkspaceManagementAndVersion() throws Exception {
    var owner = activeUser("teamowner", "Password123!");
    var member = activeUser("teammember", "Password123!");
    var stranger = activeUser("teamother", "Password123!");
    var ws = mkWorkspace(owner.getId(), "teams");
    addWsMember(ws, member.getId(), "MEMBER");
    String token = loginToken(owner.getUsername(), "Password123!");
    String mt = loginToken(member.getUsername(), "Password123!");
    String other = loginToken(stranger.getUsername(), "Password123!");
    String path = "/api/v1/workspaces/" + ws + "/teams";
    assertEquals(403, doPost(path, mt, Map.of("name", "No access")).getResponse().getStatus());
    assertEquals(404, doGet(path, other).getResponse().getStatus());
    var created = doPost(path, token, Map.of("name", "Engineering"));
    assertEquals(200, created.getResponse().getStatus(), created.getResponse().getContentAsString());
    String id = json(created).path("data").path("id").asText();
    String members = "/api/v1/teams/" + id + "/members";
    assertEquals(200, doPost(members, token, Map.of("userId", member.getId(), "role", "MEMBER"), Map.of("If-Match", "1")).getResponse().getStatus());
    assertEquals(409, doPost(members, token, Map.of("userId", member.getId(), "role", "MANAGER"), Map.of("If-Match", "1")).getResponse().getStatus());
    assertEquals(1, json(doGet(members, token)).path("data").size());
    var removed = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(members + "/" + member.getId()).header("Authorization", "Bearer " + token).header("If-Match", "2")).andReturn();
    assertEquals(200, removed.getResponse().getStatus());
    assertEquals(0, json(doGet(members, token)).path("data").size());
    assertEquals("MEMBER", wsMembers.findMembership(ws, member.getId()).orElseThrow().getRole());
  }
}
