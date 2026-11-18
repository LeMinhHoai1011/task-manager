package vn.smarttask.project.service;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.smarttask.common.AppException;

class ProjectChatValidationTest {
  @Test void normalizesLineBreaksAndCountsUnicodeCharacters() {
    var id=UUID.randomUUID();
    assertEquals("Xin chào\nDự án",ProjectChatService.text(new ProjectChatService.Send(id,"  Xin chào\r\nDự án  ",null)));
    assertEquals(8000,ProjectChatService.text(new ProjectChatService.Send(id,"😀".repeat(4000),null)).length());
    assertThrows(AppException.class,()->ProjectChatService.text(new ProjectChatService.Send(id,"😀".repeat(4001),null)));
  }
  @Test void rejectsEmptyAndInvalidInput() {
    for(String body:new String[]{""," \n\t","\0","\uD800","x".repeat(4001)})
      assertThrows(AppException.class,()->ProjectChatService.text(new ProjectChatService.Send(UUID.randomUUID(),body,null)));
    assertThrows(AppException.class,()->ProjectChatService.text(new ProjectChatService.Send(null,"Tin nhắn",null)));
  }
}
