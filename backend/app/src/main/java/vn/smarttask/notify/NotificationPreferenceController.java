package vn.smarttask.notify;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import vn.smarttask.common.*;
import vn.smarttask.security.CurrentActor;

@RestController
@RequestMapping("/api/v1/notifications/preferences")
public class NotificationPreferenceController {
  private final JdbcTemplate jdbc;
  public NotificationPreferenceController(JdbcTemplate jdbc){this.jdbc=jdbc;}
  public record Preference(String type,boolean muted){}
  @GetMapping
  public ApiResult<?> get(){return ApiResult.ok(jdbc.queryForList("select type,muted from notification_preference where user_id=? order by type",CurrentActor.userId()),"notification.preferences.success");}
  @PutMapping
  @Transactional
  public ApiResult<?> set(@RequestBody Preference body){
    if(body.type()==null||!body.type().matches("\\*|[A-Z][A-Z0-9_]{0,99}"))throw AppException.badRequest("INVALID_TYPE","Invalid notification type");
    jdbc.update("insert into notification_preference(id,user_id,type,muted) values (?,?,?,?) on conflict(user_id,type) do update set muted=excluded.muted",UuidV7.generate(),CurrentActor.userId(),body.type(),body.muted());
    return ApiResult.ok(body,"notification.preferences.success");
  }
}
