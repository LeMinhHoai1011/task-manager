package vn.smarttask.security;

import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import vn.smarttask.common.ApiResult;
import vn.smarttask.common.AppException;
import vn.smarttask.project.repo.*;
import vn.smarttask.workspace.repo.*;
import vn.smarttask.task.repo.*;

/** Self-scoped UI capabilities. Mutations still enforce their own live policy. */
@RestController
@RequestMapping("/api/v1")
@Transactional(readOnly=true)
public class AccessController {
  private final WorkspaceMembershipRepository wsMembers;
  private final WorkspaceRepository workspaces;
  private final ProjectMembershipRepository members;
  private final ProjectRepository projects;
  private final TaskRepository tasks;
  private final TaskAssignmentRepository assignments;
  private final GrantService grants;
  private final PolicyEnforcer policy;
  private final vn.smarttask.workflow.service.WorkflowService workflow;
  private final vn.smarttask.workflow.repo.ApprovalRoundRepository rounds;
  public AccessController(WorkspaceMembershipRepository wsMembers, WorkspaceRepository workspaces,
      ProjectMembershipRepository members, ProjectRepository projects, TaskRepository tasks,
      TaskAssignmentRepository assignments, GrantService grants, PolicyEnforcer policy,
      vn.smarttask.workflow.service.WorkflowService workflow, vn.smarttask.workflow.repo.ApprovalRoundRepository rounds) {
    this.wsMembers=wsMembers;this.workspaces=workspaces;this.members=members;this.projects=projects;
    this.tasks=tasks;this.assignments=assignments;this.grants=grants;this.policy=policy;
    this.workflow=workflow;this.rounds=rounds;
  }
  public record WorkspaceAccess(UUID id,String name,String role,String status,boolean canManage,boolean canCreateProject) {}
  public record ProjectAccess(UUID id,String name,String role,String status,boolean canManage,
      boolean canCreateTask,boolean canLogProgress,boolean metadata,boolean assignment) {}
  public record Access(boolean systemAdmin,List<WorkspaceAccess> workspaces,List<ProjectAccess> projects) {}
  public record TaskAccess(String role,boolean writable,boolean metadata,boolean assignment,
      boolean structure,boolean subtask,boolean execute,boolean comment,boolean review,
      List<String> workflowActions,boolean files) {}

  @GetMapping("/me/access")
  public ApiResult<Access> access() {
    UUID actor=CurrentActor.userId();
    List<WorkspaceAccess> ws=new ArrayList<>();
    for(var m:wsMembers.findByUserId(actor)) {
      if(!"ACTIVE".equals(m.getStatus()))continue;
      var w=workspaces.findById(m.getWorkspaceId()).orElse(null);
      if(w==null||"DELETED".equals(w.getStatus()))continue;
      boolean manages=managerWorkspace(m.getRole())&&"ACTIVE".equals(w.getStatus());
      ws.add(new WorkspaceAccess(w.getId(),w.getName(),m.getRole(),w.getStatus(),manages,manages));
    }
    List<ProjectAccess> ps=new ArrayList<>();
    for(var m:members.findByUserIdAndStatus(actor,"ACTIVE")) {
      var p=projects.findById(m.getProjectId()).orElse(null);
      if(p==null||"DELETED".equals(p.getStatus()))continue;
      var w=workspaces.findById(p.getWorkspaceId()).orElse(null);
      if(w==null||"DELETED".equals(w.getStatus()))continue;
      boolean writable=writable(w.getStatus(),p.getStatus());
      boolean manager=managerProject(m.getRole());
      boolean member="MEMBER".equals(m.getRole());
      ps.add(new ProjectAccess(p.getId(),p.getName(),m.getRole(),p.getStatus(),manager&&writable,
          manager&&writable,(manager||member)&&writable,
          writable&&(manager||(member&&grants.hasProjectPermission(actor,p.getId(),"TASK_METADATA_WRITE"))),
          writable&&(manager||(member&&grants.hasProjectPermission(actor,p.getId(),"TASK_ASSIGNMENT_WRITE")))));
    }
    return ApiResult.ok(new Access(grants.isSystemAdmin(actor),ws,ps),"access.success");
  }
  @GetMapping("/tasks/{id}/access")
  public ApiResult<TaskAccess> task(@PathVariable UUID id) {
    UUID actor=CurrentActor.userId();policy.requireTaskRead(actor,id);
    var t=tasks.findById(id).orElseThrow(()->AppException.notFound("Task not found"));
    var p=projects.findById(t.getProjectId()).orElseThrow(()->AppException.notFound("Task not found"));
    var w=workspaces.findById(p.getWorkspaceId()).orElseThrow(()->AppException.notFound("Task not found"));
    String role=members.findMembership(p.getId(),actor).map(m->m.getRole()).orElse("");
    boolean assigned=assignments.findByTaskIdAndUserId(id,actor).filter(a->"ACTIVE".equals(a.getStatus())).isPresent();
    var a=taskCapabilities(role,assigned,writable(w.getStatus(),p.getStatus()),
        "ACTIVE".equals(p.getStatus()),t.getParentTaskId()==null,
        grants.hasProjectPermission(actor,p.getId(),"TASK_METADATA_WRITE"),
        grants.hasProjectPermission(actor,p.getId(),"TASK_ASSIGNMENT_WRITE"));
    List<String> actions=new ArrayList<>();
    if(a.execute()&&"TODO".equals(t.getState()))actions.add("start");
    if(a.execute()&&"IN_PROGRESS".equals(t.getState()))actions.add("submit");
    if(a.review()) {
      if(Set.of("TODO","IN_PROGRESS","PENDING_APPROVAL").contains(t.getState()))actions.add("cancel");
      if("MANAGER".equals(role)&&Set.of("COMPLETED","CANCELLED").contains(t.getState()))actions.add("reopen");
      if("PENDING_APPROVAL".equals(t.getState())&&rounds.findFirstByTaskIdOrderByRoundNoDesc(id)
          .map(r->workflow.isEligibleApprover(r.getId(),actor)).orElse(false)) {
        actions.add("approve");actions.add("reject");
      }
    }
    boolean unlocked=!Set.of("PENDING_APPROVAL","COMPLETED","CANCELLED").contains(t.getState());
    return ApiResult.ok(new TaskAccess(a.role(),a.writable(),a.metadata()&&unlocked,a.assignment()&&unlocked,a.structure()&&unlocked,
        a.subtask()&&unlocked,a.execute()&&unlocked,a.comment(),a.review(),actions,a.files()&&unlocked),"access.success");
  }
  static TaskAccess taskCapabilities(String role,boolean assigned,boolean writable,boolean active,
      boolean root,boolean metadataGrant,boolean assignmentGrant) {
    boolean manager=managerProject(role),member="MEMBER".equals(role);
    boolean metadata=writable&&(manager||(member&&metadataGrant));
    return new TaskAccess(role,writable,metadata,writable&&(manager||(member&&assignmentGrant)),
        metadata,metadata&&root,writable&&active&&(manager||(member&&assigned)),
        writable&&(manager||(member&&assigned)),writable&&active&&manager,List.of(),writable&&manager);
  }
  static boolean managerWorkspace(String role){return Set.of("OWNER","ADMIN").contains(role);}
  static boolean managerProject(String role){return Set.of("OWNER","ADMIN","MANAGER").contains(role);}
  static boolean writable(String workspace,String project){return "ACTIVE".equals(workspace)&&Set.of("PLANNING","ACTIVE").contains(project);}
}
