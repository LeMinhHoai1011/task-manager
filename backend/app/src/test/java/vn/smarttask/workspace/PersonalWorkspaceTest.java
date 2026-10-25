package vn.smarttask.workspace;

import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.smarttask.common.AppException;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.common.service.OutboxService;
import vn.smarttask.identity.entity.AppUser;
import vn.smarttask.identity.repo.AppUserRepository;
import vn.smarttask.project.repo.ProjectMembershipRepository;
import vn.smarttask.project.repo.ProjectRepository;
import vn.smarttask.workspace.repo.*;
import vn.smarttask.workspace.service.WorkspaceService;

@ExtendWith(MockitoExtension.class)
public class PersonalWorkspaceTest {
  @Mock WorkspaceRepository workspaces;
  @Mock WorkspaceMembershipRepository members;
  @Mock WorkspaceInvitationRepository invitations;
  @Mock AppUserRepository users;
  @Mock ProjectRepository projects;
  @Mock ProjectMembershipRepository projectMembers;
  @Mock AuditService audit;
  @Mock OutboxService outbox;

  private WorkspaceService svc() {
    return new WorkspaceService(workspaces, members, invitations, users, projects,
        projectMembers, audit, outbox);
  }

  private AppUser activeUser(UUID id) {
    AppUser u = new AppUser(id, "u", "u@x.io", "h", "U");
    u.setStatus("ACTIVE"); u.setActive(true);
    when(users.findById(id)).thenReturn(Optional.of(u));
    return u;
  }

  @Test
  void activeCreatesExactlyOnePersonal() {
    UUID uid = UUID.randomUUID();
    activeUser(uid);
    when(workspaces.countByOwnerUserIdAndTypeAndStatusNot(uid, "PERSONAL", "DELETED")).thenReturn(0L);
    when(workspaces.save(any())).thenAnswer(i -> i.getArgument(0));
    when(members.save(any())).thenAnswer(i -> i.getArgument(0));
    var w = svc().createPersonal(uid);
    assertEquals("PERSONAL", w.getType());
    verify(workspaces).save(any());
    verify(audit).append(eq(uid), eq("workspace.createPersonal"), eq("workspace"),
        any(), eq("ALLOWED"), isNull(), any());
  }

  @Test
  void secondPersonalReturns409() {
    UUID uid = UUID.randomUUID();
    activeUser(uid);
    when(workspaces.countByOwnerUserIdAndTypeAndStatusNot(uid, "PERSONAL", "DELETED")).thenReturn(1L);
    AppException ex = assertThrows(AppException.class, () -> svc().createPersonal(uid));
    assertEquals(409, ex.getHttpStatus().value());
    assertEquals("STATE_CONFLICT", ex.getCode());
    verify(audit).appendDenied(eq(uid), eq("workspace.createPersonal"), eq("workspace"),
        isNull(), eq("ALREADY_EXISTS"));
  }
}
