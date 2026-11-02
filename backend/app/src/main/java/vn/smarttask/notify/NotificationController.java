package vn.smarttask.notify;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.smarttask.common.ApiResult;
import vn.smarttask.common.PageResponse;
import vn.smarttask.notify.service.NotificationService;
import vn.smarttask.security.CurrentActor;
import vn.smarttask.security.RequiresPermission;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

  private final NotificationService notifications;
  private final vn.smarttask.notify.repo.NotificationRepository repository;
  private final vn.smarttask.security.PolicyEnforcer policy;

  public NotificationController(NotificationService notifications, vn.smarttask.notify.repo.NotificationRepository repository, vn.smarttask.security.PolicyEnforcer policy) {
    this.notifications = notifications;
    this.repository=repository; this.policy=policy;
  }
  @GetMapping("/{id}/target")
  public ApiResult<Object> target(@PathVariable UUID id) {
    UUID actor=CurrentActor.userId();
    var notification=repository.findById(id).filter(n->actor.equals(n.getUserId()) && !n.isRedacted())
        .orElseThrow(()->vn.smarttask.common.AppException.notFound("Notification not found"));
    String link=notification.getDeepLink();
    if("PERSONAL".equals(notification.getScopeType())&&"/my-tasks".equals(link)&&notification.getScopeId()!=null) {
      return ApiResult.ok(Map.of("path","/my-tasks"),"notification.target.success");
    }
    if(link==null || !link.matches("/tasks/[0-9a-fA-F-]{36}"))throw vn.smarttask.common.AppException.notFound("Target unavailable");
    UUID taskId=UUID.fromString(link.substring("/tasks/".length()));
    if(notification.getScopeId()==null || !"PROJECT".equals(notification.getScopeType()))throw vn.smarttask.common.AppException.notFound("Target unavailable");
    policy.requireProjectRead(actor,notification.getScopeId());
    policy.requireTaskRead(actor,taskId);
    return ApiResult.ok(Map.of("path",link),"notification.target.success");
  }

  @GetMapping
  @RequiresPermission("notification:read")
  public ResponseEntity<ApiResult<PageResponse<Object>>> list(
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false, name = "since_seq") Long sinceSeq,
      @RequestParam(defaultValue = "all") String filter,
      @RequestParam(required = false, defaultValue = "20") int limit) {
    UUID actor = CurrentActor.userId();
    int pageSize = Math.min(Math.max(limit, 1), 100);
    var page = notifications.inbox(actor, cursor, sinceSeq, pageSize, filter);
    var items = page.items().stream()
        .map(n -> (Object) Map.of("id", n.getId().toString(),
            "kind", n.getKind(), "read", n.isRead(), "seq", n.getSeq())).toList();
    String next = page.next();
    return ResponseEntity.ok(ApiResult.ok(PageResponse.ofPage(items, next, next != null,
        pageSize, page.total(), null, null),
        "notification.list.success"));
  }

  @GetMapping("/unread-count")
  @RequiresPermission("notification:read")
  public ApiResult<Object> unreadCount() {
    return ApiResult.ok(Map.of("count", notifications.unreadCount(CurrentActor.userId())), "notification.unread.success");
  }

  @PostMapping("/{id}/read")
  @RequiresPermission("notification:write")
  public ResponseEntity<ApiResult<Object>> markRead(@PathVariable UUID id) {
    UUID actor = CurrentActor.userId();
    notifications.markRead(actor, id);
    return ResponseEntity.ok(ApiResult.ok(null, "notification.read.success"));
  }

  @PostMapping("/read-all")
  @RequiresPermission("notification:write")
  public ResponseEntity<ApiResult<Object>> markAllRead() {
    UUID actor = CurrentActor.userId();
    int count = notifications.markAllRead(actor);
    return ResponseEntity.ok(ApiResult.ok(Map.of("marked", count),
        "notification.read_all.success"));
  }
}
