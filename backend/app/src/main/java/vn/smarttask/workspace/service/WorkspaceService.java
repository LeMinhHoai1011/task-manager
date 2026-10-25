package vn.smarttask.workspace.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.smarttask.common.AppException;
import vn.smarttask.common.UuidV7;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.common.service.OutboxService;
import vn.smarttask.common.service.TokenHash;
import vn.smarttask.identity.entity.AppUser;
import vn.smarttask.identity.repo.AppUserRepository;
import vn.smarttask.project.repo.ProjectMembershipRepository;
import vn.smarttask.project.repo.ProjectRepository;
import vn.smarttask.project.entity.Project;
import vn.smarttask.project.entity.ProjectMembership;
import vn.smarttask.workspace.entity.*;
import vn.smarttask.workspace.repo.*;

@Service
public class WorkspaceService {
  private final WorkspaceRepository workspaces;
  private final WorkspaceMembershipRepository members;
  private final WorkspaceInvitationRepository invitations;
  private final AppUserRepository users;
  private final ProjectRepository projects;
  private final ProjectMembershipRepository projectMembers;
  private final AuditService audit;
  private final OutboxService outbox;

  public WorkspaceService(WorkspaceRepository workspaces, WorkspaceMembershipRepository members,
      WorkspaceInvitationRepository invitations, AppUserRepository users,
      ProjectRepository projects, ProjectMembershipRepository projectMembers,
      AuditService audit, OutboxService outbox) {
    this.workspaces = workspaces; this.members = members; this.invitations = invitations;
    this.users = users; this.projects = projects; this.projectMembers = projectMembers;
    this.audit = audit; this.outbox = outbox;
  }

  private AppUser requireActive(UUID userId) {
    AppUser u = users.findById(userId).orElseThrow(() -> AppException.notFound("User not found"));
    if (!u.isActive() || !"ACTIVE".equals(u.getStatus())) {
      audit.appendDenied(userId, "workspace.access", "workspace", null, "ACCOUNT_NOT_ACTIVE");
      throw AppException.accountNotActive("Account not active");
    }
    if (u.isLocked()) throw AppException.accountLocked("Locked");
    return u;
  }

  private WorkspaceMembership requireRole(UUID wsId, UUID userId, String... roles) {
    WorkspaceMembership m = members.findMembership(wsId, userId)
        .orElseThrow(() -> AppException.notFound("Workspace not found"));
    for (String r : roles) {
      if (r.equals(m.getRole())) return m;
    }
    audit.appendDenied(userId, "workspace.role", "workspace", wsId, "FORBIDDEN");
    throw AppException.forbidden("Insufficient workspace role");
  }

  @Transactional
  public Workspace createTeam(UUID creatorId, String name) {
    requireActive(creatorId);
    Workspace w = new Workspace(UuidV7.generate(), name, creatorId, "TEAM");
    workspaces.save(w);
    members.save(new WorkspaceMembership(UuidV7.generate(), w.getId(), creatorId, "OWNER"));
    audit.append(creatorId, "workspace.create", "workspace", w.getId(), "ALLOWED", null, "{}");
    outbox.emit("workspace", w.getId(), "workspace.created", "{}");
    return w;
  }

  @Transactional
  public Workspace createPersonal(UUID userId) {
    requireActive(userId);
    long n = workspaces.countByOwnerUserIdAndTypeAndStatusNot(userId, "PERSONAL", "DELETED");
    if (n > 0) {
      audit.appendDenied(userId, "workspace.createPersonal", "workspace", null, "ALREADY_EXISTS");
      throw AppException.stateConflict("PERSONAL_EXISTS", "Personal workspace already exists");
    }
    Workspace w = new Workspace(UuidV7.generate(), "personal", userId, "PERSONAL");
    workspaces.save(w);
    members.save(new WorkspaceMembership(UuidV7.generate(), w.getId(), userId, "OWNER"));
    audit.append(userId, "workspace.createPersonal", "workspace", w.getId(), "ALLOWED", null, "{}");
    return w;
  }

  @Transactional
  public WorkspaceInvitation invite(UUID actorId, UUID wsId, String email, String role, int daysValid) {    requireActive(actorId);
    requireRole(wsId, actorId, "OWNER", "ADMIN");
    Workspace w = workspaces.findById(wsId).orElseThrow(() -> AppException.notFound("WS not found"));
    if (!"ACTIVE".equals(w.getStatus())) {
      audit.appendDenied(actorId, "workspace.invite", "workspace", wsId, "WS_NOT_ACTIVE");
      throw AppException.stateConflict("WS_NOT_ACTIVE", "Workspace not active");
    }
    WorkspaceInvitation inv = new WorkspaceInvitation(UuidV7.generate(), wsId, email, role,
        TokenHash.sha256(UUID.randomUUID().toString()),
        OffsetDateTime.now(ZoneOffset.UTC).plusDays(daysValid));
    invitations.save(inv);
    audit.append(actorId, "workspace.invite", "workspace", wsId, "ALLOWED", null, "{}");
    return inv;
  }

