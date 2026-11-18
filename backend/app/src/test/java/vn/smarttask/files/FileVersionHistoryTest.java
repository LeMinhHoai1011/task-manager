package vn.smarttask.files;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.smarttask.common.AppException;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.files.entity.Attachment;
import vn.smarttask.files.repo.AttachmentRepository;
import vn.smarttask.files.repo.FileAccessEventRepository;
import vn.smarttask.files.service.FileService;
import vn.smarttask.security.PolicyEnforcer;
import vn.smarttask.security.Principal;
import vn.smarttask.task.repo.TaskRepository;

@ExtendWith(MockitoExtension.class)
class FileVersionHistoryTest {
  @Mock FileService files;
  @Mock AttachmentRepository attachments;
  @Mock TaskRepository tasks;
  @Mock PolicyEnforcer policy;
  @Mock FileAccessEventRepository accessEvents;
  @Mock AuditService audit;
  @InjectMocks FileController controller;
  final UUID actor=UUID.randomUUID(), id=UUID.randomUUID(), taskId=UUID.randomUUID();

  @BeforeEach void authenticate() {
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
        new Principal(actor, UUID.randomUUID()), null, List.of()));
  }
  @AfterEach void clear() { SecurityContextHolder.clearContext(); }

  @Test void historyRequiresTaskReadAndReturnsOnlyDisplayFields() {
    var attachment=mock(Attachment.class);
    when(attachment.getTaskId()).thenReturn(taskId);
    when(attachments.findById(id)).thenReturn(Optional.of(attachment));
    when(tasks.findById(taskId)).thenReturn(Optional.of(mock(vn.smarttask.task.entity.Task.class)));
    when(files.history(id)).thenReturn(List.of(new FileService.VersionInfo(1,"Bản đầu",null,128)));
    var response=controller.versions(id).getBody().data();
    assertEquals(1,response.get(0).version());
    var json=new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(response.get(0));
    assertFalse(json.has("objectKey"));
    assertFalse(json.has("createdBy"));
    verify(policy).requireTaskRead(actor,taskId);
  }

  @Test void outsiderCannotReadVersionHistory() {
    var attachment=mock(Attachment.class);
    when(attachment.getTaskId()).thenReturn(taskId);
    when(attachments.findById(id)).thenReturn(Optional.of(attachment));
    doThrow(AppException.notFound("Task not found")).when(policy).requireTaskRead(actor,taskId);
    assertThrows(AppException.class,()->controller.versions(id));
    verifyNoInteractions(files);
  }

  @Test void deletedAttachmentHasNoVisibleHistory() {
    var attachment=mock(Attachment.class);
    when(attachment.getDeletedAt()).thenReturn(java.time.OffsetDateTime.now());
    when(attachments.findById(id)).thenReturn(Optional.of(attachment));
    assertThrows(AppException.class,()->controller.versions(id));
    verifyNoInteractions(files,policy);
  }
}
