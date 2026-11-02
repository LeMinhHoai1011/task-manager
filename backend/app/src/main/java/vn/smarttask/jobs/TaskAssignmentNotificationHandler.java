package vn.smarttask.jobs;
import java.util.*;
import org.springframework.stereotype.Component;
import vn.smarttask.common.entity.OutboxEvent;
import vn.smarttask.common.AppException;
import vn.smarttask.task.repo.TaskRepository;
import vn.smarttask.security.PolicyEnforcer;
import vn.smarttask.notify.service.NotificationProcessor;
import vn.smarttask.realtime.RealtimePublisher;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class TaskAssignmentNotificationHandler implements OutboxHandler {
  private final TaskRepository tasks;
  private final PolicyEnforcer policy;
  private final NotificationProcessor notifications;
  private final ObjectMapper json;
  private final RealtimePublisher realtime;
  private final vn.smarttask.project.repo.ProjectMembershipRepository members;
  @org.springframework.beans.factory.annotation.Autowired private vn.smarttask.task.repo.TaskAssignmentRepository assignments;
  public TaskAssignmentNotificationHandler(TaskRepository tasks, PolicyEnforcer policy,
      NotificationProcessor notifications,ObjectMapper json,RealtimePublisher realtime,vn.smarttask.project.repo.ProjectMembershipRepository members) {
    this.tasks=tasks;this.policy=policy;this.notifications=notifications;this.json=json;this.realtime=realtime;this.members=members;
  }
  public List<String> prefixes() {return List.of("task.assigned","task.reassigned","task.deadline.","task.workflow.","task.approval.submitted","task.comment.added","task.reminder.fire");}
  public int order() {return 5;}
  public void handle(OutboxEvent event) {
    var task=tasks.findById(event.getAggregateId()).orElse(null);
    if(task==null || task.getDeletedAt()!=null) return;
    if((event.getType().equals("task.reminder.fire")||event.getType().startsWith("task.deadline.")) && java.util.Set.of("COMPLETED","CANCELLED").contains(task.getState()))return;
    try {
      var payload=json.readTree(event.getPayload());
      if(payload.hasNonNull("due_key")) {
        if(task.getDueAt()==null)return;
        var due=task.getDueAt().toInstant();
        var current=java.math.BigDecimal.valueOf(due.getEpochSecond()).add(java.math.BigDecimal.valueOf(due.getNano(),9));
        if(current.compareTo(new java.math.BigDecimal(payload.get("due_key").asText()))!=0)return;
      }
      Set<UUID> recipients=new LinkedHashSet<>();
      for(String key:List.of("user_id","previous_user_id"))
        if(payload.hasNonNull(key)) recipients.add(UUID.fromString(payload.get(key).asText()));
      if(!event.getType().equals("task.assigned")&&!event.getType().equals("task.reassigned")&&!event.getType().equals("task.reminder.fire")) {
        if(task.getProjectId()==null)recipients.add(task.getOwnerUserId());
        else {
          assignments.findByTaskIdAndStatus(task.getId(),"ACTIVE").forEach(a->recipients.add(a.getUserId()));
          members.findByProjectId(task.getProjectId()).stream().filter(m->"ACTIVE".equals(m.getStatus())&&Set.of("MANAGER","OWNER","ADMIN").contains(m.getRole())).forEach(m->recipients.add(m.getUserId()));
        }
      }
      for(UUID recipient:recipients) {
        if(task.getProjectId()!=null&&members.findMembership(task.getProjectId(),recipient).isEmpty()) continue;
        if(task.getProjectId()==null&&!recipient.equals(task.getOwnerUserId()))continue;
        try {if(task.getProjectId()!=null)policy.requireTaskRead(recipient,task.getId());}
        catch(AppException denied) { if(denied.getHttpStatus().value()==403 || denied.getHttpStatus().value()==404) continue; throw denied; }
        var message=json.createObjectNode();
        message.put("user_id",recipient.toString());message.put("type",event.getType().toUpperCase(Locale.ROOT).replace('.','_'));
        message.put("scope_type",task.getProjectId()==null?"PERSONAL":"PROJECT");message.put("scope_id",task.getProjectId()==null?task.getId().toString():task.getProjectId().toString());
        message.put("severity","NORMAL");message.put("title_key","notification."+event.getType());
        message.put("body_key","notification.task.event");message.put("deep_link",task.getProjectId()==null?"/my-tasks":"/tasks/"+task.getId());
        message.put("dedup_token","assignment:"+event.getId()+":"+recipient);
        var saved=notifications.deliver(notifications.parse(message.toString()));
        if(saved!=null&&saved.getDeferredUntil()==null) realtime.user(recipient,"NOTIFICATION_CREATED","USER",recipient,saved.getId());
      }
    } catch(com.fasterxml.jackson.core.JsonProcessingException e) {throw new IllegalArgumentException("Invalid task assignment outbox payload",e);}
  }
}
