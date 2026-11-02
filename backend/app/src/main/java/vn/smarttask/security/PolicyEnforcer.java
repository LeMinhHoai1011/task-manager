package vn.smarttask.security;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import vn.smarttask.common.AppException;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.project.repo.ProjectMembershipRepository;
import vn.smarttask.project.repo.ProjectRepository;
import vn.smarttask.task.repo.TaskAssignmentRepository;
import vn.smarttask.task.repo.TaskRepository;
import vn.smarttask.workspace.repo.WorkspaceMembershipRepository;
import vn.smarttask.workspace.repo.WorkspaceRepository;

/**
 * F0 controller-entry authorization. Builds participation + state from the
 * real repositories and funnels every check through
 * {@link AuthorizationPolicy#decide} / {@link AuthorizationPolicy#decideWithState}.
 *
 * <p>Mapping (anti-oracle rule): missing READ on a resource the actor must not
 * know exists -&gt; 404 RESOURCE_NOT_FOUND (+ {@code DENIED_NOT_FOUND} audit);
 * write denied on a visible resource -&gt; 403 FORBIDDEN
 * (+ {@code DENIED_FORBIDDEN} audit). Business services are untouched; they
 * remain the second line of defence.
 */
@Service
public class PolicyEnforcer {
  @org.springframework.beans.factory.annotation.Autowired(required=false) private GrantService grants;
  private boolean delegated(UUID actor,UUID project,String permission) {
    return grants!=null&&projectMembers.findMembership(project,actor).filter(m->"MEMBER".equals(m.getRole())).isPresent()&&grants.hasProjectPermission(actor,project,permission);
  }

  private final AuthorizationPolicy policy;
  private final WorkspaceRepository workspaces;
  private final WorkspaceMembershipRepository wsMembers;
  private final ProjectRepository projects;
  private final ProjectMembershipRepository projectMembers;
  private final TaskRepository tasks;
  private final TaskAssignmentRepository assignments;
  private final AuditService audit;

  public PolicyEnforcer(AuthorizationPolicy policy, WorkspaceRepository workspaces,
      WorkspaceMembershipRepository wsMembers, ProjectRepository projects,
      ProjectMembershipRepository projectMembers, TaskRepository tasks,
      TaskAssignmentRepository assignments, AuditService audit) {
    this.policy = policy;
    this.workspaces = workspaces;
    this.wsMembers = wsMembers;
    this.projects = projects;
    this.projectMembers = projectMembers;
    this.tasks = tasks;
    this.assignments = assignments;
    this.audit = audit;
  }

  // ---------------------------------------------------------------- workspace

  public void requireWorkspaceRead(UUID actor, UUID workspaceId) {
    var ws = workspaces.findById(workspaceId)
        .orElseThrow(() -> AppException.notFound("Workspace not found"));
    // DELETED hides existence entirely (matrix: deny all incl. read, except
    // restore paths — none exist at entry level).
    if ("DELETED".equals(ws.getStatus())) {
      audit.appendDenied(actor, "workspace.read", "workspace", workspaceId, "DENIED_NOT_FOUND");
      throw AppException.notFound("Workspace not found");
    }
    boolean participates = wsMembers.findMembership(workspaceId, actor).isPresent();
    decide(actor, workspaceId, "workspace", "workspace.read", "workspace:read",
        participates, participates, participates,
        new AuthorizationPolicy.EffectiveState(ws.getStatus(), null));
  }

  public void requireWorkspaceWrite(UUID actor, UUID workspaceId) {
    var ws = workspaces.findById(workspaceId)
        .orElseThrow(() -> AppException.notFound("Workspace not found"));
    if ("DELETED".equals(ws.getStatus())) {
      audit.appendDenied(actor, "workspace.write", "workspace", workspaceId, "DENIED_NOT_FOUND");
      throw AppException.notFound("Workspace not found");
    }
    var m = wsMembers.findMembership(workspaceId, actor);
    boolean hasRead = m.isPresent();
    boolean canWrite = m.map(x -> isWorkspaceWriter(x.getRole())).orElse(false);
    decide(actor, workspaceId, "workspace", "workspace.write", "workspace:write",
        hasRead, canWrite, canWrite,
        new AuthorizationPolicy.EffectiveState(ws.getStatus(), null));
  }

  // ---------------------------------------------------------------- project
  public void requireWorkspaceManagement(UUID actor, UUID workspaceId) {
    requireWorkspaceRead(actor, workspaceId);
    var ws = workspaces.findById(workspaceId).orElseThrow(() -> AppException.notFound("Workspace not found"));
    boolean manages = wsMembers.findMembership(workspaceId, actor)
        .map(m -> "OWNER".equals(m.getRole()) || "ADMIN".equals(m.getRole())).orElse(false);
    decide(actor, workspaceId, "workspace", "workspace.team.write", "workspace:write",
        true, manages, manages, new AuthorizationPolicy.EffectiveState(ws.getStatus(), null));
  }

