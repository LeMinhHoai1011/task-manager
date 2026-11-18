package vn.smarttask.itg;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties={"spring.datasource.hikari.maximum-pool-size=6", "spring.jpa.properties.hibernate.generate_statistics=true"})
class TaskAuditRegressionIT extends ItgBase {
  @Autowired DataSource dataSource;
  @Autowired jakarta.persistence.EntityManagerFactory emf;
  record Fixture(UUID actor, UUID project, String token) {}
  Fixture fixture() {
    var user=activeUser("auditfix","AuditPass123!");
    var project=mkActiveProject(user.getId(),mkWorkspace(user.getId(),"auditfix"),"auditfix");
    return new Fixture(user.getId(),project.getId(),loginToken(user.getUsername(),"AuditPass123!"));
  }
  void waitForTwoLocks() throws Exception {
    for(int n=0;n<200;n++) {
      if(jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock'",Integer.class)>=2)return;
      Thread.sleep(25);
    }
    fail("Both requests must reach the lock barrier");
  }
  @Test void concurrentBlockedWritesHaveOneWinner() throws Exception {
    var f=fixture();var task=mkTask(f.actor(),f.project(),"Blocked race");
    try(var lock=dataSource.getConnection();var pool=Executors.newFixedThreadPool(2)) {
      lock.setAutoCommit(false);
      try(var ps=lock.prepareStatement("select id from task where id=? for update")){ps.setObject(1,task.getId());ps.executeQuery().close();}
      Callable<Integer> write=()->doPost("/api/v1/tasks/"+task.getId()+"/blocked",f.token(),
          Map.of("blocked_flag",true,"blocked_reason",idem()),Map.of("If-Match","1","Idempotency-Key",idem())).getResponse().getStatus();
      var a=pool.submit(write);var b=pool.submit(write);
      try{waitForTwoLocks();}finally{lock.commit();}
      var statuses=List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
      assertEquals(1,statuses.stream().filter(s->s==200).count(),statuses.toString());
      assertEquals(1,statuses.stream().filter(s->s==409).count(),statuses.toString());
      assertEquals(2,taskRepo.findById(task.getId()).orElseThrow().getVersion());
    }
  }
  @Test void concurrentOppositeDependenciesCannotCreateCycle() throws Exception {
    var f=fixture();var a=mkTask(f.actor(),f.project(),"A");var b=mkTask(f.actor(),f.project(),"B");
    try(var lock=dataSource.getConnection();var pool=Executors.newFixedThreadPool(2)) {
      lock.setAutoCommit(false);lock.createStatement().execute("lock table task_dependency in share mode");
      var first=pool.submit(()->doPost("/api/v1/tasks/"+a.getId()+"/dependencies",f.token(),
          Map.of("depends_on_task_id",b.getId()),Map.of("Idempotency-Key",idem())).getResponse().getStatus());
      var second=pool.submit(()->doPost("/api/v1/tasks/"+b.getId()+"/dependencies",f.token(),
          Map.of("depends_on_task_id",a.getId()),Map.of("Idempotency-Key",idem())).getResponse().getStatus());
      try{waitForTwoLocks();}finally{lock.commit();}
      var statuses=List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));
      assertEquals(1,statuses.stream().filter(s->s==200).count(),statuses.toString());
      assertEquals(1,statuses.stream().filter(s->s==422).count(),statuses.toString());
      assertEquals(1,jdbc.queryForObject("select count(*) from task_dependency where task_id in (?,?)",Integer.class,a.getId(),b.getId()));
    }
  }
  @Test void allSortsPageWithoutMissingOrDuplicatingTasks() throws Exception {
    var f=fixture();
    for(String priority:List.of("HIGH","LOW","MEDIUM","URGENT")) {
      var task=tasks.create(f.actor(),f.project(),"Task "+priority,null,priority,null);
      if(!"LOW".equals(priority))tasks.setDeadline(f.actor(),task.getId(),null,java.time.OffsetDateTime.now().plusDays(1),"UTC",1);
    }
    for(String sort:List.of("priority","title","state","created_at","updated_at","due_at","start_at","-priority,title","due_at,-title")) {
      for(String order:List.of("asc","desc")) {
        Set<String> ids=new HashSet<>();String cursor=null;
        for(int page=0;page<5;page++) {
          String path="/api/v1/tasks?project_id="+f.project()+"&sort="+sort+"&order="+order+"&limit=1";
          if(cursor!=null)path+="&cursor="+java.net.URLEncoder.encode(cursor,java.nio.charset.StandardCharsets.UTF_8);
          var response=doGet(path,f.token());assertEquals(200,response.getResponse().getStatus(),response.getResponse().getContentAsString());
          var data=json(response).path("data");assertEquals(4,data.path("total").asInt());
          for(var row:data.path("items"))assertTrue(ids.add(row.path("id").asText()),"duplicate for "+sort);
          if(!data.path("has_more").asBoolean())break;
          cursor=data.path("next_cursor").asText();
        }
        assertEquals(4,ids.size(),sort+" "+order);
      }
    }
  }
  @Test void removedAssigneeIsExcludedAndQueryCountDoesNotGrowPerTask() throws Exception {
    var f=fixture();var task=mkTask(f.actor(),f.project(),"Removed assignee");
    tasks.assign(f.actor(),task.getId(),f.actor(),1);tasks.unassign(f.actor(),task.getId(),f.actor(),"test",2);
    var filtered=json(doGet("/api/v1/tasks?project_id="+f.project()+"&assignee_id="+f.actor(),f.token())).path("data");
    assertEquals(0,filtered.path("items").size());
    var stats=emf.unwrap(org.hibernate.SessionFactory.class).getStatistics();
    String path="/api/v1/tasks?project_id="+f.project()+"&limit=1";
    for(int i=0;i<3;i++)mkTask(f.actor(),f.project(),"Before "+i);
    stats.clear();doGet(path,f.token());long before=stats.getPrepareStatementCount();
    for(int i=0;i<20;i++)mkTask(f.actor(),f.project(),"Extra "+i);
    stats.clear();doGet(path,f.token());long after=stats.getPrepareStatementCount();
    assertEquals(before,after,"SQL query count must be independent of total tasks");
    assertTrue(after<20,"Unexpected query count: "+after);
  }
}
