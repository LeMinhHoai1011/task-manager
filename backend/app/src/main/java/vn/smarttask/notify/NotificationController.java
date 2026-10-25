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

  public NotificationController(NotificationService notifications) {
    this.notifications = notifications;
  }

  @GetMapping
  @RequiresPermission("notification:read")
  public ResponseEntity<ApiResult<PageResponse<Object>>> list(
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false, name = "since_seq") Long sinceSeq,
      @RequestParam(required = false, defaultValue = "20") int limit) {
    UUID actor = CurrentActor.userId();
    int pageSize = Math.min(Math.max(limit, 1), 100);
    // Actor-scoped only: a user can never list another user's notifications
    // (policy = ownership; unknown ids read as 404 at markRead).
    var all = notifications.list(actor).stream()
        .filter(n -> sinceSeq == null || extractSeq(n) > sinceSeq)
        .filter(n -> !n.isRedacted())
        .toList();
    int start = 0;
    if (cursor != null && !cursor.isBlank()) {
      for (int i = 0; i < all.size(); i++) {
        if (all.get(i).getId().toString().equals(cursor)) { start = i + 1; break; }
      }
    }
    int end = Math.min(start + pageSize, all.size());
    var items = all.subList(start, end).stream()
        .map(n -> (Object) Map.of("id", n.getId().toString(),
            "kind", n.getKind(), "read", n.isRead())).toList();
    String next = end < all.size() ? all.get(end - 1).getId().toString() : null;
    return ResponseEntity.ok(ApiResult.ok(PageResponse.ofPage(items, next, next != null,
        pageSize, (long) all.size(), null, null),
        "notification.list.success"));
  }

  private static long extractSeq(vn.smarttask.notify.entity.Notification n) {
    return n.getOccurredAt() == null ? 0 : n.getOccurredAt().toEpochSecond();
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