  public void requireProjectRead(UUID actor, UUID projectId) {
    var p = projects.findById(projectId)
        .orElseThrow(() -> AppException.notFound("Project not found"));
    if ("DELETED".equals(p.getStatus())) {
      audit.appendDenied(actor, "project.read", "project", projectId, "DENIED_NOT_FOUND");
      throw AppException.notFound("Project not found");
    }
    boolean participates = projectMembers.findMembership(projectId, actor).isPresent();
    decide(actor, projectId, "project", "project.read", "project:read",
        participates, participates, participates, stateOf(p));
  }

  public void requireProjectWrite(UUID actor, UUID projectId) {
    var p = projects.findById(projectId)
        .orElseThrow(() -> AppException.notFound("Project not found"));
    if ("DELETED".equals(p.getStatus())) {
      audit.appendDenied(actor, "project.write", "project", projectId, "DENIED_NOT_FOUND");
      throw AppException.notFound("Project not found");
    }
    var m = projectMembers.findMembership(projectId, actor);
    boolean hasRead = m.isPresent();
    boolean canWrite = m.map(x -> isProjectWriter(x.getRole())).orElse(false);
    decide(actor, projectId, "project", "project.write", "project:write",
        hasRead, canWrite, canWrite, stateOf(p));
  }

  // ---------------------------------------------------------------- task

  public void requireTaskRead(UUID actor, UUID taskId) {
    var t = tasks.findById(taskId)
        .orElseThrow(() -> AppException.notFound("Task not found"));
    if(t.getDeletedAt()!=null)throw AppException.notFound("Task not found");
    var scope = taskScope(actor, t.getProjectId(), taskId);
    decide(actor, taskId, "task", "task.read", "task:read",
        scope.hasRead(), scope.hasRead(), scope.hasRead(), scope.state());
  }

  public void requireTaskWrite(UUID actor, UUID taskId) {
    requireTaskWrite(actor, taskId, "task.write", "task:write");
  }

  /** Execution rights do not grant task metadata or assignment management. */
  public void requireTaskExecution(UUID actor, UUID taskId, String action) {
    var task = tasks.findById(taskId)
        .orElseThrow(() -> AppException.notFound("Task not found"));
    if(task.getProjectId()==null)throw AppException.notFound("Task not found");
    var project = projects.findById(task.getProjectId())
        .orElseThrow(() -> AppException.notFound("Task not found"));
    var member = projectMembers.findMembership(task.getProjectId(), actor)
        .filter(m -> "ACTIVE".equals(m.getStatus()));
    boolean manager = member.map(m -> isProjectWriter(m.getRole())).orElse(false);
    boolean assigned = assignments.findByTaskIdAndUserId(taskId, actor)
        .filter(a -> "ACTIVE".equals(a.getStatus())).isPresent();
    boolean executor = member.map(m -> "MEMBER".equals(m.getRole())).orElse(false) && assigned;
    boolean visible = manager || (member.isPresent() && assigned);
    decide(actor, taskId, "task", action, "task:write", visible,
        manager || executor, manager || executor, stateOf(project));
  }

  /** Write-like verbs (workflow transitions, approvals) share task write scope. */
  public void requireTaskWrite(UUID actor, UUID taskId, String action, String permission) {
    var t = tasks.findById(taskId)
        .orElseThrow(() -> AppException.notFound("Task not found"));
    var scope = taskScope(actor, t.getProjectId(), taskId);
    boolean allowed=scope.canWrite() || ("task.write".equals(action)&&delegated(actor,t.getProjectId(),"TASK_METADATA_WRITE"));
    decide(actor, taskId, "task", action, permission,
        scope.hasRead(), allowed, allowed, scope.state());
  }

  /**
   * MANAGER-level task write: requires the actor to be a project MANAGER/OWNER/ADMIN.
   * Used for assign/unassign (changing task assignment) and other management-only operations.
   */
  public void requireTaskManagerWrite(UUID actor, UUID taskId, String action, String permission) {
    var t = tasks.findById(taskId)
        .orElseThrow(() -> AppException.notFound("Task not found"));
    if(t.getProjectId()==null)throw AppException.notFound("Task not found");
    var p = projects.findById(t.getProjectId())
        .orElseThrow(() -> AppException.notFound("Task not found"));
    if ("DELETED".equals(p.getStatus())) {
      audit.appendDenied(actor, action, "task", taskId, "DENIED_NOT_FOUND");
      throw AppException.notFound("Task not found");
    }
    var m = projectMembers.findMembership(t.getProjectId(), actor);
    String delegatedPermission=switch(action){case "task.assign","task.unassign" -> "TASK_ASSIGNMENT_WRITE";case "task.update","task.deadline.set" -> "TASK_METADATA_WRITE";default -> "";};
    boolean isWriter = m.map(x -> isProjectWriter(x.getRole())).orElse(false) || (!delegatedPermission.isEmpty()&&delegated(actor,t.getProjectId(),delegatedPermission));
    // hasRead: manager always has read. non-manager without membership: 404.
    boolean hasRead = taskScope(actor,t.getProjectId(),taskId).hasRead();
    decide(actor, taskId, "task", action, permission,
        hasRead, isWriter, isWriter, stateOf(p));
  }

