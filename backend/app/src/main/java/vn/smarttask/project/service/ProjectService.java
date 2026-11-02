package vn.smarttask.project.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.smarttask.common.AppException;
import vn.smarttask.common.UuidV7;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.common.service.OutboxService;
import vn.smarttask.identity.entity.AppUser;
import vn.smarttask.identity.repo.AppUserRepository;
import vn.smarttask.project.entity.*;
import vn.smarttask.project.repo.*;
import vn.smarttask.realtime.RealtimePublisher;
import vn.smarttask.workspace.entity.Workspace;
import vn.smarttask.workspace.repo.WorkspaceMembershipRepository;
import vn.smarttask.workspace.repo.WorkspaceRepository;

@Service
public class ProjectService {
  private final ProjectRepository projects;
  private final ProjectMembershipRepository members;
  private final MilestoneRepository milestones;
  private final WorkspaceRepository workspaces;
  private final WorkspaceMembershipRepository wsMembers;
  private final AppUserRepository users;
  private final AuditService audit;
  private final OutboxService outbox;

  /** Pending approval rounds counter hook (wired to task module later; stub for PH-1). */
  public interface PendingApprovalCounter {
    long countOpenByProject(UUID projectId);
  }

  private PendingApprovalCounter pendingCounter = pid -> 0L;

  public ProjectService(ProjectRepository projects, ProjectMembershipRepository members,
      MilestoneRepository milestones, WorkspaceRepository workspaces,
      WorkspaceMembershipRepository wsMembers, AppUserRepository users,
      AuditService audit, OutboxService outbox) {
    this.projects = projects; this.members = members; this.milestones = milestones;
    this.workspaces = workspaces; this.wsMembers = wsMembers; this.users = users;
    this.audit = audit; this.outbox = outbox;
  }

  public void setPendingCounter(PendingApprovalCounter c) { this.pendingCounter = c; }

  /** Optional in plain-unit tests; production injects it. Signals are advisory. */
  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private RealtimePublisher realtime;

  private void signalWorkspace(UUID workspaceId, String kind, UUID projectId) {
    if (realtime != null) realtime.workspace(workspaceId, kind, projectId);
  }

  private void signalProject(UUID projectId, String kind, UUID objectId) {
    if (realtime != null) realtime.project(projectId, kind, objectId);
  }

  // PJ-05/PJ-06 cascade wiring (setter-injected so PH-1 8-arg constructor keeps working)
  private vn.smarttask.task.repo.TaskRepository taskRepo;
  private vn.smarttask.workflow.repo.ApprovalRoundRepository roundRepo;
  private vn.smarttask.workflow.repo.WorkflowEventRepository eventRepo;

  public void setTaskCascade(vn.smarttask.task.repo.TaskRepository tasks,
      vn.smarttask.workflow.repo.ApprovalRoundRepository rounds,
      vn.smarttask.workflow.repo.WorkflowEventRepository events) {
    this.taskRepo = tasks; this.roundRepo = rounds; this.eventRepo = events;
  }

  private AppUser requireActive(UUID userId) {
    AppUser u = users.findById(userId).orElseThrow(() -> AppException.notFound("User not found"));
    if (!u.isActive() || !"ACTIVE".equals(u.getStatus())) throw AppException.accountNotActive("Not active");
    if (u.isLocked()) throw AppException.accountLocked("Locked");
    return u;
  }

  private ProjectMembership requireManager(UUID projectId, UUID userId) {
    ProjectMembership m = members.findMembership(projectId, userId)
        .orElseThrow(() -> AppException.notFound("Project not found"));
    if (!java.util.Set.of("MANAGER","OWNER","ADMIN").contains(m.getRole())) {
      audit.appendDenied(userId, "project.manage", "project", projectId, "FORBIDDEN");
      throw AppException.forbidden("Project manager required");
    }
    return m;
  }

