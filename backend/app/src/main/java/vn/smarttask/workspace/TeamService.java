package vn.smarttask.workspace;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.smarttask.common.AppException;
import vn.smarttask.common.UuidV7;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.security.PolicyEnforcer;
import vn.smarttask.workspace.repo.WorkspaceMembershipRepository;
import vn.smarttask.identity.repo.AppUserRepository;

@Service
public class TeamService {
  @org.springframework.beans.factory.annotation.Autowired(required=false) private vn.smarttask.common.service.OutboxService outbox;
  public record Team(UUID id, UUID workspaceId, String name, long version) {}
  public record Member(UUID userId, String displayName, String role) {}
  private final JdbcTemplate jdbc;
  private final PolicyEnforcer policy;
  private final WorkspaceMembershipRepository memberships;
  private final AppUserRepository users;
  private final AuditService audit;
  public TeamService(JdbcTemplate jdbc, PolicyEnforcer policy, WorkspaceMembershipRepository memberships,
      AppUserRepository users, AuditService audit) {
    this.jdbc = jdbc; this.policy = policy; this.memberships = memberships; this.users = users; this.audit = audit;
  }
  private Team team(UUID actor, UUID id, boolean write) {
    var list = jdbc.query("select id,workspace_id,name,version from workspace_team where id=?" + (write ? " for update" : ""),
        (r, n) -> new Team(r.getObject(1, UUID.class), r.getObject(2, UUID.class), r.getString(3), r.getLong(4)), id);
    if (list.isEmpty()) throw AppException.notFound("Team not found");
    Team team = list.get(0);
    if (write) {
      var membership=memberships.findMembership(team.workspaceId(),actor)
          .orElseThrow(()->AppException.notFound("Team not found"));
      boolean workspaceManager=java.util.Set.of("OWNER","ADMIN").contains(membership.getRole());
      boolean teamManager=Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from team_member where team_id=? and user_id=? and role='MANAGER' and status='ACTIVE')",Boolean.class,id,actor));
      if(workspaceManager)policy.requireWorkspaceManagement(actor,team.workspaceId());
      else {
        policy.requireWorkspaceWrite(actor,team.workspaceId());
        if(!teamManager || !"MEMBER".equals(membership.getRole()))throw AppException.forbidden("Team manager required");
      }
    }
    else policy.requireWorkspaceRead(actor, team.workspaceId());
    return team;
  }
  @Transactional(readOnly = true)
  public java.util.List<Member> candidates(UUID actor,UUID workspace,int page,int size) {
    policy.requireWorkspaceRead(actor,workspace);
    int limit=Math.max(1,Math.min(size,100));
    return jdbc.query("select w.user_id,u.display_name,w.role from workspace_membership w join app_user u on u.id=w.user_id where w.workspace_id=? and w.status='ACTIVE' and u.status='ACTIVE' order by u.display_name,w.user_id limit ? offset ?",(r,n)->new Member(r.getObject(1,UUID.class),r.getString(2),r.getString(3)),workspace,limit,(long)Math.max(page,0)*limit);
  }
  @Transactional(readOnly = true)
  public java.util.List<Team> list(UUID actor, UUID workspace, int page, int size) {
    policy.requireWorkspaceRead(actor, workspace);
    int limit = Math.max(1, Math.min(size, 100));
    return jdbc.query("select id,workspace_id,name,version from workspace_team where workspace_id=? order by created_at,id limit ? offset ?",
        (r, n) -> new Team(r.getObject(1, UUID.class), r.getObject(2, UUID.class), r.getString(3), r.getLong(4)), workspace, limit, (long)Math.max(0, page) * limit);
  }
  @Transactional
  public Team create(UUID actor, UUID workspace, String name) {
    policy.requireWorkspaceManagement(actor, workspace);
    if (name == null || name.isBlank() || name.trim().length() > 200)
      throw AppException.badRequest("TEAM_NAME_INVALID", "Team name must contain 1–200 characters");
    UUID id = UuidV7.generate();
    try { jdbc.update("insert into workspace_team(id,workspace_id,name,created_by) values (?,?,?,?)", id, workspace, name.trim(), actor); }
    catch (org.springframework.dao.DuplicateKeyException e) { throw AppException.stateConflict("TEAM_EXISTS", "Team name already exists"); }
    audit.append(actor, "team.create", "team", id, "ALLOWED", null, "{}");
    return new Team(id, workspace, name.trim(), 1);
  }
  @Transactional(readOnly = true)
  public java.util.List<Member> members(UUID actor, UUID id, int page, int size) {
    Team team = team(actor, id, false);
    int limit = Math.max(1, Math.min(size, 100));
    return jdbc.query("select m.user_id,u.display_name,m.role from team_member m join app_user u on u.id=m.user_id join workspace_membership w on w.user_id=m.user_id and w.workspace_id=? and w.status='ACTIVE' where m.team_id=? and m.status='ACTIVE' order by m.joined_at,m.user_id limit ? offset ?",
        (r, n) -> new Member(r.getObject(1, UUID.class), r.getString(2), r.getString(3)), team.workspaceId(), id, limit, (long)Math.max(0, page)*limit);
  }
  @Transactional
  public Team membership(UUID actor, UUID id, UUID target, String role, boolean remove, long expected) {
    Team team = team(actor, id, true);
    if (team.version() != expected) throw AppException.versionConflict("Stale team version", team.version());
    var actorMembership=memberships.findMembership(team.workspaceId(),actor).orElseThrow();
    boolean workspaceManager=java.util.Set.of("OWNER","ADMIN").contains(actorMembership.getRole());
    if(!workspaceManager) {
      boolean managesTarget=Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from team_member where team_id=? and user_id=? and role='MANAGER' and status='ACTIVE')",Boolean.class,id,target));
      if("MANAGER".equals(role)||managesTarget)throw AppException.forbidden("Only workspace managers may change team managers");
    }
    // Lock linked tasks before checking primary responsibilities, including downgrades.
    jdbc.query("select t.id from task t join task_team tt on tt.task_id=t.id where tt.team_id=? order by t.id for update of t",(r,n)->r.getObject(1,UUID.class),id);
    if (!remove && "GUEST".equals(role)) {
      Long primary=jdbc.queryForObject("select count(*) from task_assignment a join task_team tt on tt.task_id=a.task_id join task t on t.id=a.task_id where tt.team_id=? and a.user_id=? and a.status='ACTIVE' and a.is_primary and t.deleted_at is null and t.state not in ('COMPLETED','CANCELLED')",Long.class,id,target);
      if(primary!=null && primary>0)throw AppException.stateConflict("PRIMARY_TRANSFER_REQUIRED","Transfer primary tasks before downgrading to Guest");
    }
    if (!remove) {
      if (role == null || !java.util.Set.of("MANAGER", "MEMBER", "GUEST").contains(role))
        throw AppException.badRequest("TEAM_ROLE_INVALID", "Invalid team role");
      var member = memberships.findMembership(team.workspaceId(), target)
          .orElseThrow(() -> AppException.stateConflict("NOT_WORKSPACE_MEMBER", "Target must be an active workspace member"));
      if (("GUEST".equals(member.getRole()) || "VIEWER".equals(member.getRole())) && !"GUEST".equals(role))
        throw AppException.forbidden("Workspace guest cannot gain team execution or management rights");
      users.findById(target).filter(u -> u.isActive() && "ACTIVE".equals(u.getStatus()))
          .orElseThrow(() -> AppException.stateConflict("TARGET_NOT_ACTIVE", "Target must be active"));
      jdbc.update("insert into team_member(team_id,user_id,role) values (?,?,?) on conflict(team_id,user_id) do update set role=excluded.role,status='ACTIVE'", id, target, role);
      if(outbox!=null)outbox.emit("team",id,"notification.team.membership","{\"user_id\":\""+target+"\",\"type\":\"TEAM_MEMBER_ADDED\",\"scope_type\":\"TEAM\",\"scope_id\":\""+id+"\",\"title_key\":\"notification.team.membership\",\"deep_link\":\"/teams\",\"dedup_token\":\"team:"+id+":"+target+":"+team.version()+"\"}");
    } else {
      var taskIds=jdbc.query("select t.id from task t join task_team tt on tt.task_id=t.id where tt.team_id=? order by t.id for update of t",(r,n)->r.getObject(1,UUID.class),id);
      Long primaryCount=jdbc.queryForObject("select count(*) from task_assignment a join task_team tt on tt.task_id=a.task_id join task t on t.id=a.task_id where tt.team_id=? and a.user_id=? and a.status='ACTIVE' and a.is_primary and t.deleted_at is null and t.state not in ('COMPLETED','CANCELLED')",Long.class,id,target);
      if(primaryCount!=null && primaryCount>0) throw AppException.stateConflict("PRIMARY_TRANSFER_REQUIRED","Transfer unfinished primary tasks before removing team member");
      if(jdbc.update("update team_member set status='REMOVED' where team_id=? and user_id=? and status='ACTIVE'",id,target)==0)
        throw AppException.notFound("Team member not found");
      for(UUID taskId:taskIds) {
        int changed=jdbc.update("update task_assignment set status='REMOVED',is_primary=false,removal_reason='TEAM_MEMBERSHIP_REMOVED' where task_id=? and user_id=? and status='ACTIVE'",taskId,target);
        if(changed>0) {
          jdbc.update("update task set version=version+1,updated_at=now() where id=?",taskId);
          audit.append(actor,"task.team.member.remove","task",taskId,"ALLOWED",null,"{\"user_id\":\""+target+"\"}");
        }
      }
    }
    jdbc.update("update workspace_team set version=version+1 where id=?", id);
    audit.append(actor, remove ? "team.member.remove" : "team.member.set", "team", id, "ALLOWED", null, "{}");
    return new Team(id, team.workspaceId(), team.name(), team.version()+1);
  }
  @Transactional
  public java.util.Map<String,Object> linkTask(UUID actor,UUID teamId,UUID taskId,long expected) {
    Team team=team(actor,teamId,true);
    var rows=jdbc.queryForList("select t.version,p.workspace_id,t.state,t.deleted_at from task t join project p on p.id=t.project_id where t.id=? for update of t",taskId);
    if(rows.isEmpty()) throw AppException.notFound("Project task not found");
    policy.requireTaskManagerWrite(actor,taskId,"task.assign","task:write");
    var row=rows.get(0);
    if(((Number)row.get("version")).longValue()!=expected)throw AppException.versionConflict("Stale task version",((Number)row.get("version")).longValue());
    if(!team.workspaceId().equals(row.get("workspace_id")))throw AppException.stateConflict("TEAM_SCOPE_MISMATCH","Team and Project must share Workspace");
    if(row.get("deleted_at")!=null || java.util.Set.of("PENDING_APPROVAL","COMPLETED","CANCELLED").contains(row.get("state")))throw AppException.stateConflict("EXECUTION_LOCKED","Task cannot change team in this state");
    Long invalid=jdbc.queryForObject("select count(*) from task_assignment a where a.task_id=? and a.status='ACTIVE' and not exists (select 1 from team_member m where m.team_id=? and m.user_id=a.user_id and m.status='ACTIVE')",Long.class,taskId,teamId);
    if(invalid!=null && invalid>0)throw AppException.stateConflict("ASSIGNEES_NOT_TEAM_MEMBERS","All active assignees must join the Team before linking");
    jdbc.update("insert into task_team(task_id,team_id,linked_by) values (?,?,?) on conflict(task_id) do update set team_id=excluded.team_id,linked_by=excluded.linked_by,linked_at=now()",taskId,teamId,actor);
    jdbc.update("update task set version=version+1,updated_at=now() where id=?",taskId);
    audit.append(actor,"task.team.link","task",taskId,"ALLOWED",null,"{\"team_id\":\""+teamId+"\"}");
    return java.util.Map.of("taskId",taskId,"teamId",teamId,"version",expected+1);
  }
  @Transactional(readOnly=true)
  public java.util.List<java.util.Map<String,Object>> assignedTasks(UUID actor,UUID teamId,UUID userId) {
    team(actor,teamId,true);
    return jdbc.queryForList("select t.id,t.title,t.version,a.is_primary from task t join task_team tt on tt.task_id=t.id join task_assignment a on a.task_id=t.id where tt.team_id=? and a.user_id=? and a.status='ACTIVE' and t.deleted_at is null and t.state not in ('COMPLETED','CANCELLED') order by t.id limit 100",teamId,userId).stream().filter(row->{
      try {policy.requireTaskRead(actor,(UUID)row.get("id"));return true;}
      catch(AppException denied){if(denied.getHttpStatus().value()==403 || denied.getHttpStatus().value()==404)return false;throw denied;}
    }).toList();
  }
}
