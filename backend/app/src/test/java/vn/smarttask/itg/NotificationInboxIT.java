package vn.smarttask.itg;

import java.util.HashSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.smarttask.notify.entity.Notification;
import vn.smarttask.notify.repo.NotificationRepository;
import static org.junit.jupiter.api.Assertions.*;

class NotificationInboxIT extends ItgBase {
  @Autowired NotificationRepository repository;
  @Autowired vn.smarttask.notify.service.NotificationService service;

  @Test void pagesAreActorScopedAndStableAcrossNewArrivals() throws Exception {
    var owner = activeUser("inbox", "Password123!");
    var other = activeUser("otherinbox", "Password123!");
    String token = loginToken(owner.getUsername(), "Password123!");
    for (int i=0; i<105; i++) repository.save(new Notification(UUID.randomUUID(), owner.getId(), "TEST"));
    var hidden = new Notification(UUID.randomUUID(), owner.getId(), "HIDDEN");
    hidden.setRedacted(true); repository.save(hidden);
    var foreign = repository.save(new Notification(UUID.randomUUID(), other.getId(), "FOREIGN"));
    var first = json(doGet("/api/v1/notifications?limit=20", token)).path("data");
    assertEquals(105, first.path("total").asInt());
    assertEquals(20, first.path("items").size());
    long latest = first.path("items").get(0).path("seq").asLong();
    var arrival = repository.save(new Notification(UUID.randomUUID(), owner.getId(), "NEW"));
    var seen = new HashSet<String>();
    var page = first;
    while (true) {
      for (var item : page.path("items")) assertTrue(seen.add(item.path("id").asText()), "Duplicate across pages");
      if (!page.path("has_more").asBoolean()) break;
      var result = doGet("/api/v1/notifications?limit=20&cursor="+page.path("next_cursor").asText(), token);
      assertEquals(200, result.getResponse().getStatus());
      page = json(result).path("data");
    }
    assertEquals(105, seen.size());
    assertFalse(seen.contains(arrival.getId().toString()));
    var delta = json(doGet("/api/v1/notifications?since_seq="+latest, token)).path("data");
    assertEquals(1, delta.path("items").size());
    assertEquals(arrival.getId().toString(), delta.path("items").get(0).path("id").asText());
    assertEquals(404, doGet("/api/v1/notifications?cursor="+foreign.getId(), token).getResponse().getStatus());
    assertEquals(400, doGet("/api/v1/notifications?cursor=invalid", token).getResponse().getStatus());
    assertEquals(400, doGet("/api/v1/notifications?since_seq=-1", token).getResponse().getStatus());
    assertEquals(400, doGet("/api/v1/notifications?filter=unknown", token).getResponse().getStatus());
    var task = repository.save(new Notification(UUID.randomUUID(), owner.getId(), "TASK_ASSIGNED"));
    var reminder = repository.save(new Notification(UUID.randomUUID(), owner.getId(), "REMINDER_DUE"));
    var taskPage = json(doGet("/api/v1/notifications?filter=task", token)).path("data");
    assertEquals(1, taskPage.path("total").asInt());
    assertEquals(task.getId().toString(), taskPage.path("items").get(0).path("id").asText());
    assertEquals(1, json(doGet("/api/v1/notifications?filter=reminder", token)).path("data").path("total").asInt());
    task.setReadAt(java.time.OffsetDateTime.now()); repository.save(task);
    assertEquals(107, json(doGet("/api/v1/notifications?filter=unread", token)).path("data").path("total").asInt());
    assertEquals(106, json(doGet("/api/v1/notifications?filter=system", token)).path("data").path("total").asInt());
    assertEquals(200,doPost("/api/v1/notifications/read-all",token,java.util.Map.of()).getResponse().getStatus());
    assertEquals(0,json(doGet("/api/v1/notifications/unread-count",token)).path("data").path("count").asInt());
    assertNull(repository.findById(foreign.getId()).orElseThrow().getReadAt());
    var deferred=new Notification(UUID.randomUUID(),owner.getId(),"DEFERRED_TEST");deferred.setDeferredUntil(java.time.OffsetDateTime.now().plusHours(1));repository.save(deferred);
    assertEquals(0,json(doGet("/api/v1/notifications/unread-count",token)).path("data").path("count").asInt());
    assertFalse(json(doGet("/api/v1/notifications?filter=unread",token)).path("data").toString().contains(deferred.getId().toString()));
    deferred=repository.findById(deferred.getId()).orElseThrow();long oldSeq=deferred.getSeq();deferred.setDeferredUntil(java.time.OffsetDateTime.now().minusMinutes(1));repository.save(deferred);
    assertTrue(service.releaseDeferred()>=1);
    assertNull(repository.findById(deferred.getId()).orElseThrow().getDeferredUntil());
    assertTrue(repository.findById(deferred.getId()).orElseThrow().getSeq()>oldSeq);
    assertEquals(1,json(doGet("/api/v1/notifications/unread-count",token)).path("data").path("count").asInt());
  }
}
