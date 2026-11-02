package vn.smarttask.task.service;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.smarttask.common.AppException;
import vn.smarttask.common.UuidV7;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.common.service.OutboxService;
import vn.smarttask.task.entity.TaskAssignment;
import vn.smarttask.task.repo.*;
import vn.smarttask.project.repo.ProjectMembershipRepository;
import vn.smarttask.identity.repo.AppUserRepository;
import vn.smarttask.security.PolicyEnforcer;

@Service
public class TaskResponsibilityService {
  private final TaskRepository tasks;
  private final TaskAssignmentRepository assignments;
  private final ProjectMembershipRepository members;
  private final AppUserRepository users;
  private final PolicyEnforcer policy;
  private final AuditService audit;
  private final OutboxService outbox;
  private final vn.smarttask.collab.service.ActivityService activity;
  private final JdbcTemplate jdbc;
  public TaskResponsibilityService(TaskRepository tasks, TaskAssignmentRepository assignments,
      ProjectMembershipRepository members, AppUserRepository users, PolicyEnforcer policy,
      AuditService audit, OutboxService outbox, vn.smarttask.collab.service.ActivityService activity, JdbcTemplate jdbc) {
    this.tasks=tasks; this.assignments=assignments; this.members=members; this.users=users;
    this.policy=policy; this.audit=audit; this.outbox=outbox; this.activity=activity;this.jdbc=jdbc;
  }
  private boolean eligibleTeam(UUID taskId,UUID userId) {
    return Boolean.TRUE.equals(jdbc.queryForObject("select not exists(select 1 from task_team tt where tt.task_id=? and not exists(select 1 from team_member tm where tm.team_id=tt.team_id and tm.user_id=? and tm.status='ACTIVE' and tm.role in ('MEMBER','MANAGER')))",Boolean.class,taskId,userId));
  }
  public record Result(UUID taskId, UUID primaryUserId, int version) {}
  public record Candidate(UUID userId, String displayName) {}
  @Transactional(readOnly=true)
  public Result current(UUID actor, UUID id) {
    policy.requireTaskRead(actor,id);
    var task=tasks.findById(id).orElseThrow(() -> AppException.notFound("Task not found"));
    UUID primary=assignments.findByTaskIdAndStatus(id,"ACTIVE").stream().filter(TaskAssignment::isPrimary)
        .map(TaskAssignment::getUserId).findFirst().orElse(null);
    return new Result(id,primary,task.getVersion());
  }
  @Transactional(readOnly=true)
  public java.util.List<Candidate> candidates(UUID actor,UUID id) {
    policy.requireTaskManagerWrite(actor,id,"task.assign","task:write");
    var task=tasks.findById(id).orElseThrow(() -> AppException.notFound("Task not found"));
    return members.findByProjectId(task.getProjectId()).stream()
        .filter(m -> "ACTIVE".equals(m.getStatus()) && java.util.Set.of("MEMBER","MANAGER","OWNER","ADMIN").contains(m.getRole()))
        .map(m -> users.findById(m.getUserId()).filter(u -> u.isActive() && "ACTIVE".equals(u.getStatus()))
            .map(u -> new Candidate(u.getId(),u.getDisplayName())).orElse(null))
        .filter(java.util.Objects::nonNull).filter(c -> eligibleTeam(id,c.userId())).toList();
  }
  @Transactional
  public Result transfer(UUID actor, UUID id, UUID target, long expected) {
    var task = tasks.findByIdForUpdate(id).orElseThrow(() -> AppException.notFound("Task not found"));
    // Lock before policy loads the entity, avoiding a stale first-level-cache version after waiting.
    policy.requireTaskManagerWrite(actor, id, "task.assign", "task:write");
    if (task.getVersion()!=expected) throw AppException.versionConflict("Stale task version",task.getVersion());
    if (java.util.Set.of("PENDING_APPROVAL","COMPLETED","CANCELLED").contains(task.getState()))
      throw AppException.stateConflict("EXECUTION_LOCKED","Task state blocks reassignment");
    if(!eligibleTeam(id,target))throw AppException.stateConflict("NOT_TEAM_EXECUTOR","Target must be an active executor of the linked Team");
    members.findMembership(task.getProjectId(),target)
        .filter(m -> java.util.Set.of("MEMBER","MANAGER","OWNER","ADMIN").contains(m.getRole()))
        .orElseThrow(() -> AppException.stateConflict("ASSIGNEE_NOT_ELIGIBLE","Target must be an active project executor"));
    users.findById(target).filter(u -> u.isActive() && "ACTIVE".equals(u.getStatus()))
        .orElseThrow(() -> AppException.stateConflict("TARGET_NOT_ACTIVE","Target must be active"));
    UUID previous = null;
    for (var assignment : assignments.findByTaskId(id)) {
      if (assignment.isPrimary() && "ACTIVE".equals(assignment.getStatus())) previous=assignment.getUserId();
      if (assignment.isPrimary()) { assignment.setPrimary(false); assignments.save(assignment); }
    }
    assignments.flush();
    var next = assignments.findByTaskIdAndUserId(id,target).orElseGet(() -> new TaskAssignment(UuidV7.generate(),id,target));
    next.setStatus("ACTIVE"); next.setRemovalReason(null); next.setPrimary(true); assignments.save(next);
    task.setVersion(task.getVersion()+1); tasks.save(task);
    String payload = "{\"user_id\":\""+target+"\",\"previous_user_id\":"+(previous==null ? "null" : "\""+previous+"\"")+"}";
    audit.append(actor,"task.primary.transfer","task",id,"ALLOWED",null,payload);
    activity.append(id,task.getProjectId(),actor,"TASK_PRIMARY_TRANSFERRED",payload);
    outbox.emit("task",id,"task.reassigned",payload);
    return new Result(id,target,task.getVersion());
  }
}
