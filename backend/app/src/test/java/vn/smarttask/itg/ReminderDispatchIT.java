package vn.smarttask.itg;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.smarttask.calendar.service.ReminderDispatcher;
import vn.smarttask.jobs.TaskAssignmentNotificationHandler;
import vn.smarttask.common.repo.OutboxEventRepository;
import static org.junit.jupiter.api.Assertions.*;

class ReminderDispatchIT extends ItgBase {
  @Autowired vn.smarttask.calendar.service.CalendarService calendar;
  @Autowired ReminderDispatcher dispatcher;
  @Autowired TaskAssignmentNotificationHandler handler;
  @Autowired OutboxEventRepository events;
  @Test void concurrentResumesCollapseMissedRulesIntoOneOutboxDigest() throws Exception {
    var owner=activeUser("digestowner","Password123!");var ws=mkWorkspace(owner.getId(),"digest");var project=mkActiveProject(owner.getId(),ws,"digest");var task=mkTask(owner.getId(),project.getId(),"Missed reminders");
    var first=UUID.randomUUID();var second=UUID.randomUUID();
    for(var rule:java.util.List.of(first,second))jdbc.update("insert into reminder_rule(id,task_id,user_id,remind_at,paused,status) values (?,?,?,?,true,'PAUSED')",rule,task.getId(),owner.getId(),OffsetDateTime.now().minusHours(1));
    var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var gate=new java.util.concurrent.CountDownLatch(1);
    try {
      var a=pool.submit(()->{gate.await();return calendar.resume(owner.getId(),first);});var b=pool.submit(()->{gate.await();return calendar.resume(owner.getId(),second);});gate.countDown();
      assertEquals(1,a.get(15,java.util.concurrent.TimeUnit.SECONDS)+b.get(15,java.util.concurrent.TimeUnit.SECONDS));
      assertEquals(1,jdbc.queryForObject("select count(*) from outbox_event where aggregate_id=? and type='notification.reminder.digest'",Integer.class,owner.getId()));
      assertEquals(2,jdbc.queryForObject("select count(*) from reminder_rule where id in (?,?) and status='FIRED' and digest_sent",Integer.class,first,second));
    }finally{pool.shutdownNow();}
  }
  @Test void deadlineEventsDeduplicateAndDiscardAnOldDeadline() {
    var owner=activeUser("remindowner","Password123!");var ws=mkWorkspace(owner.getId(),"remind");var p=mkActiveProject(owner.getId(),ws,"remind");var task=mkTask(owner.getId(),p.getId(),"Due task");
    jdbc.update("update task set due_at=now()-interval '1 hour' where id=?",task.getId());
    dispatcher.dispatch();dispatcher.dispatch();
    assertEquals(1,jdbc.queryForObject("select count(*) from outbox_event where aggregate_id=? and type='task.deadline.overdue'",Integer.class,task.getId()));
    UUID event=jdbc.queryForObject("select id from outbox_event where aggregate_id=? and type='task.deadline.overdue'",UUID.class,task.getId());
    jdbc.update("update task set due_at=now()+interval '10 days' where id=?",task.getId());
    handler.handle(events.findById(event).orElseThrow());
    assertEquals(0,jdbc.queryForObject("select count(*) from notification where dedup_token=?",Integer.class,"assignment:"+event+":"+owner.getId()));
    UUID rule=UUID.randomUUID();
    jdbc.update("insert into reminder_rule(id,task_id,user_id,remind_at) values (?,?,?,?)",rule,task.getId(),owner.getId(),OffsetDateTime.now().minusMinutes(1));
    dispatcher.dispatch();dispatcher.dispatch();
    assertEquals("FIRED",jdbc.queryForObject("select status from reminder_rule where id=?",String.class,rule));
    assertEquals(1,jdbc.queryForObject("select count(*) from outbox_event where aggregate_id=? and type='task.reminder.fire'",Integer.class,task.getId()));
    UUID fire=jdbc.queryForObject("select id from outbox_event where aggregate_id=? and type='task.reminder.fire'",UUID.class,task.getId());
    handler.handle(events.findById(fire).orElseThrow());handler.handle(events.findById(fire).orElseThrow());
    assertEquals(1,jdbc.queryForObject("select count(*) from notification where dedup_token=?",Integer.class,"assignment:"+fire+":"+owner.getId()));
  }
}
