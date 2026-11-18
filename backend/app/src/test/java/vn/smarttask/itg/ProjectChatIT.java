package vn.smarttask.itg;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.smarttask.common.AppException;
import vn.smarttask.identity.entity.AppUser;
import vn.smarttask.project.service.ProjectChatService;
import vn.smarttask.project.service.ProjectChatService.Send;
import vn.smarttask.realtime.RealtimeRegistry;

class ProjectChatIT extends ItgBase {
  @Autowired ProjectChatService chat;
  @Autowired PlatformTransactionManager transactions;
  @SpyBean RealtimeRegistry realtime;
  private record Room(AppUser manager,AppUser member,UUID project) {}
  private Room room() {
    var manager=activeUser("chatpm","Passw0rd!");var member=activeUser("chatmember","Passw0rd!");
    var p=mkActiveProject(manager.getId(),mkWorkspace(manager.getId(),"chat"),"chat");
    addProjectMember(manager.getId(),p.getId(),member.getId(),"MEMBER");
    return new Room(manager,member,p.getId());
  }
  private Send message(String body) {return new Send(UUID.randomUUID(),body,null);}
  private void hidden(Runnable action) {assertEquals(404,assertThrows(AppException.class,action::run).getHttpStatus().value());}

  @Test void activeMembersOnlyAndRemovedMembersLoseAccess() {
    var r=room();var outsider=activeUser("chatoutside","Passw0rd!");var guest=activeUser("chatguest","Passw0rd!");
    addProjectMember(r.manager().getId(),r.project(),guest.getId(),"GUEST");
    hidden(()->chat.access(outsider.getId(),r.project()));hidden(()->chat.access(guest.getId(),r.project()));
    hidden(()->chat.send(outsider.getId(),r.project(),message("Không hợp lệ")));
    hidden(()->chat.send(r.member().getId(),UUID.randomUUID(),message("Không có phòng")));
    assertTrue(chat.access(r.member().getId(),r.project()).write());
    chat.send(r.manager().getId(),r.project(),message("Tin riêng của dự án"));
    projects.removeMember(r.manager().getId(),r.project(),r.member().getId());
    hidden(()->chat.list(r.member().getId(),r.project(),null,null,null,50));
    hidden(()->chat.read(r.member().getId(),r.project(),1));
    hidden(()->chat.send(r.member().getId(),r.project(),message("Đã mất quyền")));
  }

  @Test void readOnlyProjectAndArchivedWorkspaceKeepHistoryButBlockWrites() {
    var r=room();chat.send(r.member().getId(),r.project(),message("Tin trước lưu trữ"));
    jdbc.update("update project set status='ON_HOLD' where id=?",r.project());
    assertFalse(chat.access(r.member().getId(),r.project()).write());
    assertEquals(1,chat.list(r.member().getId(),r.project(),null,null,null,50).items().size());
    assertEquals("PROJECT_READ_ONLY",assertThrows(AppException.class,()->chat.send(r.member().getId(),r.project(),message("Không gửi"))).getReasonCode());
    jdbc.update("update project set status='ACTIVE' where id=?",r.project());
    jdbc.update("update workspace set status='ARCHIVED' where id=(select workspace_id from project where id=?)",r.project());
    assertFalse(chat.access(r.member().getId(),r.project()).write());
  }

  @Test void concurrentRetriesPersistOneMessageAndChangedPayloadConflicts() throws Exception {
    var r=room();var request=message("Gửi duy nhất một lần");
    var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
    try{
      var first=pool.submit(()->{start.await();return chat.send(r.member().getId(),r.project(),request);});
      var second=pool.submit(()->{start.await();return chat.send(r.member().getId(),r.project(),request);});
      start.countDown();assertEquals(first.get(20,TimeUnit.SECONDS).id(),second.get(20,TimeUnit.SECONDS).id());
    }finally{pool.shutdownNow();}
    assertEquals(1,jdbc.queryForObject("select count(*) from project_message where project_id=?",Integer.class,r.project()));
    assertEquals("IDEMPOTENCY_CONFLICT",assertThrows(AppException.class,()->chat.send(r.member().getId(),r.project(),new Send(request.clientMessageId(),"Đã đổi nội dung",null))).getReasonCode());
  }

