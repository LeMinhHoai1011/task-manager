package vn.smarttask.task.service;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.smarttask.common.AppException;

/** Shared SQL visibility for aggregate and calendar reads. */
@Service
public class TaskOverviewService {
  private final NamedParameterJdbcTemplate jdbc;
  public TaskOverviewService(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }
  private static final String VISIBLE = """
      t.deleted_at is null and (
        (t.scope='PERSONAL' and t.owner_user_id=:actor) or
        (t.scope='PROJECT' and exists (
          select 1 from project p join workspace w on w.id=p.workspace_id
          join project_membership m on m.project_id=p.id and m.user_id=:actor and m.status='ACTIVE'
          where p.id=t.project_id and p.status not in ('ARCHIVED','DELETED') and w.status='ACTIVE'
          and (m.role in ('OWNER','ADMIN','MANAGER') or (m.role='MEMBER' and exists(select 1 from global_permission_grant g where g.user_id=:actor and g.scope_type='PROJECT' and g.scope_id=p.id and g.code in ('TASK_METADATA_WRITE','TASK_ASSIGNMENT_WRITE') and g.revoked_at is null and (g.expires_at is null or g.expires_at>now()))) or exists (
            select 1 from task_assignment a where a.task_id=t.id and a.user_id=:actor and a.status='ACTIVE')))))
      """;
  private record Scope(String where, Map<String,Object> params) {}
  private Scope scope(UUID actor, String scope, UUID project, UUID team, OffsetDateTime from, OffsetDateTime to) {
    if (!Set.of("all","personal","project").contains(scope)) throw AppException.badRequest("INVALID_SCOPE","Unknown scope");
    var params=new HashMap<String,Object>(); params.put("actor",actor);
    String where=VISIBLE;
    if (!"all".equals(scope)) { where+=" and t.scope=:scope";params.put("scope",scope.toUpperCase(Locale.ROOT)); }
    if (project!=null) { where+=" and t.project_id=:project";params.put("project",project); }
    if (team!=null) { where+=" and exists(select 1 from task_team tt where tt.task_id=t.id and tt.team_id=:team)";params.put("team",team); }
    if (from!=null) { where+=" and t.due_at>=:from";params.put("from",from); }
    if (to!=null) { where+=" and t.due_at<:to";params.put("to",to); }
    if (from!=null && to!=null && !from.isBefore(to)) throw AppException.badRequest("INVALID_RANGE","from must precede to");
    return new Scope(where,params);
  }
  public static ZoneId zone(String timezone) {
    try {return ZoneId.of(timezone==null?"UTC":timezone);}
    catch (DateTimeException e) {throw AppException.badRequest("TIMEZONE_UNKNOWN","Unknown timezone");}
  }
  @Transactional(readOnly=true)
  public Map<String,Object> summary(UUID actor,String scope,UUID project,UUID team,OffsetDateTime from,OffsetDateTime to,String timezone) {
    var filter=scope(actor,scope,project,team,from,to); var p=filter.params();
    var now=OffsetDateTime.now(ZoneOffset.UTC);var today=now.atZoneSameInstant(zone(timezone)).toLocalDate();
    p.put("now",now);p.put("soon",now.plusDays(7));p.put("today",today.atStartOfDay(zone(timezone)).toOffsetDateTime());
    p.put("tomorrow",today.plusDays(1).atStartOfDay(zone(timezone)).toOffsetDateTime());
    var result=new LinkedHashMap<String,Object>(jdbc.queryForMap("""
      select count(*) total,
      count(*) filter(where t.state='TODO') todo,
      count(*) filter(where t.state='IN_PROGRESS') in_progress,
      count(*) filter(where t.state='PENDING_APPROVAL') pending_approval,
      count(*) filter(where t.state='COMPLETED') completed,
      count(*) filter(where t.state='CANCELLED') cancelled,
      count(*) filter(where t.state not in ('COMPLETED','CANCELLED') and t.due_at<:now) overdue,
      count(*) filter(where t.state not in ('COMPLETED','CANCELLED') and t.due_at>=:now and t.due_at<:soon) due_soon,
      count(*) filter(where t.state not in ('COMPLETED','CANCELLED') and t.due_at>=:today and t.due_at<:tomorrow) due_today
      from task t where
      """+filter.where(),p));
    long total=((Number)result.get("total")).longValue(), done=((Number)result.get("completed")).longValue();
    result.put("completion_rate",total==0?0.0:100.0*done/total);
    result.put("workload",jdbc.queryForList("select a.user_id,u.display_name,count(*) total,count(*) filter(where t.state not in ('COMPLETED','CANCELLED')) active from task t join task_assignment a on a.task_id=t.id and a.status='ACTIVE' join app_user u on u.id=a.user_id where "+filter.where()+" group by a.user_id,u.display_name order by active desc,a.user_id",p));
    result.put("focus",jdbc.queryForList("select t.id,t.title,t.state,t.scope,t.due_at,t.blocked_flag from task t where "+filter.where()+" and t.state not in ('COMPLETED','CANCELLED') order by (t.due_at<:now) desc nulls last,t.due_at asc nulls last,t.updated_at desc,t.id limit 6",p));
    return result;
  }
  @Transactional(readOnly=true)
  public List<Map<String,Object>> calendar(UUID actor,String view,LocalDate anchor,String timezone) {
    return calendar(actor,view,anchor,timezone,"all",null,null,0,200);
  }
  @Transactional(readOnly=true)
  public List<Map<String,Object>> calendar(UUID actor,String view,LocalDate anchor,String timezone,String selectedScope,UUID project,UUID team,int page,int size) {
    var zone=zone(timezone); var date=anchor==null?LocalDate.now(zone):anchor;
    LocalDate start,end;
    switch(view) {
      case "day" -> {start=date;end=date.plusDays(1);}
      case "week" -> {start=date.with(java.time.DayOfWeek.MONDAY);end=start.plusWeeks(1);}
      case "month" -> {start=date.withDayOfMonth(1);end=start.plusMonths(1);}
      case "unscheduled" -> {start=null;end=null;}
      default -> throw AppException.badRequest("CALENDAR_VIEW_UNKNOWN","Unknown view");
    }
    var f=scope(actor,selectedScope,project,team,start==null?null:start.atStartOfDay(zone).toOffsetDateTime(),end==null?null:end.atStartOfDay(zone).toOffsetDateTime());
    int limit=Math.max(1,Math.min(size,200));f.params().put("limit",limit);f.params().put("offset",(long)Math.max(page,0)*limit);
    return jdbc.queryForList("select t.id task_id,t.project_id,t.title,t.scope,t.due_at,t.due_timezone tz,t.due_is_date_only,t.state,t.version from task t where "+f.where()+(start==null?" and t.due_at is null":"")+" order by t.due_at nulls last,t.id limit :limit offset :offset",f.params());
  }
}
