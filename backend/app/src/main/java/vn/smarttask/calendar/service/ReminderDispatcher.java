package vn.smarttask.calendar.service;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.smarttask.common.service.OutboxService;

/** Database locks and the existing outbox make scheduled reminders restart-safe. */
@Service
public class ReminderDispatcher {
  private final JdbcTemplate jdbc;private final OutboxService outbox;
  public ReminderDispatcher(JdbcTemplate jdbc,OutboxService outbox){this.jdbc=jdbc;this.outbox=outbox;}
  @Transactional
  public int dispatch() {
    if(!Boolean.TRUE.equals(jdbc.queryForObject("select pg_try_advisory_xact_lock(83640912)",Boolean.class)))return 0;
    int count=0;
    var rules=jdbc.queryForList("select id,task_id,user_id from reminder_rule where status='ACTIVE' and not paused and remind_at<=now() order by remind_at,id limit 100 for update skip locked");
    for(var rule:rules) {
      UUID id=(UUID)rule.get("id"),task=(UUID)rule.get("task_id");
      outbox.emit("task",task,"task.reminder.fire","{\"user_id\":\""+rule.get("user_id")+"\",\"rule_id\":\""+id+"\"}");
      jdbc.update("update reminder_rule set status='FIRED' where id=?",id);count++;
    }
    for(String kind:java.util.List.of("soon","overdue")) {
      String timing="soon".equals(kind)?"t.due_at>now() and t.due_at<=now()+interval '24 hours'":"t.due_at<=now()";
      String type="task.deadline."+kind;
      var due=jdbc.queryForList("select t.id,extract(epoch from t.due_at)::text due_key from task t where t.deleted_at is null and t.state not in ('COMPLETED','CANCELLED') and "+timing+" and not exists(select 1 from outbox_event e where e.aggregate_id=t.id and e.type=? and e.payload->>'due_key'=extract(epoch from t.due_at)::text) order by t.due_at,t.id limit 100 for update of t skip locked",type);
      for(var task:due){outbox.emit("task",(UUID)task.get("id"),type,"{\"due_key\":\""+task.get("due_key")+"\"}");count++;}
    }
    return count;
  }
}
