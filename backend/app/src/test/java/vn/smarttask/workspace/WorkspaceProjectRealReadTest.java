package vn.smarttask.workspace;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.smarttask.common.ApiResult;
import vn.smarttask.common.AppException;
import vn.smarttask.common.PageResponse;
import vn.smarttask.identity.service.IdentityService;
import vn.smarttask.project.ProjectController;
import vn.smarttask.project.entity.Milestone;
import vn.smarttask.project.entity.Project;
import vn.smarttask.project.repo.MilestoneRepository;
import vn.smarttask.project.repo.ProjectMembershipRepository;
import vn.smarttask.project.repo.ProjectRepository;
import vn.smarttask.project.service.ProjectService;
import vn.smarttask.security.PolicyEnforcer;
import vn.smarttask.security.Principal;
import vn.smarttask.workspace.entity.Workspace;
import vn.smarttask.workspace.entity.WorkspaceMembership;
import vn.smarttask.workspace.repo.WorkspaceMembershipRepository;
import vn.smarttask.workspace.repo.WorkspaceRepository;
import vn.smarttask.workspace.service.WorkspaceService;

/**
 * F1 workspace/project real reads (offline, Mockito): list/get from repos,
 * update/delete with If-Match, milestones from repo, policy entry intact.
 */
@ExtendWith(MockitoExtension.class)
class WorkspaceProjectRealReadTest {

  @Mock WorkspaceRepository workspaces;
  @Mock WorkspaceMembershipRepository wsMembers;
  @Mock IdentityService identity;
  @Mock PolicyEnforcer policy;
  @Mock ProjectRepository projects;
  @Mock ProjectMembershipRepository projectMembers;
  @Mock MilestoneRepository milestones;
  @Mock vn.smarttask.common.service.AuditService audit;
  @Mock vn.smarttask.common.service.OutboxService outbox;
  @Mock vn.smarttask.workspace.repo.WorkspaceInvitationRepository invitations;
  @Mock vn.smarttask.identity.repo.AppUserRepository users;

  private WorkspaceController wsController;
  private ProjectController projectController;
  private final UUID actor = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    var wsService = new WorkspaceService(workspaces, wsMembers, invitations, users, projects,
        projectMembers, audit, outbox);
    wsController = new WorkspaceController(wsService, identity, policy);
    var projectService = new ProjectService(projects, projectMembers, milestones, workspaces,
        wsMembers, users, audit, outbox);
    projectController = new ProjectController(projectService, policy, milestones);
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(new Principal(actor, UUID.randomUUID()), null,
            List.of()));
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  private Workspace ws(UUID id) {
    Workspace w = new Workspace(id, "w", actor, "TEAM");
    w.setStatus("ACTIVE");
    return w;
  }

  @Test
  void workspaceListHidesDeleted() {
    UUID a = UUID.randomUUID(), b = UUID.randomUUID();
    Workspace deleted = ws(b);
    deleted.setStatus("DELETED");
    when(wsMembers.findByUserId(actor)).thenReturn(List.of(
        new WorkspaceMembership(UUID.randomUUID(), a, actor, "OWNER"),
        new WorkspaceMembership(UUID.randomUUID(), b, actor, "MEMBER")));
    when(workspaces.findById(a)).thenReturn(Optional.of(ws(a)));
    when(workspaces.findById(b)).thenReturn(Optional.of(deleted));
    when(users.findById(actor)).thenReturn(Optional.of(activeUser()));

    ResponseEntity<ApiResult<PageResponse<WorkspaceController.WorkspaceDto>>> res =
        wsController.list(null, 20);
    assertEquals(1, res.getBody().data().data().size());
    assertEquals(a, res.getBody().data().data().get(0).id());
  }

  @Test
  void workspaceGetReadsStoredRow() {
    UUID id = UUID.randomUUID();
    when(workspaces.findById(id)).thenReturn(Optional.of(ws(id)));
    when(wsMembers.findMembership(id, actor)).thenReturn(
        Optional.of(new WorkspaceMembership(UUID.randomUUID(), id, actor, "MEMBER")));

    var res = wsController.get(id);
    assertEquals("w", res.getBody().data().name());
  }

  @Test
  void workspaceUpdateRequiresIfMatch() {
    UUID id = UUID.randomUUID();
    AppException ex = assertThrows(AppException.class,
        () -> wsController.update(id, Map.of("name", "n2"), null));
    // §7: missing If-Match answers 428 (docs), code IF_MATCH_REQUIRED kept.
    assertEquals(428, ex.getHttpStatus().value());
    assertEquals("IF_MATCH_REQUIRED", ex.getCode());
  }

  @Test
  void projectListScopedToMembership() {
    UUID wsId = UUID.randomUUID(), p1 = UUID.randomUUID(), p2 = UUID.randomUUID();
    Project a = new Project(p1, wsId, "a", actor);
    Project bb = new Project(p2, wsId, "b", actor);
    when(projects.findByWorkspaceId(wsId)).thenReturn(List.of(a, bb));
    when(projectMembers.findMembership(p1, actor)).thenReturn(Optional.of(
        new vn.smarttask.project.entity.ProjectMembership(UUID.randomUUID(), p1, actor,
            "MEMBER")));
    when(projectMembers.findMembership(p2, actor)).thenReturn(Optional.empty());

    var res = projectController.listProjects(wsId, null, null, 20);
    assertEquals(1, res.getBody().data().data().size());
    assertEquals(p1, res.getBody().data().data().get(0).id());
  }

  @Test
  void projectUpdateRequiresIfMatch() {
    UUID id = UUID.randomUUID();
    AppException ex = assertThrows(AppException.class,
        () -> projectController.updateProject(id, Map.of("name", "n"), null));
    // §7: missing If-Match answers 428 (docs), code IF_MATCH_REQUIRED kept.
    assertEquals(428, ex.getHttpStatus().value());
    assertEquals("IF_MATCH_REQUIRED", ex.getCode());
  }

  @Test
  void milestonesReadRepo() {
    UUID pid = UUID.randomUUID();
    when(milestones.findByProjectId(pid)).thenReturn(
        List.of(new Milestone(UUID.randomUUID(), pid, "m1", null)));

    var res = projectController.milestones(pid);
    assertEquals(1, res.getBody().data().size());
    assertEquals("m1", res.getBody().data().get(0).title());
  }

  @Test
  void outsiderProjectListIs404FromPolicy() {
    UUID wsId = UUID.randomUUID();
    doThrow(AppException.notFound("Workspace not found"))
        .when(policy).requireWorkspaceRead(actor, wsId);
    AppException ex = assertThrows(AppException.class,
        () -> projectController.listProjects(wsId, null, null, 20));
    assertEquals(404, ex.getHttpStatus().value());
  }

  private vn.smarttask.identity.entity.AppUser activeUser() {
    var u = new vn.smarttask.identity.entity.AppUser(actor, "u", "u@x.io", "h", "U");
    u.setActive(true);
    u.setStatus("ACTIVE");
    return u;
  }
}
