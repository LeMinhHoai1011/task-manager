package vn.smarttask.project;

import java.util.*;
import org.springframework.web.bind.annotation.*;
import vn.smarttask.common.ApiResult;
import vn.smarttask.common.AppException;
import vn.smarttask.project.service.ProjectChatService;
import vn.smarttask.security.CurrentActor;

@RestController
@RequestMapping("/api/v1/projects/{projectId}")
public class ProjectChatController {
  private final ProjectChatService chat;
  public ProjectChatController(ProjectChatService chat) { this.chat=chat; }
  private void fields(Map<String,Object> body,String... allowed) {
    if(body==null||!Set.of(allowed).containsAll(body.keySet()))throw AppException.badRequest("CHAT_INVALID","Unexpected request fields");
  }
  private UUID uuid(Object value,boolean required) {
    if(value==null&&!required)return null;
    try { if(!(value instanceof String s)||s.length()!=36)throw new IllegalArgumentException();return UUID.fromString(s); }
    catch(IllegalArgumentException e) {throw AppException.badRequest("CHAT_INVALID","Invalid message identity");}
  }
  private long number(Object value) {
    try{return Long.parseLong(String.valueOf(value));}
    catch(NumberFormatException e){throw AppException.badRequest("CHAT_INVALID","Invalid sequence");}
  }
  @GetMapping("/chat/access")
  public ApiResult<?> access(@PathVariable UUID projectId) {return ApiResult.ok(chat.access(CurrentActor.userId(),projectId),"chat.access");}
  @GetMapping("/messages")
  public ApiResult<?> list(@PathVariable UUID projectId,@RequestParam(required=false) Long before,
      @RequestParam(required=false) Long after,@RequestParam(required=false) Long until,
      @RequestParam(defaultValue="50") int limit) {
    return ApiResult.ok(chat.list(CurrentActor.userId(),projectId,before,after,until,limit),"chat.messages");
  }
  @PostMapping("/messages")
  public ApiResult<?> send(@PathVariable UUID projectId,@RequestBody Map<String,Object> body) {
    fields(body,"client_message_id","body","linked_task_id","reply_to_id");
    if(!(body.get("body") instanceof String text))throw AppException.badRequest("CHAT_INVALID","Text required");
    return ApiResult.ok(chat.send(CurrentActor.userId(),projectId,new ProjectChatService.Send(
        uuid(body.get("client_message_id"),true),text,uuid(body.get("linked_task_id"),false),
        uuid(body.get("reply_to_id"),false))),"chat.sent");
  }
  @GetMapping("/messages/{messageId}")
  public ApiResult<?> message(@PathVariable UUID projectId,@PathVariable UUID messageId) {
    return ApiResult.ok(chat.message(CurrentActor.userId(),projectId,messageId),"chat.messages");
  }
  @GetMapping("/chat/search")
  public ApiResult<?> search(@PathVariable UUID projectId,@RequestParam String q,@RequestParam(required=false) Long before) {
    return ApiResult.ok(chat.search(CurrentActor.userId(),projectId,q,before),"chat.messages");
  }
  @GetMapping("/chat/pinned")
  public ApiResult<?> pinned(@PathVariable UUID projectId) {
    return ApiResult.ok(chat.pinned(CurrentActor.userId(),projectId).map(List::of).orElseGet(List::of),"chat.messages");
  }
  @PutMapping("/messages/{messageId}/pin")
  public ApiResult<?> pin(@PathVariable UUID projectId,@PathVariable UUID messageId,@RequestBody Map<String,Object> body) {
    fields(body,"pinned");
    if(!(body.get("pinned") instanceof Boolean pinned))throw AppException.badRequest("CHAT_INVALID","Pin state required");
    chat.pin(CurrentActor.userId(),projectId,messageId,pinned);
    return ApiResult.ok(null,"chat.messages");
  }
  @DeleteMapping("/messages/{messageId}")
  public ApiResult<?> delete(@PathVariable UUID projectId,@PathVariable UUID messageId,
      @RequestHeader(value="If-Match",required=false) String version,@RequestBody Map<String,Object> body) {
    fields(body,"reason");
    if(version==null)throw AppException.ifMatchRequired();
    if(body.get("reason")!=null&&!(body.get("reason") instanceof String))throw AppException.badRequest("CHAT_INVALID","Reason must be text");
    return ApiResult.ok(chat.delete(CurrentActor.userId(),projectId,messageId,number(version),(String)body.get("reason")),"chat.deleted");
  }
  @GetMapping("/chat/unread")
  public ApiResult<?> unread(@PathVariable UUID projectId) {return ApiResult.ok(chat.unread(CurrentActor.userId(),projectId),"chat.unread");}
  @PutMapping("/chat/read")
  public ApiResult<?> read(@PathVariable UUID projectId,@RequestBody Map<String,Object> body) {
    fields(body,"last_read_seq");
    return ApiResult.ok(chat.read(CurrentActor.userId(),projectId,number(body.get("last_read_seq"))),"chat.read");
  }
}