  @Transactional
  public Project activate(UUID actor,UUID id,long expected) {
    requireActive(actor);
    var project=projects.findByIdForUpdate(id).orElseThrow(()->AppException.notFound("Project not found"));
    requireManager(id,actor);
    var workspace=workspaces.findById(project.getWorkspaceId()).orElseThrow(()->AppException.notFound("Workspace not found"));
    if(!"ACTIVE".equals(workspace.getStatus()))throw AppException.stateConflict("WS_NOT_ACTIVE","Workspace must be active");
    if(project.getVersion()!=expected)throw AppException.versionConflict("Stale project version",project.getVersion());
    if(!"PLANNING".equals(project.getStatus()))throw AppException.stateConflict("PROJECT_NOT_PLANNING","Only Planning projects can activate");
    project.setPreviousStatus("PLANNING");project.setStatus("ACTIVE");projects.saveAndFlush(project);
    audit.append(actor,"project.activate","project",id,"ALLOWED",null,"{}");outbox.emit("project",id,"project.activated","{}");signalProject(id,"PROJECT_ACTIVATED",id);
    return project;
  }
  @Transactional
  public Project create(UUID creatorId, UUID workspaceId, String name, String description) {
    requireActive(creatorId);
    Workspace w = workspaces.findById(workspaceId)
        .orElseThrow(() -> AppException.notFound("Workspace not found"));
    if (!"ACTIVE".equals(w.getStatus())) throw AppException.stateConflict("WS_ARCHIVED", "Workspace not active");
    var ws = wsMembers.findMembership(workspaceId, creatorId)
        .orElseThrow(() -> AppException.notFound("Workspace not found"));
    if ("GUEST".equals(ws.getRole())) {
      audit.appendDenied(creatorId, "project.create", "project", null, "GUEST_DENIED");
      throw AppException.forbidden("Workspace guest cannot create project");
    }
    Project p = new Project(UuidV7.generate(), workspaceId, name, creatorId);
    p.setDescription(description);
    projects.save(p);
    members.save(new ProjectMembership(UuidV7.generate(), p.getId(), creatorId, "MANAGER"));
    audit.append(creatorId, "project.create", "project", p.getId(), "ALLOWED", null, "{}");
    outbox.emit("project", p.getId(), "project.created", "{}");
    signalWorkspace(workspaceId, "PROJECT_CREATED", p.getId());
    return p;
  }

  @Transactional
  public ProjectMembership addMember(UUID actorId, UUID projectId, UUID targetId, String role) {
    requireActive(actorId);
    Project p = projects.findById(projectId).orElseThrow(() -> AppException.notFound("Project not found"));
    requireManager(projectId, actorId);
    // target must be ACTIVE account (no workspace membership required)
    AppUser target = users.findById(targetId)
        .orElseThrow(() -> {
          audit.appendDenied(actorId, "project.member.add", "project", projectId, "TARGET_NOT_FOUND");
          return AppException.notFound("Target user not found");
        });
    if (!target.isActive() || !"ACTIVE".equals(target.getStatus())) {
      audit.appendDenied(actorId, "project.member.add", "project", projectId, "TARGET_NOT_ACTIVE");
      throw AppException.stateConflict("TARGET_NOT_ACTIVE", "Target account must be ACTIVE");
    }
    if ("MANAGER".equals(role) && wsMembers.findMembership(p.getWorkspaceId(), targetId)
        .filter(m -> "ACTIVE".equals(m.getStatus()) && "GUEST".equals(m.getRole())).isPresent()) {
      audit.appendDenied(actorId, "project.member.add", "project", projectId, "GUEST_GUARD");
      throw AppException.forbidden("Workspace guest cannot manage a project");
    }
    // no duplicate membership
    if (members.findByProjectIdAndUserId(projectId, targetId).isPresent()) {
      audit.appendDenied(actorId, "project.member.add", "project", projectId, "ALREADY_MEMBER");
      throw AppException.stateConflict("ALREADY_MEMBER", "Target is already a project member");
    }
    // cannot invite self as member with manager role escalation
    if (actorId.equals(targetId) && "MANAGER".equals(role)) {
      audit.appendDenied(actorId, "project.member.add", "project", projectId, "SELF_ESCALATION");
      throw AppException.forbidden("Self promotion to MANAGER forbidden");
    }
    ProjectMembership m = new ProjectMembership(UuidV7.generate(), projectId, targetId, role);
    members.save(m);
    audit.append(actorId, "project.member.add", "project", projectId, "ALLOWED", null, "{}");
    signalProject(projectId, "PROJECT_MEMBER_CHANGED", m.getId());
    return m;
  }