  /** F1 read paths: membership-scoped list (hides DELETED), get, rename, soft-delete. */
  @Transactional(readOnly = true, timeout = 15)
  public java.util.List<Workspace> listForUser(UUID userId) {
    requireActive(userId);
    java.util.List<Workspace> out = new java.util.ArrayList<>();
    for (var m : members.findByUserId(userId)) {
      if (!"ACTIVE".equals(m.getStatus())) continue;
      workspaces.findById(m.getWorkspaceId()).ifPresent(w -> {
        if (!"DELETED".equals(w.getStatus())) out.add(w);
      });
    }
    return out;
  }

  @Transactional(readOnly = true, timeout = 15)
  public Workspace get(UUID actorId, UUID wsId) {
    Workspace w = workspaces.findById(wsId)
        .orElseThrow(() -> AppException.notFound("Workspace not found"));
    if ("DELETED".equals(w.getStatus())) throw AppException.notFound("Workspace not found");
    members.findMembership(wsId, actorId)
        .orElseThrow(() -> AppException.notFound("Workspace not found"));
    return w;
  }

  @Transactional
  public Workspace update(UUID actorId, UUID wsId, String name, long expectedVersion) {
    requireActive(actorId);
    Workspace w = workspaces.findByIdForUpdate(wsId)
        .orElseThrow(() -> AppException.notFound("Workspace not found"));
    if ("DELETED".equals(w.getStatus())) throw AppException.notFound("Workspace not found");
    requireRole(wsId, actorId, "OWNER", "ADMIN");
    if (w.getVersion() != expectedVersion) {
      audit.appendDenied(actorId, "workspace.update", "workspace", wsId, "VERSION_CONFLICT");
      throw AppException.versionConflict("Stale version", w.getVersion());
    }
    if (name == null || name.isBlank()) throw AppException.badRequest("NAME_REQUIRED", "Name required");
    w.setName(name.trim());
    workspaces.save(w);
    audit.append(actorId, "workspace.update", "workspace", wsId, "ALLOWED", null, "{}");
    return w;
  }

  @Transactional
  public void delete(UUID actorId, UUID wsId, long expectedVersion) {
    requireActive(actorId);
    Workspace w = workspaces.findByIdForUpdate(wsId)
        .orElseThrow(() -> AppException.notFound("Workspace not found"));
    if ("DELETED".equals(w.getStatus())) throw AppException.notFound("Workspace not found");
    requireRole(wsId, actorId, "OWNER");
    if (w.getVersion() != expectedVersion) {
      audit.appendDenied(actorId, "workspace.delete", "workspace", wsId, "VERSION_CONFLICT");
      throw AppException.versionConflict("Stale version", w.getVersion());
    }
    w.setStatus("DELETED");
    w.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
    w.setDeletedBy(actorId);
    workspaces.save(w);
    audit.append(actorId, "workspace.delete", "workspace", wsId, "ALLOWED", null, "{}");
    outbox.emit("workspace", wsId, "workspace.deleted", "{}");
  }

  /** Remove member; blocks when target is last manager of a non-deleted project. */
  @Transactional
  public void removeMember(UUID actorId, UUID wsId, UUID targetId) {
    requireActive(actorId);
    requireRole(wsId, actorId, "OWNER", "ADMIN");
    // last-project-manager guard
    for (Project p : projects.findByWorkspaceId(wsId)) {
      if ("DELETED".equals(p.getStatus())) continue;
      boolean targetIsManager = projectMembers.findByProjectIdAndUserId(p.getId(), targetId)
          .filter(m -> "MANAGER".equals(m.getRole()) && "ACTIVE".equals(m.getStatus())).isPresent();
      if (targetIsManager && projectMembers.countActiveManager(p.getId()) <= 1) {
        audit.appendDenied(actorId, "workspace.member.remove", "project", p.getId(), "LAST_PROJECT_MANAGER");
        throw AppException.lastProjectManager("Cannot remove last project manager");
      }
    }
    WorkspaceMembership m = members.findByWorkspaceIdAndUserId(wsId, targetId)
        .orElseThrow(() -> AppException.notFound("Membership not found"));
    if ("OWNER".equals(m.getRole())) {
      audit.appendDenied(actorId, "workspace.member.remove", "workspace", wsId, "OWNER_REMOVE");
      throw AppException.forbidden("Use ownerTransfer to move OWNER");
    }
    m.setStatus("REMOVED");
    members.save(m);
    audit.append(actorId, "workspace.member.remove", "workspace", wsId, "ALLOWED", null, "{}");
  }

  @Transactional
  public void leave(UUID userId, UUID wsId) {
    removeMemberSelf(userId, wsId);
  }

