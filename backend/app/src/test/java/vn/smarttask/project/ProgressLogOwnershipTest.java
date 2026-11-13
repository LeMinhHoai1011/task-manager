package vn.smarttask.project;

import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import vn.smarttask.common.AppException;
import vn.smarttask.project.entity.*;
import vn.smarttask.project.repo.*;
import vn.smarttask.project.service.ProgressLogService;
import vn.smarttask.security.PolicyEnforcer;

class ProgressLogOwnershipTest {
  @Test void memberCanUpdateOwnLogButCannotUpdateOrDeleteAnotherMembersLog() {
    UUID actor=UUID.randomUUID(),project=UUID.randomUUID(),other=UUID.randomUUID(),id=UUID.randomUUID();
    var logs=mock(ProgressLogRepository.class);var projects=mock(ProjectRepository.class);
    var members=mock(ProjectMembershipRepository.class);var policy=mock(PolicyEnforcer.class);
    when(projects.existsById(project)).thenReturn(true);
    when(members.findByProjectIdAndUserId(project,actor)).thenReturn(Optional.of(new ProjectMembership(UUID.randomUUID(),project,actor,"MEMBER")));
    var service=new ProgressLogService(logs,projects,members,policy);
    var own=new ProgressLog(id,project,LocalDate.now(),10,"Own",actor);
    when(logs.findById(id)).thenReturn(Optional.of(own));
    var req=new ProgressLogService.UpsertProgressLogRequest(LocalDate.now(),20,"Updated",null,null,null);
    assertEquals("Updated",service.update(actor,project,id,req,1L).summary());
    verify(policy,never()).requireProjectWrite(actor,project);
    clearInvocations(logs);
    var foreign=new ProgressLog(id,project,LocalDate.now(),10,"Other",other);
    when(logs.findById(id)).thenReturn(Optional.of(foreign));
    doThrow(AppException.forbidden("Manager required")).when(policy).requireProjectWrite(actor,project);
    assertThrows(AppException.class,()->service.update(actor,project,id,req,1L));
    assertThrows(AppException.class,()->service.delete(actor,project,id));
    verify(logs,never()).save(any());verify(logs,never()).delete(any());
  }
}