  @Transactional
  public void removeMember(UUID actorId, UUID projectId, UUID targetId) {
    requireActive(actorId);
    requireManager(projectId, actorId);
    ProjectMembership m = members.findByProjectIdAndUserId(projectId, targetId)
        .orElseThrow(() -> AppException.notFound("Membership not found"));
    if ("MANAGER".equals(m.getRole()) && members.countActiveManager(projectId) <= 1) {
      audit.appendDenied(actorId, "project.member.remove", "project", projectId, "LAST_PROJECT_MANAGER");
      throw AppException.lastProjectManager("Cannot remove last manager");
    }
    m.setStatus("REMOVED");
    members.save(m);
    audit.append(actorId, "project.member.remove", "project", projectId, "ALLOWED", null, "{}");
    signalProject(projectId, "PROJECT_MEMBER_CHANGED", projectId);
  }

  @Transactional
  public void roleChange(UUID actorId, UUID projectId, UUID targetId, String newRole) {
    requireActive(actorId);
    requireManager(projectId, actorId);
    ProjectMembership m = members.findByProjectIdAndUserId(projectId, targetId)
        .orElseThrow(() -> AppException.notFound("Membership not found"));
    if ("MANAGER".equals(m.getRole()) && !"MANAGER".equals(newRole)
        && members.countActiveManager(projectId) <= 1) {
      audit.appendDenied(actorId, "project.role.change", "project", projectId, "LAST_PROJECT_MANAGER");
      throw AppException.lastProjectManager("Cannot demote last manager");
    }
    m.setRole(newRole);
    members.save(m);
    audit.append(actorId, "project.role.change", "project", projectId, "ALLOWED", null, "{}");
  }

  @Transactional
  public Milestone createMilestone(UUID actorId, UUID projectId, String title, OffsetDateTime dueAt) {
    requireActive(actorId);
    requireManager(projectId, actorId);
    Milestone m = new Milestone(UuidV7.generate(), projectId, title, dueAt);
    milestones.save(m);
    audit.append(actorId, "project.milestone.create", "project", projectId, "ALLOWED", null, "{}");
    return m;
  }

  @Transactional
  public void deleteMilestone(UUID actorId, UUID milestoneId) {
    Milestone m = milestones.findById(milestoneId)
        .orElseThrow(() -> AppException.notFound("Milestone not found"));
    requireActive(actorId);
    requireManager(m.getProjectId(), actorId);
    // delete = unlink only (metadata preserved in audit)
    milestones.delete(m);
    audit.append(actorId, "project.milestone.delete", "project", m.getProjectId(),
        "ALLOWED", "UNLINK_ONLY", "{}");
  }

  @Transactional
  public void approvalToggle(UUID actorId, UUID projectId, boolean enable, long expectedVersion) {
    requireActive(actorId);
    Project p = projects.findByIdForUpdate(projectId)
        .orElseThrow(() -> AppException.notFound("Project not found"));
    requireManager(projectId, actorId);
    if (p.getVersion() != expectedVersion) {
      audit.appendDenied(actorId, "project.approval.toggle", "project", projectId, "VERSION_CONFLICT");
      throw AppException.versionConflict("Stale version", p.getVersion());
    }
    if (!enable && pendingCounter.countOpenByProject(projectId) > 0) {
      audit.appendDenied(actorId, "project.approval.toggle", "project", projectId, "PENDING_APPROVALS_EXIST");
      throw AppException.stateConflict("PENDING_APPROVALS_EXIST", "Open approval rounds exist");
    }
    p.setApprovalEnabled(enable);
    projects.save(p);
    audit.append(actorId, "project.approval.toggle", "project", projectId, "ALLOWED", null, "{}");
  }

