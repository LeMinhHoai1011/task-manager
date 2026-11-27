package vn.smarttask.itg;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PersonalTaskApiIT extends ItgBase {
  @Test
  void checklistLabelsEditTrashAndRestorePersistThroughHttp() throws Exception {
    var owner = activeUser("detail", "Password123!");
    var stranger = activeUser("hidden", "Password123!");
    String token = loginToken(owner.getUsername(), "Password123!");
    String other = loginToken(stranger.getUsername(), "Password123!");
    var created = doPost("/api/v1/me/tasks", token, Map.of("title", "Details"), Map.of("Idempotency-Key", idem()));
    String path = "/api/v1/me/tasks/" + json(created).path("data").path("id").asText();
    assertEquals(200, doPost(path + "/checklist", token, Map.of("title", "Check"), Map.of("If-Match", "1")).getResponse().getStatus());
    var detail = json(doGet(path, token)).path("data");
    String item = detail.path("checklist").get(0).path("id").asText();
    assertEquals(404, doPatch(path + "/checklist/" + item, other, Map.of("done", true), Map.of("If-Match", "2")).getResponse().getStatus());
    assertEquals(200, doPatch(path + "/checklist/" + item, token, Map.of("done", true), Map.of("If-Match", "2")).getResponse().getStatus());
    assertEquals("TODO", json(doGet(path, token)).path("data").path("task").path("state").asText());
    var labels = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path + "/labels")
        .header("Authorization", "Bearer " + token).header("If-Match", "3")
        .contentType("application/json").content(om.writeValueAsString(Map.of("labels", java.util.List.of("work", "work"))))).andReturn();
    assertEquals(200, labels.getResponse().getStatus(), labels.getResponse().getContentAsString());
    assertEquals(1, json(doGet(path, token)).path("data").path("tags").size());
    var edit = Map.of("title", "Edited", "description", "Description", "notes", "Private note", "priority", "HIGH");
    assertEquals(200, doPatch(path, token, edit, Map.of("If-Match", "4")).getResponse().getStatus());
    var removed = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(path)
        .header("Authorization", "Bearer " + token).header("If-Match", "5")).andReturn();
    assertEquals(200, removed.getResponse().getStatus());
    assertEquals(404, doGet(path, token).getResponse().getStatus());
    assertEquals(0, json(doGet("/api/v1/me/tasks/summary", token)).path("data").path("total").asInt());
    assertEquals(404, doPost(path + "/restore", other, null, Map.of("If-Match", "6")).getResponse().getStatus());
    assertEquals(200, doPost(path + "/restore", token, null, Map.of("If-Match", "6")).getResponse().getStatus());
    assertEquals("Private note", json(doGet(path, token)).path("data").path("task").path("notes").asText());
    assertEquals(1, json(doGet("/api/v1/me/tasks?tag=work&q=Edited", token)).path("data").path("totalElements").asInt());
  }
  @org.springframework.beans.factory.annotation.Autowired
  vn.smarttask.task.service.PersonalTaskCreationService creation;

  @org.springframework.beans.factory.annotation.Autowired
  vn.smarttask.task.service.PersonalTaskRecurrenceService recurrences;

  @Test
  void dashboardScopesDateOnlyWorkAndRecurringPeriodsStayIndependent() throws Exception {
    var owner = activeUser("agenda", "Password123!");
    var stranger = activeUser("agendaother", "Password123!");
    String token = loginToken(owner.getUsername(), "Password123!");
    String other = loginToken(stranger.getUsername(), "Password123!");
    var source = creation.create(owner.getId(), "Daily agenda", idem());
    var path = "/api/v1/me/tasks/" + source.getId();
    var zone = java.time.ZoneId.of("Pacific/Kiritimati");
    var today = java.time.LocalDate.now(zone);
    var due = today.atStartOfDay(zone).toOffsetDateTime();
    assertEquals(200, doPatch(path, token, Map.of("title", source.getTitle(), "priority", "HIGH",
        "dueAt", due.toString(), "dueIsDateOnly", true, "dueTimezone", zone.getId()), Map.of("If-Match", "1"))
        .getResponse().getStatus());
    var agenda = "/api/v1/dashboard/today?bucket=today&timezone=Pacific/Kiritimati";
    var own = json(doGet(agenda, token)).path("data").path("items");
    assertEquals(1, own.size());
    assertEquals(today.toString(), own.get(0).path("due_date_only").asText());
    assertEquals(0, json(doGet(agenda, other)).path("data").path("items").size());

    var recurrence = "/api/v1/me/task-recurrences/" + source.getId();
    var put = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(recurrence)
        .header("Authorization", "Bearer " + token).contentType("application/json")
        .content("{\"intervalDays\":1,\"version\":2}");
    assertEquals(200, mvc.perform(put).andReturn().getResponse().getStatus());
    assertEquals(0, json(doGet(recurrence, other)).path("data").size());
    jdbc.update("update personal_task_recurrence set run_at=now() where source_task_id=?", source.getId());
    var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      var first = executor.submit(recurrences::generateDue);
      var second = executor.submit(recurrences::generateDue);
      first.get(20, java.util.concurrent.TimeUnit.SECONDS);
      second.get(20, java.util.concurrent.TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
    }
    var tasks = taskRepo.findByOwnerUserIdAndScopeAndDeletedAtIsNull(owner.getId(), "PERSONAL",
        org.springframework.data.domain.PageRequest.of(0, 20)).getContent();
    assertEquals(2, tasks.size());
    var generated = tasks.stream().filter(task -> !task.getId().equals(source.getId())).findFirst().orElseThrow();
    assertEquals(today.plusDays(1), generated.getDueAt().atZoneSameInstant(zone).toLocalDate());
    assertEquals("HIGH", generated.getPriority());
    assertEquals("TODO", generated.getState());
    recurrences.stop(owner.getId(), source.getId());
    jdbc.update("update personal_task_recurrence set run_at=now() where source_task_id=?", source.getId());
    recurrences.generateDue();
    assertEquals(2, taskRepo.findByOwnerUserIdAndScopeAndDeletedAtIsNull(owner.getId(), "PERSONAL",
        org.springframework.data.domain.PageRequest.of(0, 20)).getTotalElements());
  }
  @Test
  void concurrentRetriesCreateExactlyOneTask() throws Exception {
    var owner = activeUser("retry", "Password123!");
    String key = idem();
    var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
    var ready = new java.util.concurrent.CountDownLatch(2);
    var go = new java.util.concurrent.CountDownLatch(1);
    try {
      java.util.concurrent.Callable<java.util.UUID> create = () -> {
        ready.countDown();
        if (!go.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Timed out");
        return creation.create(owner.getId(), "Concurrent", key).getId();
      };
      var first = executor.submit(create); var second = executor.submit(create);
      assertTrue(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)); go.countDown();
      assertEquals(first.get(20, java.util.concurrent.TimeUnit.SECONDS), second.get(20, java.util.concurrent.TimeUnit.SECONDS));
      assertEquals(1, taskRepo.findByOwnerUserIdAndScopeAndDeletedAtIsNull(owner.getId(), "PERSONAL",
          org.springframework.data.domain.PageRequest.of(0, 20)).getTotalElements());
    } finally { go.countDown(); executor.shutdownNow(); }
  }
  @Test
  void authenticatedOwnerLifecycleAndIdempotentCreation() throws Exception {
    var owner = activeUser("personal", "Password123!");
    var outsider = activeUser("outsider", "Password123!");
    String token = loginToken(owner.getUsername(), "Password123!");
    String other = loginToken(outsider.getUsername(), "Password123!");
    assertEquals(401, doGet("/api/v1/me/tasks", null).getResponse().getStatus());
    var headers = Map.of("Idempotency-Key", idem());
    var first = doPost("/api/v1/me/tasks", token, Map.of("title", "Private task"), headers);
    assertEquals(200, first.getResponse().getStatus(), first.getResponse().getContentAsString());
    var data = om.readTree(first.getResponse().getContentAsString()).path("data");
    String id = data.path("id").asText();
    assertTrue(data.path("projectId").isNull());
    var repeated = doPost("/api/v1/me/tasks", token, Map.of("title", "Private task"), headers);
    assertEquals(200, repeated.getResponse().getStatus());
    assertEquals(id, om.readTree(repeated.getResponse().getContentAsString()).path("data").path("id").asText());
    assertEquals(409, doPost("/api/v1/me/tasks", token, Map.of("title", "Different"), headers).getResponse().getStatus());
    assertEquals(404, doGet("/api/v1/me/tasks/" + id, other).getResponse().getStatus());
    var complete = doPatch("/api/v1/me/tasks/" + id + "/completion", token, Map.of("completed", true), Map.of("If-Match", "\"1\""));
    assertEquals(200, complete.getResponse().getStatus(), complete.getResponse().getContentAsString());
    assertEquals("COMPLETED", om.readTree(complete.getResponse().getContentAsString()).path("data").path("state").asText());
    assertEquals(409, doPatch("/api/v1/me/tasks/" + id + "/completion", token, Map.of("completed", false), Map.of("If-Match", "1")).getResponse().getStatus());
    var list = doGet("/api/v1/me/tasks", token);
    assertEquals(200, list.getResponse().getStatus());
    assertEquals(1, om.readTree(list.getResponse().getContentAsString()).path("data").path("totalElements").asInt());
  }
}
