package vn.smarttask.workspace;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import vn.smarttask.common.ApiResult;
import vn.smarttask.security.CurrentActor;
import vn.smarttask.workspace.service.WorkspaceService;
@RestController
@RequestMapping("/api/v1/me/invitations")
public class InvitationController {
  private final WorkspaceService workspaces;
  public InvitationController(WorkspaceService workspaces){this.workspaces=workspaces;}
  @GetMapping public ApiResult<?> list(@RequestParam(defaultValue="0") int page){return ApiResult.ok(workspaces.myInvitations(CurrentActor.userId(),page),"invitation.list.success");}
  @PostMapping("/{id}/accept") public ApiResult<?> accept(@PathVariable UUID id){return ApiResult.ok(Map.of("workspace_id",workspaces.acceptInvitation(CurrentActor.userId(),id)),"invitation.accept.success");}
}
