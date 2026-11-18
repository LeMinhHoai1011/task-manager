package vn.smarttask.project.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.smarttask.common.AppException;
import vn.smarttask.common.UuidV7;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.common.service.TokenHash;
import vn.smarttask.realtime.RealtimePublisher;
import vn.smarttask.security.PolicyEnforcer;
import vn.smarttask.task.repo.TaskRepository;

@Service
public class ProjectChatService {
  private final JdbcTemplate jdbc;
  private final PolicyEnforcer policy;
  private final TaskRepository tasks;
  private final RealtimePublisher realtime;
  private final AuditService audit;
  private static final Set<String> ROLES=Set.of("OWNER","ADMIN","MANAGER","MEMBER");

  public ProjectChatService(JdbcTemplate jdbc, PolicyEnforcer policy, TaskRepository tasks,
      RealtimePublisher realtime, AuditService audit) {
    this.jdbc=jdbc;this.policy=policy;this.tasks=tasks;this.realtime=realtime;this.audit=audit;
  }
  public record Access(boolean read, boolean write, boolean moderate, String reason) {}
  public record Send(UUID clientMessageId, String body, UUID linkedTaskId) {}
  public record TaskLink(UUID id, String title, String state) {}
  public record Message(UUID id, long seq, @JsonProperty("changed_seq") long changedSeq,
      @JsonProperty("sender_id") UUID senderId, @JsonProperty("sender_name") String senderName,
      @JsonProperty("sender_username") String senderUsername,
      @JsonProperty("client_message_id") UUID clientMessageId, String body,
      @JsonProperty("linked_task") TaskLink linkedTask,
      @JsonProperty("task_unavailable") boolean taskUnavailable,
      @JsonProperty("created_at") OffsetDateTime createdAt, boolean deleted) {}
  public record Page(List<Message> items, long revision,
      @JsonProperty("next_before") Long nextBefore, @JsonProperty("next_after") Long nextAfter,
      @JsonProperty("has_more") boolean hasMore, Access access, Unread unread) {}
  public record Unread(long count, @JsonProperty("last_read_seq") long lastReadSeq) {}
  private record Row(UUID id,long seq,long changedSeq,UUID senderId,String name,String username,
      UUID clientId,String hash,String body,UUID taskId,OffsetDateTime createdAt,OffsetDateTime deletedAt) {}
  private static final String SELECT="select m.*,u.display_name,u.username from project_message m join app_user u on u.id=m.sender_user_id ";