  /** Emergency manager restore: metadata-only, requires reauth. */
  @Transactional
  public void managerRestoreEmergency(UUID actorId, UUID projectId, UUID targetId,
      boolean reauthed, com.fasterxml.jackson.databind.ObjectMapper om) {
    if (!reauthed) {
      audit.appendDenied(actorId, "project.manager.restore", "project", projectId, "REAUTH_REQUIRED");
      throw AppException.reauthRequired("Reauth required");
    }
    ProjectMembership m = members.findByProjectIdAndUserId(projectId, targetId)
        .orElseThrow(() -> AppException.notFound("Membership not found"));
    m.setRole("MANAGER");
    m.setStatus("ACTIVE");
    members.save(m);
    audit.append(actorId, "project.manager.restore", "project", projectId,
        "ALLOWED", "EMERGENCY_METADATA_ONLY", "{\"content_read\":false}");
  }

  @Transactional(readOnly = true, timeout = 15)
  public List<ProjectMembership> listMembers(UUID actorId, UUID projectId) {    members.findMembership(projectId, actorId)
        .orElseThrow(() -> AppException.notFound("Project not found"));
    return members.findByProjectId(projectId);
  }

  @Transactional(readOnly = true, timeout = 15)
  public Project get(UUID actorId, UUID projectId) {
    members.findMembership(projectId, actorId)
        .orElseThrow(() -> AppException.notFound("Project not found"));
    return projects.findById(projectId).orElseThrow(() -> AppException.notFound("Project not found"));
  }

  /** F1 read path: projects the actor participates in, optionally scoped to a workspace. */
  @Transactional(readOnly = true, timeout = 15)
  public List<Project> listForUser(UUID actorId, UUID workspaceId) {
    List<Project> out = new java.util.ArrayList<>();
    if (workspaceId != null) {
      for (Project p : projects.findByWorkspaceId(workspaceId)) {
        if ("DELETED".equals(p.getStatus())) continue;
        if (members.findMembership(p.getId(), actorId).isPresent()) out.add(p);
      }
      return out;
    }
    // F2.5: actor-scoped membership read + single batch project load
    // (never members.findAll()). Order follows the membership rows.
    List<ProjectMembership> mine = members.findByUserIdAndStatus(actorId, "ACTIVE");
    if (mine.isEmpty()) return out;
    java.util.Map<UUID, Project> byId = new java.util.LinkedHashMap<>();
    for (Project p : projects.findAllById(
        mine.stream().map(ProjectMembership::getProjectId).distinct().toList())) {
      byId.put(p.getId(), p);
    }
    for (ProjectMembership m : mine) {
      Project p = byId.get(m.getProjectId());
      if (p != null && !"DELETED".equals(p.getStatus()) && !out.contains(p)) out.add(p);
    }
    return out;
  }

  /** F1 update path: rename + description with optimistic concurrency. */
  @Transactional
  public Project update(UUID actorId, UUID projectId, String name, String description,
      long expectedVersion) {
    requireActive(actorId);
    Project p = projects.findByIdForUpdate(projectId)
        .orElseThrow(() -> AppException.notFound("Project not found"));
    if ("DELETED".equals(p.getStatus())) throw AppException.notFound("Project not found");
    if (!"PLANNING".equals(p.getStatus()) && !"ACTIVE".equals(p.getStatus())) {
      throw AppException.stateConflict("PROJECT_NOT_WRITABLE", "Project not writable");
    }
    requireManager(projectId, actorId);
    if (p.getVersion() != expectedVersion) {
      audit.appendDenied(actorId, "project.update", "project", projectId, "VERSION_CONFLICT");
      throw AppException.versionConflict("Stale version", p.getVersion());
    }
    if (name != null && !name.isBlank()) p.setName(name.trim());
    if (description != null) p.setDescription(description);
    projects.save(p);
    audit.append(actorId, "project.update", "project", projectId, "ALLOWED", null, "{}");
    outbox.emit("project", projectId, "project.updated", "{}");
    signalWorkspace(p.getWorkspaceId(), "PROJECT_UPDATED", projectId);
    return p;
  }

