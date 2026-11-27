package vn.smarttask.project;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.smarttask.common.ApiResult;
import vn.smarttask.project.service.ProjectChatService;
import vn.smarttask.security.CurrentActor;

@RestController
public class ProjectChatInboxController {
  private final ProjectChatService chat;

  public ProjectChatInboxController(ProjectChatService chat) { this.chat = chat; }

  @GetMapping("/api/v1/chat/unread")
  public ApiResult<?> unread() {
    return ApiResult.ok(chat.unreadProjects(CurrentActor.userId()), "chat.unread");
  }
}
