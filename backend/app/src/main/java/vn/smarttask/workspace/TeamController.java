package vn.smarttask.workspace;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import vn.smarttask.common.ApiResult;
import vn.smarttask.security.CurrentActor;
import vn.smarttask.task.TaskVersion;

@RestController
@RequestMapping("/api/v1")
public class TeamController {
  private final TeamService teams;
  public TeamController(TeamService teams) { this.teams = teams; }
  public record Create(String name) {}
  public record Membership(UUID userId, String role) {}
  public record Link(UUID teamId) {}
  @GetMapping("/teams/{id}/access")
  public ApiResult<?> access(@PathVariable UUID id) {
    return ApiResult.ok(teams.capabilities(CurrentActor.userId(),id),"teams.access.success");
  }
  @GetMapping("/workspaces/{id}/team-candidates")
  public ApiResult<?> candidates(@PathVariable UUID id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
    return ApiResult.ok(teams.candidates(CurrentActor.userId(),id,page,size),"teams.candidates");
  }
  @PutMapping("/tasks/{id}/team")
  public ApiResult<?> link(@PathVariable UUID id,@RequestBody Link body,@RequestHeader("If-Match") TaskVersion version) {
    if(body.teamId()==null)throw vn.smarttask.common.AppException.badRequest("TEAM_REQUIRED","Team required");
    return ApiResult.ok(teams.linkTask(CurrentActor.userId(),body.teamId(),id,version.value()),"teams.task.linked");
  }
  @GetMapping("/teams/{id}/members/{userId}/tasks")
  public ApiResult<?> assigned(@PathVariable UUID id,@PathVariable UUID userId) {
    return ApiResult.ok(teams.assignedTasks(CurrentActor.userId(),id,userId),"teams.member.tasks");
  }
  @GetMapping("/workspaces/{id}/teams")
  public ApiResult<?> list(@PathVariable UUID id, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
    return ApiResult.ok(teams.list(CurrentActor.userId(), id, page, size), "teams.list.success");
  }
  @PostMapping("/workspaces/{id}/teams")
  public ApiResult<?> create(@PathVariable UUID id, @RequestBody Create body) {
    return ApiResult.ok(teams.create(CurrentActor.userId(), id, body.name()), "teams.create.success");
  }
  @GetMapping("/teams/{id}/members")
  public ApiResult<?> members(@PathVariable UUID id, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
    return ApiResult.ok(teams.members(CurrentActor.userId(), id, page, size), "teams.members.success");
  }
  @PostMapping("/teams/{id}/members")
  public ApiResult<?> set(@PathVariable UUID id, @RequestBody Membership body, @RequestHeader("If-Match") TaskVersion version) {
    if (body.userId() == null) throw vn.smarttask.common.AppException.badRequest("USER_REQUIRED", "User required");
    return ApiResult.ok(teams.membership(CurrentActor.userId(), id, body.userId(), body.role(), false, version.value()), "teams.member.success");
  }
  @DeleteMapping("/teams/{id}/members/{userId}")
  public ApiResult<?> remove(@PathVariable UUID id, @PathVariable UUID userId, @RequestHeader("If-Match") TaskVersion version) {
    return ApiResult.ok(teams.membership(CurrentActor.userId(), id, userId, null, true, version.value()), "teams.remove.success");
  }
}
