package vn.smarttask.itg;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.smarttask.task.service.TaskOverviewService;
import static org.junit.jupiter.api.Assertions.*;

class TaskOverviewIT extends ItgBase {
  @Autowired TaskOverviewService overview;
  @Autowired vn.smarttask.workspace.TeamService teams;
  @Test void aggregatesBeyondOnePageAndCalendarChecksEachTask() throws Exception {
    var owner=activeUser("overview","Password123!");var member=activeUser("overviewmember","Password123!");
    var ws=mkWorkspace(owner.getId(),"overview");var project=mkActiveProject(owner.getId(),ws,"overview");
    addProjectMember(owner.getId(),project.getId(),member.getId(),"MEMBER");
    var task=mkTask(owner.getId(),project.getId(),"Visible assigned");
    var second=mkTask(owner.getId(),project.getId(),"Private to manager");
    tasks.assign(owner.getId(),task.getId(),member.getId(),task.getVersion());
    var due=LocalDate.of(2026,3,8).atTime(23,59).atZone(ZoneId.of("America/New_York")).toOffsetDateTime();
    jdbc.update("update task set due_at=? where id in (?,?)",due,task.getId(),second.getId());
    for(int i=0;i<103;i++) mkTask(owner.getId(),project.getId(),"Aggregate "+i);
    var result=overview.summary(owner.getId(),"project",project.getId(),null,null,null,"UTC");
    assertEquals(105L,((Number)result.get("total")).longValue());
    var team=teams.create(owner.getId(),ws,"Calendar filter team");teams.linkTask(owner.getId(),team.id(),second.getId(),second.getVersion());
    assertEquals(1,overview.calendar(owner.getId(),"day",LocalDate.of(2026,3,8),"America/New_York","project",project.getId(),team.id(),0,20).size());
    assertTrue(overview.calendar(member.getId(),"day",LocalDate.of(2026,3,8),"America/New_York","project",project.getId(),team.id(),0,20).isEmpty());
    var firstPage=overview.calendar(owner.getId(),"unscheduled",null,"UTC","project",project.getId(),null,0,20);
    var nextPage=overview.calendar(owner.getId(),"unscheduled",null,"UTC","project",project.getId(),null,1,20);
    assertEquals(20,firstPage.size());assertEquals(20,nextPage.size());
    assertTrue(firstPage.stream().noneMatch(a->nextPage.stream().anyMatch(b->a.get("task_id").equals(b.get("task_id")))));
    assertEquals(1L,((Number)overview.summary(member.getId(),"all",null,null,null,null,"UTC").get("total")).longValue());
    var entries=overview.calendar(member.getId(),"day",LocalDate.of(2026,3,8),"America/New_York");
    assertEquals(1,entries.size());assertEquals(task.getId(),entries.get(0).get("task_id"));
    assertEquals(0,overview.calendar(member.getId(),"day",LocalDate.of(2026,3,8),"UTC").size());
    var membership=projectMembers.findMembership(project.getId(),member.getId()).orElseThrow();membership.setStatus("REMOVED");projectMembers.save(membership);
    assertTrue(overview.calendar(member.getId(),"month",LocalDate.of(2026,3,8),"America/New_York").isEmpty());
    assertEquals(0L,((Number)overview.summary(member.getId(),"all",null,null,null,null,"UTC").get("total")).longValue());
  }
}
