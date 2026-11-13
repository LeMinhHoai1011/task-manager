package vn.smarttask.project;

import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import vn.smarttask.common.AppException;
import vn.smarttask.common.service.*;
import vn.smarttask.identity.entity.AppUser;
import vn.smarttask.identity.repo.AppUserRepository;
import vn.smarttask.project.repo.*;
import vn.smarttask.project.service.ProjectService;
import vn.smarttask.workspace.entity.*;
import vn.smarttask.workspace.repo.*;

class ProjectCreationRoleTest {
  @ParameterizedTest @ValueSource(strings={"OWNER","ADMIN","MEMBER","GUEST","VIEWER"})
  void onlyWorkspaceManagersCreateProjects(String role) {
    UUID actor=UUID.randomUUID(),wsId=UUID.randomUUID();
    var projects=mock(ProjectRepository.class);var members=mock(ProjectMembershipRepository.class);
    var workspaces=mock(WorkspaceRepository.class);var memberships=mock(WorkspaceMembershipRepository.class);
    var users=mock(AppUserRepository.class);var user=mock(AppUser.class);var ws=mock(Workspace.class);
    when(users.findById(actor)).thenReturn(Optional.of(user));when(user.isActive()).thenReturn(true);when(user.getStatus()).thenReturn("ACTIVE");
    when(workspaces.findById(wsId)).thenReturn(Optional.of(ws));when(ws.getStatus()).thenReturn("ACTIVE");
    when(memberships.findMembership(wsId,actor)).thenReturn(Optional.of(new WorkspaceMembership(UUID.randomUUID(),wsId,actor,role)));
    var service=new ProjectService(projects,members,mock(MilestoneRepository.class),workspaces,memberships,users,mock(AuditService.class),mock(OutboxService.class));
    if(Set.of("OWNER","ADMIN").contains(role)) {
      var p=service.create(actor,wsId,"P",null);assertEquals(wsId,p.getWorkspaceId());verify(members).save(any());
    }else {
      assertEquals(403,assertThrows(AppException.class,()->service.create(actor,wsId,"P",null)).getHttpStatus().value());
      verifyNoInteractions(projects,members);
    }
  }
}