  @Test void rolledBackMessagesDoNotPublishAndCommitPublishesOnlyRoutingKeys() {
    var r=room();clearInvocations(realtime);
    new TransactionTemplate(transactions).execute(status->{chat.send(r.member().getId(),r.project(),message("Không được lưu"));status.setRollbackOnly();return null;});
    assertEquals(0,jdbc.queryForObject("select count(*) from project_message where project_id=?",Integer.class,r.project()));
    verify(realtime,never()).publishToUser(any(),eq("CHAT_MESSAGE_CREATED"),anyString(),any(),any());
    var stored=chat.send(r.member().getId(),r.project(),message("Đã lưu"));
    verify(realtime).publishToUser(r.manager().getId(),"CHAT_MESSAGE_CREATED","CHAT",r.project(),stored.id());
    verify(realtime).publishToUser(r.member().getId(),"CHAT_MESSAGE_CREATED","CHAT",r.project(),stored.id());
  }

  @Test void deletionIsRedactedReplayableAndCaughtUpAfterDisconnect() {
    var r=room();var request=message("Nội dung cần xóa");
    var stored=chat.send(r.member().getId(),r.project(),request);
    long before=chat.list(r.manager().getId(),r.project(),null,null,null,50).revision();
    assertThrows(AppException.class,()->chat.delete(r.manager().getId(),r.project(),stored.id(),stored.changedSeq(),null));
    var removed=chat.delete(r.manager().getId(),r.project(),stored.id(),stored.changedSeq(),"Gửi nhầm nội dung");
    assertTrue(removed.deleted());assertNull(removed.body());assertNull(removed.linkedTask());
    var caughtUp=chat.list(r.member().getId(),r.project(),null,before,null,50);
    assertEquals(stored.id(),caughtUp.items().get(0).id());assertTrue(caughtUp.items().get(0).deleted());
    assertTrue(chat.send(r.member().getId(),r.project(),request).deleted());
    assertEquals(removed.changedSeq(),chat.delete(r.member().getId(),r.project(),stored.id(),stored.changedSeq(),null).changedSeq());
  }

  @Test void anotherMemberCannotDeleteAndAuthorNeedsCurrentVersion() {
    var r=room();var other=activeUser("chatpeer","Passw0rd!");addProjectMember(r.manager().getId(),r.project(),other.getId(),"MEMBER");
    var stored=chat.send(r.member().getId(),r.project(),message("Tin của thành viên"));
    assertEquals(403,assertThrows(AppException.class,()->chat.delete(other.getId(),r.project(),stored.id(),stored.changedSeq(),"Không được" )).getHttpStatus().value());
    assertEquals("VERSION_CONFLICT",assertThrows(AppException.class,()->chat.delete(r.member().getId(),r.project(),stored.id(),99,null)).getCode());
    assertTrue(chat.delete(r.member().getId(),r.project(),stored.id(),stored.changedSeq(),null).deleted());
  }

  @Test void unreadExcludesOwnMessagesAndReadMarkerOnlyMovesForward() {
    var r=room();var one=chat.send(r.manager().getId(),r.project(),message("Một"));
    var two=chat.send(r.manager().getId(),r.project(),message("Hai"));
    chat.send(r.member().getId(),r.project(),message("Của tôi"));
    assertEquals(2,chat.unread(r.member().getId(),r.project()).count());
    assertEquals(0,chat.read(r.member().getId(),r.project(),two.seq()).count());
    assertEquals(two.seq(),chat.read(r.member().getId(),r.project(),one.seq()).lastReadSeq());
    assertThrows(AppException.class,()->chat.read(r.member().getId(),r.project(),999));
  }

  @Test void historyAndDeltaPaginationDoNotMissConcurrentDeletion() {
    var r=room();var one=chat.send(r.manager().getId(),r.project(),message("Một"));
    var two=chat.send(r.manager().getId(),r.project(),message("Hai"));
    var initial=chat.list(r.member().getId(),r.project(),null,null,null,1);
    assertEquals(two.id(),initial.items().get(0).id());assertTrue(initial.hasMore());
    assertEquals(one.id(),chat.list(r.member().getId(),r.project(),initial.nextBefore(),null,null,1).items().get(0).id());
    var delta=chat.list(r.member().getId(),r.project(),null,0L,null,1);
    chat.delete(r.manager().getId(),r.project(),two.id(),two.changedSeq(),null);
    var oldSnapshot=chat.list(r.member().getId(),r.project(),null,delta.nextAfter(),delta.revision(),1);
    assertEquals(delta.revision(),oldSnapshot.nextAfter());
    var next=chat.list(r.member().getId(),r.project(),null,oldSnapshot.nextAfter(),null,50);
    assertEquals(two.id(),next.items().get(0).id());assertTrue(next.items().get(0).deleted());
    assertThrows(AppException.class,()->chat.list(r.member().getId(),r.project(),1L,1L,null,50));
  }