  /** PJ-05: complete project + synchronous cascade in ONE transaction. COMPLETED tasks kept; rest CANCELLED (T-10); OPEN rounds + OPEN milestones CANCELLED. */
  @Transactional
  public Project complete(UUID actorId, UUID projectId, long expectedVersion) {
    requireActive(actorId);
    Project p = projects.findByIdForUpdate(projectId)
        .orElseThrow(() -> AppException.notFound("Project not found"));
    requireManager(projectId, actorId);
    if (p.getVersion() != expectedVersion) {
      audit.appendDenied(actorId, "project.complete", "project", projectId, "VERSION_CONFLICT");
      throw AppException.versionConflict("Stale version", p.getVersion());
    }
    if (!"ACTIVE".equals(p.getStatus())) {
      throw AppException.stateConflict("PROJECT_NOT_ACTIVE", "Only ACTIVE project can complete");
    }
    p.setStatus("COMPLETED");
    projects.save(p);
    String reason = "PROJECT_COMPLETED_CASCADE";
    java.time.OffsetDateTime now = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC);
    if (taskRepo != null) {
      for (var t : taskRepo.findByProjectId(projectId)) {
        if ("COMPLETED".equals(t.getState()) || "CANCELLED".equals(t.getState())) continue;
        if (roundRepo != null) {
          for (var r : roundRepo.findByTaskIdAndStatus(t.getId(), "OPEN")) {
            r.setStatus("CANCELLED");
            r.setRoundOutcome("CANCELLED");
            r.setDecidedAt(now);
            roundRepo.save(r);
          }
        }
        String from = t.getState();
        t.setState("CANCELLED");
        t.setCancelledAt(now);
        t.setCancelledBy(actorId);
        t.setCancelledReason(reason);
        t.setVersion(t.getVersion() + 1);
        taskRepo.save(t);
        if (eventRepo != null) {
          eventRepo.save(new vn.smarttask.workflow.entity.WorkflowEvent(
              UuidV7.generate(), t.getId(), from, "CANCELLED", "T-10", actorId));
        }
        audit.append(actorId, "workflow.cascade", "task", t.getId(), "ALLOWED", reason, "{}");
        outbox.emit("task", t.getId(), "task.workflow.t_10", "{\"reason\":\"" + reason + "\"}");
      }
    }
    for (var m : milestones.findByProjectId(projectId)) {
      if ("OPEN".equals(m.getStatus())) {
        m.setStatus("CANCELLED");
        m.setCancelledReason(reason);
        m.setCancelledAt(now);
        milestones.save(m);
      }
    }
    audit.append(actorId, "project.complete", "project", projectId, "ALLOWED", null, "{}");
    outbox.emit("project", projectId, "project.completed", "{}");
    signalProject(projectId, "PROJECT_COMPLETED", projectId);
    return p;
  }

  /** PJ-06: COMPLETED->ACTIVE reactivation; never auto-reopens tasks. */
  @Transactional
  public Project reactivate(UUID actorId, UUID projectId, long expectedVersion) {
    requireActive(actorId);
    Project p = projects.findByIdForUpdate(projectId)
        .orElseThrow(() -> AppException.notFound("Project not found"));
    requireManager(projectId, actorId);
    if (p.getVersion() != expectedVersion) {
      audit.appendDenied(actorId, "project.reactivate", "project", projectId, "VERSION_CONFLICT");
      throw AppException.versionConflict("Stale version", p.getVersion());
    }
    if (!"COMPLETED".equals(p.getStatus())) {
      throw AppException.stateConflict("PROJECT_NOT_COMPLETED", "Only COMPLETED project can reactivate");
    }
    p.setPreviousStatus(p.getStatus());
    p.setStatus("ACTIVE");
    projects.save(p);
    audit.append(actorId, "project.reactivate", "project", projectId, "ALLOWED", "NO_TASK_REOPEN", "{}");
    outbox.emit("project", projectId, "project.reactivated", "{}");
    signalProject(projectId, "PROJECT_REACTIVATED", projectId);
    return p;
  }
}
