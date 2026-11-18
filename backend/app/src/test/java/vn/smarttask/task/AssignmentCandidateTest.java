package vn.smarttask.task;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.smarttask.identity.entity.AppUser;
import vn.smarttask.identity.repo.AppUserRepository;
import vn.smarttask.project.entity.ProjectMembership;
import vn.smarttask.project.repo.ProjectMembershipRepository;
import vn.smarttask.security.PolicyEnforcer;
import vn.smarttask.task.entity.Task;
import vn.smarttask.task.repo.TaskRepository;
import vn.smarttask.task.service.TaskResponsibilityService;

@ExtendWith(MockitoExtension.class)
class AssignmentCandidateTest {
  @Mock TaskRepository tasks;
  @Mock ProjectMembershipRepository members;
  @Mock AppUserRepository users;
  @Mock PolicyEnforcer policy;
  @Mock JdbcTemplate jdbc;
  @InjectMocks TaskResponsibilityService service;

  @Test void guestCanCollaborateButCannotBePrimaryExecutor() {
    UUID actor=UUID.randomUUID(),id=UUID.randomUUID(),project=UUID.randomUUID(),guest=UUID.randomUUID();
    Task task=mock(Task.class);
    when(task.getProjectId()).thenReturn(project);
    when(tasks.findById(id)).thenReturn(Optional.of(task));
    var membership=new ProjectMembership(UUID.randomUUID(),project,guest,"GUEST");
    membership.setStatus("ACTIVE");
    when(members.findByProjectId(project)).thenReturn(List.of(membership));
    var user=new AppUser(guest,"khach","hidden@example.test","hidden","Khách cộng tác");
    user.setActive(true);user.setStatus("ACTIVE");
    when(users.findById(guest)).thenReturn(Optional.of(user));
    when(jdbc.queryForObject(anyString(),eq(Boolean.class),eq(id),eq(guest))).thenReturn(true);
    assertEquals("khach",service.collaborators(actor,id).get(0).username());
    assertTrue(service.candidates(actor,id).isEmpty());
    verify(policy,times(2)).requireTaskManagerWrite(actor,id,"task.assign","task:write");
  }

  @Test void membershipInLinkedTeamIsRequiredForCollaborators() {
    UUID actor=UUID.randomUUID(),id=UUID.randomUUID(),project=UUID.randomUUID(),target=UUID.randomUUID();
    Task task=mock(Task.class);when(task.getProjectId()).thenReturn(project);
    when(tasks.findById(id)).thenReturn(Optional.of(task));
    var member=new ProjectMembership(UUID.randomUUID(),project,target,"MEMBER");member.setStatus("ACTIVE");
    when(members.findByProjectId(project)).thenReturn(List.of(member));
    var user=new AppUser(target,"an","hidden@example.test","hidden","An");user.setActive(true);user.setStatus("ACTIVE");
    when(users.findById(target)).thenReturn(Optional.of(user));
    when(jdbc.queryForObject(anyString(),eq(Boolean.class),eq(id),eq(target))).thenReturn(false);
    assertTrue(service.collaborators(actor,id).isEmpty());
  }
}