  // ---------------------------------------------------------------- internals

  private record Scope(boolean hasRead, boolean canWrite,
      AuthorizationPolicy.EffectiveState state) {}

  private Scope taskScope(UUID actor, UUID projectId, UUID taskId) {
    // Personal tasks are served exclusively by the owner-scoped /me/tasks API.
    if (projectId == null) throw AppException.notFound("Task not found");
    var p = projects.findById(projectId)
        .orElseThrow(() -> AppException.notFound("Task not found"));
    if ("DELETED".equals(p.getStatus())) {
      audit.appendDenied(actor, "task.scope", "task", taskId, "DENIED_NOT_FOUND");
      throw AppException.notFound("Task not found");
    }
    var m = projectMembers.findMembership(projectId, actor);
    boolean assigned = assignments.findByTaskIdAndUserId(taskId, actor)
        .filter(a -> "ACTIVE".equals(a.getStatus())).isPresent();
    boolean isWriter = m.map(x -> isProjectWriter(x.getRole())).orElse(false);
    // MANAGER/OWNER/ADMIN: doc + ghi tat ca task.
    // MEMBER (duoc giao): chi DOC task, khong duoc ghi (khong doi assignee/deadline/title).
    // GUEST: chi doc task duoc giao, khong duoc ghi.
    boolean hasRead = m.isPresent() && (isWriter || assigned || delegated(actor,projectId,"TASK_METADATA_WRITE") || delegated(actor,projectId,"TASK_ASSIGNMENT_WRITE"));
    // canWrite chi cho project writer (MANAGER/OWNER/ADMIN); MEMBER/GUEST khong duoc write bat ke co assigned hay khong.
    boolean canWrite = isWriter;
    return new Scope(hasRead, canWrite, stateOf(p));
  }

  private AuthorizationPolicy.EffectiveState stateOf(vn.smarttask.project.entity.Project p) {
    String wsStatus = workspaces.findById(p.getWorkspaceId())
        .map(w -> w.getStatus()).orElse(null);
    return new AuthorizationPolicy.EffectiveState(wsStatus, p.getStatus());
  }

  private void decide(UUID actor, UUID resourceId, String entityType, String action,
      String permission, boolean hasRead, boolean participates, boolean writeFlag,
      AuthorizationPolicy.EffectiveState state) {
    var authActor = new AuthorizationPolicy.Actor(actor, true, false);
    var resource = new AuthorizationPolicy.Resource(resourceId, true, participates, hasRead);
    // Reads ignore the P7 state guard (mutations only); writes enforce it.
    // Note: AuthorizationPolicy keys its write check off the ":write" suffix,
    // so non-suffixed verbs (e.g. "task:approve") are normalised to write-like.
    String writePerm = permission.endsWith(":write") ? permission : (permission + ":write");
    AuthorizationPolicy.Decision d = permission.endsWith(":read")
        ? policy.decide(authActor, resource, permission)
        : policy.decideWithState(authActor, resource, writePerm, state, action);
    if (d == AuthorizationPolicy.Decision.ALLOW) {
      return;
    }
    if (d == AuthorizationPolicy.Decision.NOT_FOUND) {
      audit.appendDenied(actor, action, entityType, resourceId, "DENIED_NOT_FOUND");
      throw AppException.notFound("Resource not found");
    }
    audit.appendDenied(actor, action, entityType, resourceId, "DENIED_FORBIDDEN");
    throw AppException.forbidden("Forbidden");
  }

  private static boolean isWorkspaceWriter(String role) {
    return "OWNER".equals(role) || "ADMIN".equals(role) || "MEMBER".equals(role);
  }

  /** Quản lý Project: co the doc/ghi tat ca task. MEMBER/GUEST khong duoc coi la writer. */
  private static boolean isProjectWriter(String role) {
    return "OWNER".equals(role) || "ADMIN".equals(role) || "MANAGER".equals(role);
  }

  /** Participation helper for tests/consumers: project member ids + assignees. */
  public Set<UUID> participantIds(UUID projectId, UUID taskId) {
    Set<UUID> ids = new HashSet<>();
    projectMembers.findByProjectId(projectId).forEach(m -> ids.add(m.getUserId()));
    if (taskId != null) {
      assignments.findByTaskId(taskId).forEach(a -> ids.add(a.getUserId()));
    }
    return ids;
  }
}
