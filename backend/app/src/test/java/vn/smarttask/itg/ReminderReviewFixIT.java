package vn.smarttask.itg;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

class ReminderReviewFixIT extends ItgBase {
  @Autowired vn.smarttask.calendar.service.CalendarService calendar;
  @Autowired vn.smarttask.task.service.PersonalTaskService personal;
  @Autowired vn.smarttask.task.service.TaskOverviewService overview;
  @Autowired org.springframework.transaction.PlatformTransactionManager transactions;

  @Test void cancelWinsAgainstAnAlreadyStartedPause() throws Exception {
    var owner=activeUser("pauseowner","Password123!");var task=personal.create(owner.getId(),"Reminder race");
    var rule=calendar.createRule(owner.getId(),task.getId(),OffsetDateTime.now().plusDays(2));
    var pool=Executors.newSingleThreadExecutor();var pid=new CompletableFuture<Integer>();
    var pending=new java.util.concurrent.atomic.AtomicReference<Future<?>>();
    try {
      new TransactionTemplate(transactions).execute(status->{
        jdbc.queryForObject("select id from reminder_rule where id=? for update",UUID.class,rule.getId());
        pending.set(pool.submit(()->new TransactionTemplate(transactions).execute(s->{
          pid.complete(jdbc.queryForObject("select pg_backend_pid()",Integer.class));
          return calendar.pause(owner.getId(),rule.getId());
        })));
        awaitBlocked(pid);
        jdbc.update("update reminder_rule set status='CANCELLED',paused=false where id=?",rule.getId());return null;
      });
      var error=assertThrows(ExecutionException.class,()->pending.get().get(10,TimeUnit.SECONDS));
      assertInstanceOf(vn.smarttask.common.AppException.class,error.getCause());
      assertEquals("CANCELLED",jdbc.queryForObject("select status from reminder_rule where id=?",String.class,rule.getId()));
    }finally{pool.shutdownNow();}
  }

  @Test void resumeDigestCannotOverwriteAnotherCancelledReminder() throws Exception {
    var owner=activeUser("resumeowner","Password123!");var task=personal.create(owner.getId(),"Digest race");
    UUID first=UUID.randomUUID(),second=UUID.randomUUID();
    for(UUID id:List.of(first,second))jdbc.update("insert into reminder_rule(id,task_id,user_id,remind_at,paused,status) values (?,?,?,?,true,'PAUSED')",id,task.getId(),owner.getId(),OffsetDateTime.now().minusHours(1));
    var pool=Executors.newSingleThreadExecutor();var pid=new CompletableFuture<Integer>();
    var pending=new java.util.concurrent.atomic.AtomicReference<Future<Integer>>();
    try {
      new TransactionTemplate(transactions).execute(status->{
        jdbc.queryForObject("select id from reminder_rule where id=? for update",UUID.class,second);
        pending.set(pool.submit(()->new TransactionTemplate(transactions).execute(s->{pid.complete(jdbc.queryForObject("select pg_backend_pid()",Integer.class));return calendar.resume(owner.getId(),first);})));
        awaitBlocked(pid);
        jdbc.update("update reminder_rule set status='CANCELLED',paused=false where id=?",second);return null;
      });
      assertEquals(1,pending.get().get(10,TimeUnit.SECONDS));
      assertEquals("CANCELLED",jdbc.queryForObject("select status from reminder_rule where id=?",String.class,second));
      assertEquals("FIRED",jdbc.queryForObject("select status from reminder_rule where id=?",String.class,first));
    }finally{pool.shutdownNow();}
  }

  private void awaitBlocked(CompletableFuture<Integer> pid){
    try {
      int backendPid=pid.get(5,TimeUnit.SECONDS);long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
      while(System.nanoTime()<end){
        jdbc.execute("select pg_stat_clear_snapshot()");
        if(Boolean.TRUE.equals(jdbc.queryForObject("select wait_event_type='Lock' from pg_stat_activity where pid=?",Boolean.class,backendPid)))return;
        Thread.sleep(20);
      }
      fail("Reminder mutation did not wait for the row lock");
    }catch(Exception e){throw new RuntimeException(e);}
  }

  @Test void dateOnlyCalendarUsesOriginalDateAtDayWeekAndMonthBoundaries(){
    var owner=activeUser("dateowner","Password123!");
    var previous=personal.create(owner.getId(),"October all-day");var next=personal.create(owner.getId(),"November all-day");var timed=personal.create(owner.getId(),"November timed");
    jdbc.update("update task set due_at=?,due_timezone='UTC',due_is_date_only=true where id=?",OffsetDateTime.parse("2026-10-31T23:59:59.999999Z"),previous.getId());
    jdbc.update("update task set due_at=?,due_timezone='UTC',due_is_date_only=true where id=?",OffsetDateTime.parse("2026-11-01T23:59:59.999999Z"),next.getId());
    jdbc.update("update task set due_at=?,due_timezone='UTC',due_is_date_only=false where id=?",OffsetDateTime.parse("2026-11-01T01:00:00Z"),timed.getId());
    for(String view:List.of("day","month")){
      var rows=overview.calendar(owner.getId(),view,LocalDate.parse("2026-11-01"),"Asia/Bangkok");
      assertFalse(rows.stream().anyMatch(row->previous.getId().equals(row.get("task_id"))));
      assertTrue(rows.stream().anyMatch(row->next.getId().equals(row.get("task_id"))&&"2026-11-01".equals(row.get("due_date_only"))));
      assertTrue(rows.stream().anyMatch(row->timed.getId().equals(row.get("task_id"))));
    }
    var following=overview.calendar(owner.getId(),"week",LocalDate.parse("2026-11-02"),"Asia/Bangkok");
    assertFalse(following.stream().anyMatch(row->next.getId().equals(row.get("task_id"))));
    var prior=overview.calendar(owner.getId(),"day",LocalDate.parse("2026-10-31"),"Asia/Bangkok");assertEquals(previous.getId(),prior.get(0).get("task_id"));
  }
}