  @Test void taskLinksAreScopedAndRedactedPerReader() {
    var r=room();var task=mkTask(r.manager().getId(),r.project(),"Công việc bí mật");
    chat.send(r.manager().getId(),r.project(),new Send(UUID.randomUUID(),"Liên quan công việc",task.getId()));
    assertEquals(task.getId(),chat.list(r.manager().getId(),r.project(),null,null,null,50).items().get(0).linkedTask().id());
    var redacted=chat.list(r.member().getId(),r.project(),null,null,null,50).items().get(0);
    assertNull(redacted.linkedTask());assertTrue(redacted.taskUnavailable());
    var another=mkActiveProject(r.manager().getId(),mkWorkspace(r.manager().getId(),"otherchat"),"otherchat");
    var wrong=mkTask(r.manager().getId(),another.getId(),"Ngoài dự án");
    assertEquals("CHAT_TASK_SCOPE",assertThrows(AppException.class,()->chat.send(r.manager().getId(),r.project(),new Send(UUID.randomUUID(),"Sai phạm vi",wrong.getId()))).getReasonCode());
  }

  @Test void httpRejectsForgedSenderAndExcessiveSending() throws Exception {
    var r=room();String token=loginToken(r.member().getUsername(),"Passw0rd!");
    String path="/api/v1/projects/"+r.project()+"/messages";
    assertEquals(400,doPost(path,token,Map.of("client_message_id",UUID.randomUUID(),"body","Tin","sender_id",r.manager().getId())).getResponse().getStatus());
    var accepted=doPost(path,token,Map.of("client_message_id",UUID.randomUUID(),"body","Tiếng Việt 😀"));
    assertEquals(200,accepted.getResponse().getStatus(),accepted.getResponse().getContentAsString());
    assertEquals(r.member().getId().toString(),json(accepted).path("data").path("sender_id").asText());
    for(int i=0;i<29;i++)chat.send(r.member().getId(),r.project(),message("Tin "+i));
    assertEquals(429,assertThrows(AppException.class,()->chat.send(r.member().getId(),r.project(),message("Quá nhiều"))).getHttpStatus().value());
  }

  @Test void twoAuthenticatedMembersReadPersistedMessagesOverHttp() throws Exception {
    var r=room();
    jdbc.update("update app_user set display_name='Nguyễn An' where id=?",r.manager().getId());
    jdbc.update("update app_user set display_name='Trần Bình' where id=?",r.member().getId());
    jdbc.update("update project set name='Website khách hàng — kiểm thử trao đổi' where id=?",r.project());
    String first=loginToken(r.manager().getUsername(),"Passw0rd!"),second=loginToken(r.member().getUsername(),"Passw0rd!");
    var task=mkTask(r.manager().getId(),r.project(),"Kiểm tra giao diện đăng nhập");
    tasks.assign(r.manager().getId(),task.getId(),r.member().getId(),task.getVersion());
    String path="/api/v1/projects/"+r.project()+"/messages";
    var sent=doPost(path,first,Map.of("client_message_id",UUID.randomUUID(),"body","Chào cả nhóm, đây là trao đổi của dự án.","linked_task_id",task.getId()));
    assertEquals(200,sent.getResponse().getStatus(),sent.getResponse().getContentAsString());
    var fetched=doGet(path,second);assertEquals(200,fetched.getResponse().getStatus());
    assertEquals(json(sent).path("data").path("id"),json(fetched).path("data").path("items").get(0).path("id"));
    assertEquals(task.getId().toString(),json(fetched).path("data").path("items").get(0).path("linked_task").path("id").asText());
    // Explicit opt-in exports only isolated test sessions for the real-browser acceptance script.
    String fixture=System.getenv("SMARTTASK_CHAT_FIXTURE_FILE");
    if(fixture!=null){
      assertEquals("smarttask_chat_itg",jdbc.queryForObject("select current_database()",String.class));
      var file=java.nio.file.Path.of(fixture);java.nio.file.Files.createDirectories(file.getParent());
      om.writeValue(file.toFile(),Map.of("projectId",r.project(),"taskId",task.getId(),
          "manager",Map.of("id",r.manager().getId(),"username",r.manager().getUsername(),"token",first),
          "member",Map.of("id",r.member().getId(),"username",r.member().getUsername(),"token",second)));
    }
  }
}
