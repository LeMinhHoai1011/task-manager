package vn.smarttask.security;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.smarttask.common.AppException;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.common.service.OutboxService;
import vn.smarttask.identity.repo.AppUserRepository;
import vn.smarttask.project.repo.*;
import vn.smarttask.project.service.ProjectService;
import vn.smarttask.workspace.repo.WorkspaceMembershipRepository;
import vn.smarttask.workspace.repo.WorkspaceRepository;

/** WO doc project chua tham gia -> 404 (khong phai 403). */
@ExtendWith(MockitoExtension.class)
public class NotFoundVsForbiddenTest {
  @Mock ProjectRepository projects;
  @Mock ProjectMembershipRepository members;
  @Mock MilestoneRepository milestones;
  @Mock WorkspaceRepository workspaces;
  @Mock WorkspaceMembershipRepository wsMembers;
  @Mock AppUserRepository users;
  @Mock AuditService audit;
  @Mock OutboxService outbox;

  @Test
  void outsiderReadReturns404() {
    ProjectService svc = new ProjectService(projects, members, milestones, workspaces,
        wsMembers, users, audit, outbox);
    UUID outsider = UUID.randomUUID(), pid = UUID.randomUUID();
    when(members.findMembership(pid, outsider)).thenReturn(Optional.empty());
    AppException ex = assertThrows(AppException.class, () -> svc.get(outsider, pid));
    assertEquals(404, ex.getHttpStatus().value());
    assertEquals("RESOURCE_NOT_FOUND", ex.getCode());
  }

  @Test
  void policyHidesExistenceWithoutRead() {
    AuthorizationPolicy policy = new AuthorizationPolicy();
    var actor = new AuthorizationPolicy.Actor(UUID.randomUUID(), true, false);
    var res = new AuthorizationPolicy.Resource(UUID.randomUUID(), true, false, false);
    assertEquals(AuthorizationPolicy.Decision.NOT_FOUND,
        policy.decide(actor, res, "project:read"));
    assertEquals("RESOURCE_NOT_FOUND",
        AuthorizationPolicy.errorCodeFor(AuthorizationPolicy.Decision.NOT_FOUND));
    // write-denied on visible resource -> 403
    var visible = new AuthorizationPolicy.Resource(UUID.randomUUID(), true, false, true);
    assertEquals(AuthorizationPolicy.Decision.FORBIDDEN,
        policy.decide(actor, visible, "project:write"));
  }
}
