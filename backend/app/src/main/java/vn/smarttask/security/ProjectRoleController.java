package vn.smarttask.security;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import vn.smarttask.common.*;
import vn.smarttask.project.repo.ProjectMembershipRepository;
import vn.smarttask.project.repo.ProjectRepository;

/** A custom role is a named bundle of project-scoped grants, not a membership role string. */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/members/{userId}/custom-role")
public class ProjectRoleController {
  private final GrantService grants;private final PolicyEnforcer policy;private final ProjectMembershipRepository members;private final ProjectRepository projects;
  public ProjectRoleController(GrantService grants,PolicyEnforcer policy,ProjectMembershipRepository members,ProjectRepository projects){this.grants=grants;this.policy=policy;this.members=members;this.projects=projects;}
  public record Role(String name,Set<String> permissions){}
  @GetMapping
  public ApiResult<?> get(@PathVariable UUID projectId,@PathVariable UUID userId) {
    policy.requireProjectWrite(CurrentActor.userId(),projectId);
    members.findMembership(projectId,userId).orElseThrow(()->AppException.notFound("Member not found"));
    var permissions=new HashSet<String>();String name="Member";
    for(String code:Set.of("TASK_METADATA_WRITE","TASK_ASSIGNMENT_WRITE"))for(var grant:grants.listLive(userId,code)) {
      if("PROJECT".equals(grant.getScopeType())&&projectId.equals(grant.getScopeId())) {
        permissions.add(code);
        if(grant.getReason()!=null&&grant.getReason().startsWith("CUSTOM_ROLE:"))name=grant.getReason().substring("CUSTOM_ROLE:".length());
      }
    }
    return ApiResult.ok(new Role(name,permissions),"project.custom_role.success");
  }
  @PutMapping
  @Transactional
  public ApiResult<?> set(@PathVariable UUID projectId,@PathVariable UUID userId,@RequestBody Role role) {
    UUID actor=CurrentActor.userId();policy.requireProjectWrite(actor,projectId);
    // Serialize replacements on the same project and prevent self escalation.
    projects.findByIdForUpdate(projectId).orElseThrow(()->AppException.notFound("Project not found"));
    if(actor.equals(userId))throw AppException.forbidden("Cannot assign your own custom role");
    var target=members.findMembership(projectId,userId).orElseThrow(()->AppException.notFound("Member not found"));
    if(!"MEMBER".equals(target.getRole()))throw AppException.forbidden("Custom roles require Member participation");
    if(role.name()==null||role.name().isBlank()||role.name().length()>100||role.permissions()==null||!Set.of("TASK_METADATA_WRITE","TASK_ASSIGNMENT_WRITE").containsAll(role.permissions()))throw AppException.badRequest("CUSTOM_ROLE_INVALID","Invalid role name or permission");
    grants.replaceProjectRole(actor,userId,projectId,role.name().trim(),role.permissions());
    return ApiResult.ok(role,"project.custom_role.success");
  }
}