  private void removeMemberSelf(UUID userId, UUID wsId) {
    requireActive(userId);
    for (Project p : projects.findByWorkspaceId(wsId)) {
      if ("DELETED".equals(p.getStatus())) continue;
      boolean selfIsManager = projectMembers.findByProjectIdAndUserId(p.getId(), userId)
          .filter(m -> "MANAGER".equals(m.getRole()) && "ACTIVE".equals(m.getStatus())).isPresent();
      if (selfIsManager && projectMembers.countActiveManager(p.getId()) <= 1) {
        audit.appendDenied(userId, "workspace.leave", "project", p.getId(), "LAST_PROJECT_MANAGER");
        throw AppException.lastProjectManager("Last project manager cannot leave");
      }
    }
    WorkspaceMembership m = members.findByWorkspaceIdAndUserId(wsId, userId)
        .orElseThrow(() -> AppException.notFound("Membership not found"));
    if ("OWNER".equals(m.getRole())) {
      audit.appendDenied(userId, "workspace.leave", "workspace", wsId, "OWNER_LEAVE");
      throw AppException.forbidden("Owner must transfer first");
    }
    m.setStatus("REMOVED");
    members.save(m);
    audit.append(userId, "workspace.leave", "workspace", wsId, "ALLOWED", null, "{}");
  }

  @Transactional
  public void roleChange(UUID actorId, UUID wsId, UUID targetId, String newRole) {
    requireActive(actorId);
    requireRole(wsId, actorId, "OWNER");
    WorkspaceMembership m = members.findByWorkspaceIdAndUserId(wsId, targetId)
        .orElseThrow(() -> AppException.notFound("Membership not found"));
    if (!"ACTIVE".equals(m.getStatus())) throw AppException.stateConflict("INACTIVE_MEMBER", "Target not active");
    if ("GUEST".equals(newRole) && "OWNER".equals(m.getRole())) {
      throw AppException.forbidden("Cannot demote owner to guest directly");
    }
    m.setRole(newRole);
    members.save(m);
    audit.append(actorId, "workspace.role.change", "workspace", wsId, "ALLOWED", null, "{}");
  }

  /**
   * Atomic owner transfer: lock workspace, target ACTIVE non-GUEST, old OWNER-&gt;MEMBER,
   * project roles unchanged. Emergency variant requires reason&gt;=10 chars + reauth ticket consumed.
   */
  @Transactional
  public void ownerTransfer(UUID actorId, UUID wsId, UUID targetId,
      boolean emergency, String reason, UUID reauthTicketId,
      vn.smarttask.identity.service.IdentityService identityService,
      String reauthAction) {
    requireActive(actorId);
    Workspace w = workspaces.findByIdForUpdate(wsId)
        .orElseThrow(() -> AppException.notFound("Workspace not found"));
    WorkspaceMembership cur = members.findByWorkspaceIdAndUserId(wsId, actorId)
        .orElseThrow(() -> AppException.notFound("Workspace not found"));
    if (!"OWNER".equals(cur.getRole()) && !emergency) {
      audit.appendDenied(actorId, "workspace.owner.transfer", "workspace", wsId, "FORBIDDEN");
      throw AppException.forbidden("Only owner can transfer");
    }
    if (emergency) {
      if (reason == null || reason.length() < 10) {
        audit.appendDenied(actorId, "workspace.owner.transfer", "workspace", wsId, "REASON_SHORT");
        throw AppException.stateConflict("REASON_SHORT", "Emergency reason >= 10 chars");
      }
      if (reauthTicketId == null) {
        audit.appendDenied(actorId, "workspace.owner.transfer", "workspace", wsId, "REAUTH_REQUIRED");
        throw AppException.reauthRequired("Reauth ticket required");
      }
      identityService.consumeReauthTicket(reauthTicketId, actorId, reauthAction, wsId);
    }
    WorkspaceMembership target = members.findByWorkspaceIdAndUserId(wsId, targetId)
        .orElseThrow(() -> AppException.notFound("Target not member"));
    if (!"ACTIVE".equals(target.getStatus()) || "GUEST".equals(target.getRole())) {
      audit.appendDenied(actorId, "workspace.owner.transfer", "workspace", wsId, "TARGET_INVALID");
      throw AppException.stateConflict("TARGET_INVALID", "Target must be ACTIVE non-GUEST");
    }
    // two owners must never exist: demote old first, then promote
    cur.setRole("MEMBER");
    members.save(cur);
    target.setRole("OWNER");
    members.save(target);
    w.setOwnerUserId(targetId);
    workspaces.save(w);
    // emergency transfer: content_read=false metadata-only (no project role change by design)
    audit.append(actorId, "workspace.owner.transfer", "workspace", wsId,
        "ALLOWED", emergency ? "EMERGENCY:" + reason : null, "{\"content_read\":false}");
    outbox.emit("workspace", wsId, "workspace.owner.transferred", "{}");
  }
}