  /** Chat is project-wide: a task guest or a global administrator does not inherit access. */
  private Access access(UUID actor,UUID project,boolean lock) {
    var rows=jdbc.query("""
        select m.role,p.status project_status,w.status workspace_status
        from project p join workspace w on w.id=p.workspace_id
        join project_membership m on m.project_id=p.id and m.user_id=?
        join app_user u on u.id=m.user_id
        where p.id=? and m.status='ACTIVE' and u.active=true and u.status='ACTIVE'
          and u.locked=false and (u.locked_until is null or u.locked_until<=clock_timestamp())
          and u.deleted_at is null and p.deleted_at is null and w.deleted_at is null
          and p.status<>'DELETED' and w.status<>'DELETED'
        """+(lock?" for share of p,w,m,u":""),
        (rs,n)->new String[]{rs.getString("role"),rs.getString("project_status"),rs.getString("workspace_status")},actor,project);
    if(rows.isEmpty()||!ROLES.contains(rows.get(0)[0]))throw AppException.notFound("Project chat not found");
    var row=rows.get(0);
    boolean writable=Set.of("PLANNING","ACTIVE").contains(row[1])&&"ACTIVE".equals(row[2]);
    return new Access(true,writable,!"MEMBER".equals(row[0]),writable?null:"PROJECT_READ_ONLY");
  }
  @Transactional(readOnly=true)
  public Access access(UUID actor,UUID project) { return access(actor,project,false); }
  private void writable(Access access) {
    if(!access.write())throw AppException.stateConflict("PROJECT_READ_ONLY","Project chat is read only");
  }
  private long revision(UUID project) {
    var rows=jdbc.queryForList("select last_revision from project_chat_state where project_id=?",Long.class,project);
    return rows.isEmpty()?0:rows.get(0);
  }
  /** Serializes message creation/deletion and their revisions in commit order. */
  private void lock(UUID project) {
    jdbc.update("insert into project_chat_state(project_id) values(?) on conflict do nothing",project);
    jdbc.queryForObject("select last_revision from project_chat_state where project_id=? for update",Long.class,project);
  }
  private long next(UUID project) {
    return jdbc.queryForObject("update project_chat_state set last_revision=last_revision+1 where project_id=? returning last_revision",Long.class,project);
  }
  static String text(Send request) {
    if(request==null||request.clientMessageId()==null||request.body()==null)throw AppException.badRequest("CHAT_INVALID","Message identity and body required");
    String body=request.body().replace("\r\n","\n").replace('\r','\n').strip();
    if(body.isBlank()||body.indexOf('\0')>=0||body.codePointCount(0,body.length())>4000
        ||body.codePoints().anyMatch(cp->cp>=0xD800&&cp<=0xDFFF))
      throw AppException.badRequest("CHAT_INVALID","Message must contain 1-4000 characters");
    return body;
  }
  private Row row(ResultSet rs,int index) throws SQLException {
    return new Row(rs.getObject("id",UUID.class),rs.getLong("seq"),rs.getLong("changed_seq"),
        rs.getObject("sender_user_id",UUID.class),rs.getString("display_name"),rs.getString("username"),
        rs.getObject("client_message_id",UUID.class),rs.getString("request_hash"),rs.getString("body"),
        rs.getObject("linked_task_id",UUID.class),rs.getObject("created_at",OffsetDateTime.class),rs.getObject("deleted_at",OffsetDateTime.class));
  }
  private Row get(UUID project,UUID id) {
    return jdbc.query(SELECT+"where m.project_id=? and m.id=?",this::row,project,id).stream()
        .findFirst().orElseThrow(()->AppException.notFound("Message not found"));
  }
  private List<Message> display(UUID actor,List<Row> rows) {
    var ids=rows.stream().filter(r->r.deletedAt()==null&&r.taskId()!=null).map(Row::taskId).toList();
    Map<UUID,TaskLink> links=new HashMap<>();
    if(!ids.isEmpty())for(var task:tasks.findAll(policy.readableTasks(actor)
        .and((root,q,cb)->root.get("id").in(ids))))
      links.put(task.getId(),new TaskLink(task.getId(),task.getTitle(),task.getState()));
    return rows.stream().map(r->new Message(r.id(),r.seq(),r.changedSeq(),r.senderId(),r.name(),r.username(),
        r.clientId(),r.deletedAt()!=null?null:r.body(),r.deletedAt()!=null?null:links.get(r.taskId()),
        r.deletedAt()==null&&r.taskId()!=null&&!links.containsKey(r.taskId()),r.createdAt(),r.deletedAt()!=null)).toList();
  }
  private void signal(UUID project,String kind,UUID object) {
    // Reuse the existing post-commit publisher; never put text or task titles in signals.
    var recipients=jdbc.queryForList("select user_id from project_membership where project_id=? and status='ACTIVE' and role in ('OWNER','ADMIN','MANAGER','MEMBER')",UUID.class,project);
    for(var user:recipients)realtime.user(user,kind,"CHAT",project,object);
  }
  @Transactional
  public Message send(UUID actor,UUID project,Send request) {
    String body=text(request);
    writable(access(actor,project,true));
    lock(project);
    String hash=TokenHash.sha256(body+"\0"+request.linkedTaskId());
    var previous=jdbc.query(SELECT+"where m.project_id=? and m.sender_user_id=? and m.client_message_id=?",this::row,project,actor,request.clientMessageId());
    if(!previous.isEmpty()) {
      if(!hash.equals(previous.get(0).hash()))throw AppException.stateConflict("IDEMPOTENCY_CONFLICT","Message identity reused with different content");
      return display(actor,previous).get(0);
    }
    long recent=jdbc.queryForObject("select count(*) from project_message where project_id=? and sender_user_id=? and created_at>clock_timestamp()-interval '1 minute'",Long.class,project,actor);
    if(recent>=30)throw new AppException("RATE_LIMITED","error.rate_limited","CHAT_RATE_LIMITED",HttpStatus.TOO_MANY_REQUESTS,"Too many messages").withRetryAfterSeconds(60);
    if(request.linkedTaskId()!=null) {
      policy.requireTaskRead(actor,request.linkedTaskId());
      var task=tasks.findById(request.linkedTaskId()).orElseThrow(()->AppException.notFound("Task not found"));
      if(!project.equals(task.getProjectId()))throw AppException.badRequest("CHAT_TASK_SCOPE","Task belongs to another project");
    }
    long seq=next(project);UUID id=UuidV7.generate();
    jdbc.update("insert into project_message(id,project_id,seq,changed_seq,sender_user_id,client_message_id,request_hash,body,linked_task_id) values(?,?,?,?,?,?,?,?,?)",id,project,seq,seq,actor,request.clientMessageId(),hash,body,request.linkedTaskId());
    audit.append(actor,"chat.send","project",project,"ALLOWED",null,"{}");
    signal(project,"CHAT_MESSAGE_CREATED",id);
    return display(actor,List.of(get(project,id))).get(0);
  }
  @Transactional(readOnly=true)
  public Page list(UUID actor,UUID project,Long before,Long after,Long until,int limit) {
    var permission=access(actor,project,false);
    if(limit<1||limit>100||before!=null&&before<1||after!=null&&after<0||before!=null&&after!=null||until!=null&&(until<0||after==null))
      throw AppException.badRequest("CHAT_CURSOR_INVALID","Invalid message page");
    long snapshot=revision(project);
    if(until!=null) { if(until>snapshot||until<after)throw AppException.badRequest("CHAT_CURSOR_INVALID","Invalid snapshot");snapshot=until; }
    if(after!=null&&after>snapshot)throw AppException.badRequest("CHAT_CURSOR_INVALID","Cursor exceeds project history");
    List<Row> rows;
    if(after!=null)rows=jdbc.query(SELECT+"where m.project_id=? and m.changed_seq>? and m.changed_seq<=? order by m.changed_seq asc limit ?",this::row,project,after,snapshot,limit+1);
    else rows=jdbc.query(SELECT+"where m.project_id=? and m.seq<? and m.changed_seq<=? order by m.seq desc limit ?",this::row,project,before==null?Long.MAX_VALUE:before,snapshot,limit+1);
    boolean more=rows.size()>limit;
    rows=new ArrayList<>(rows.subList(0,Math.min(limit,rows.size())));
    Long nextBefore=after==null&&more?rows.get(rows.size()-1).seq():null;
    Long nextAfter=after!=null?(more?rows.get(rows.size()-1).changedSeq():snapshot):null;
    if(after==null)Collections.reverse(rows);
    return new Page(display(actor,rows),snapshot,nextBefore,nextAfter,more,permission,unreadCounts(actor,project));
  }
  @Transactional
  public Message delete(UUID actor,UUID project,UUID id,long version,String reason) {
    var permission=access(actor,project,true);writable(permission);lock(project);
    var message=get(project,id);
    if(!actor.equals(message.senderId())&&(!permission.moderate()||reason==null||reason.strip().length()<3))
      throw AppException.forbidden("Only author or project manager with a reason may delete");
    if(reason!=null&&reason.length()>500)throw AppException.badRequest("CHAT_INVALID","Reason too long");
    if(message.deletedAt()!=null)return display(actor,List.of(message)).get(0);
    if(version!=message.changedSeq())throw AppException.versionConflict("Message changed",message.changedSeq());
    long change=next(project);
    jdbc.update("update project_message set changed_seq=?,deleted_at=clock_timestamp(),deleted_by=?,delete_reason=? where id=? and project_id=?",change,actor,reason==null?null:reason.strip(),id,project);
    audit.append(actor,"chat.delete","project",project,"ALLOWED",actor.equals(message.senderId())?"AUTHOR":"MANAGER", "{}");
    signal(project,"CHAT_MESSAGE_DELETED",id);
    return display(actor,List.of(get(project,id))).get(0);
  }
  @Transactional(readOnly=true)
  public Unread unread(UUID actor,UUID project) {
    access(actor,project,false);
    return unreadCounts(actor,project);
  }
  private Unread unreadCounts(UUID actor,UUID project) {
    long seen=jdbc.queryForObject("select coalesce((select last_read_seq from project_message_read where project_id=? and user_id=?),0)",Long.class,project,actor);
    long count=jdbc.queryForObject("select count(*) from project_message where project_id=? and seq>? and sender_user_id<>? and deleted_at is null",Long.class,project,seen,actor);
    return new Unread(count,seen);
  }
  @Transactional
  public Unread read(UUID actor,UUID project,long seen) {
    access(actor,project,true);
    if(seen<0||seen>0&&!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from project_message where project_id=? and seq=?)",Boolean.class,project,seen)))
      throw AppException.badRequest("CHAT_CURSOR_INVALID","Read marker must refer to a message in this project");
    jdbc.update("insert into project_message_read(project_id,user_id,last_read_seq) values(?,?,?) on conflict(project_id,user_id) do update set last_read_seq=greatest(project_message_read.last_read_seq,excluded.last_read_seq),updated_at=clock_timestamp()",project,actor,seen);
    realtime.user(actor,"CHAT_READ_UPDATED","CHAT",project,project);
    return unreadCounts(actor,project);
  }
}
