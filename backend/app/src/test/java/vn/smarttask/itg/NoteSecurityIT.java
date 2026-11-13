package vn.smarttask.itg;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import vn.smarttask.common.AppException;
import vn.smarttask.note.entity.Note;
import vn.smarttask.note.repo.NoteRepository;
import vn.smarttask.note.service.NoteService;
import vn.smarttask.note.service.NoteService.UpsertNoteRequest;
import vn.smarttask.task.entity.Task;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** Real PostgreSQL + Flyway + production policy, with independent account fixtures. */
class NoteSecurityIT extends ItgBase {
  @Autowired NoteService notes;
  @Autowired NoteRepository noteRepo;
  UUID manager, member, guest, outsider, ws, project;
  String memberToken;
  static final String PASSWORD = "AuditOnly-Strong-543!";

  @BeforeEach void fixture() {
    manager = activeUser("nm", PASSWORD).getId();
    var m = activeUser("nu", PASSWORD);
    member = m.getId();
    guest = activeUser("ng", PASSWORD).getId();
    outsider = activeUser("nx", PASSWORD).getId();
    ws = mkWorkspace(manager, "notes");
    addWsMember(ws, member, "MEMBER");
    project = mkActiveProject(manager, ws, "notes").getId();
    addProjectMember(manager, project, member, "MEMBER");
    addProjectMember(manager, project, guest, "GUEST");
    memberToken = loginToken(m.getUsername(), PASSWORD);
  }

  UpsertNoteRequest request(String scope, UUID workspaceId, UUID projectId, UUID taskId) {
    return new UpsertNoteRequest(workspaceId, projectId, taskId, scope, "Audit note", "private body", null, false);
  }
  void denied(Runnable action, int status) {
    assertEquals(status, assertThrows(AppException.class, action::run).getHttpStatus().value());
  }

  @Test void personalCannotLeakViaProjectOrTaskFilters() {
    var task = mkTask(manager, project, "notes task");
    Note legacy = new Note(UUID.randomUUID(), manager, "PERSONAL", "secret", "private");
    legacy.setProjectId(project); legacy.setTaskId(task.getId());
    noteRepo.saveAndFlush(legacy);
    denied(() -> notes.getNote(member, legacy.getId()), 404);
    assertTrue(notes.listNotes(member, null, project, null, null).isEmpty());
    denied(() -> notes.listNotes(outsider, null, project, null, null), 404);
    denied(() -> notes.listNotes(outsider, null, null, task.getId(), null), 404);
  }

  @Test void projectReadsDoNotGrantWrites() {
    var n = notes.createNote(manager, request("PROJECT", ws, project, null));
    assertEquals(n.id(), notes.getNote(member, n.id()).id());
    assertEquals(n.id(), notes.getNote(guest, n.id()).id());
    denied(() -> notes.updateNote(member, n.id(), request("PROJECT", ws, project, null), n.version()), 403);
    denied(() -> notes.deleteNote(guest, n.id()), 403);
    denied(() -> notes.togglePin(guest, n.id()), 403);
    denied(() -> notes.createNote(outsider, request("PROJECT", ws, project, null)), 404);
    denied(() -> notes.createNote(member, request("PROJECT", ws, project, null)), 403);
  }

  @Test void sharedMembersReadButCannotEditAnotherOwnersNote() {
    var n = notes.createNote(manager, request("SHARED", ws, null, null));
    assertEquals(n.id(), notes.getNote(member, n.id()).id());
    denied(() -> notes.updateNote(member, n.id(), request("SHARED", ws, null, null), n.version()), 403);
    denied(() -> notes.getNote(outsider, n.id()), 404);
    var own = notes.createNote(member, request("SHARED", ws, null, null));
    notes.updateNote(member, own.id(), request("SHARED", ws, null, null), own.version());
  }

  @Test void removedOwnerLosesReadWriteListAndRestore() {
    var n = notes.createNote(member, request("SHARED", ws, null, null));
    notes.deleteNote(member, n.id());
    var membership = wsMembers.findMembership(ws, member).orElseThrow();
    membership.setStatus("REMOVED"); wsMembers.saveAndFlush(membership);
    denied(() -> notes.restoreNote(member, n.id()), 404);
    assertTrue(notes.listNotes(member, null, null, null, null).isEmpty());
  }

  @Test void removedProjectOwnerHasNoOwnershipBypass() {
    var n = notes.createNote(manager, request("PROJECT", ws, project, null));
    var membership = projectMembers.findMembership(project, manager).orElseThrow();
    membership.setStatus("REMOVED"); projectMembers.saveAndFlush(membership);
    denied(() -> notes.getNote(manager, n.id()), 404);
    denied(() -> notes.deleteNote(manager, n.id()), 404);
    assertTrue(notes.listNotes(manager, null, null, null, null).isEmpty());
  }

  @Test void taskScopeUsesActualTaskVisibility() {
    var t = mkTask(manager, project, "restricted task");
    var n = notes.createNote(manager, request("TASK", ws, project, t.getId()));
    denied(() -> notes.getNote(member, n.id()), 404);
    denied(() -> notes.getNote(outsider, n.id()), 404);
    denied(() -> notes.listNotes(member, null, null, t.getId(), null), 404);
    assertTrue(notes.listNotes(member, null, null, null, null).isEmpty());
    assertEquals(n.id(), notes.getNote(manager, n.id()).id());
  }

  @Test void personalTaskNotesAreOwnerOnly() {
    var task = taskRepo.saveAndFlush(Task.personal(UUID.randomUUID(), member, "Private task"));
    var n = notes.createNote(member, request("TASK", null, null, task.getId()));
    assertEquals(n.id(), notes.getNote(member, n.id()).id());
    denied(() -> notes.getNote(manager, n.id()), 404);
    denied(() -> notes.createNote(outsider, request("TASK", null, null, task.getId())), 404);
  }

  @Test void cannotMoveToUnauthorizedProjectOrForgeResourceRelations() {
    var n = notes.createNote(member, request("PERSONAL", null, null, null));
    denied(() -> notes.updateNote(member, n.id(), request("PROJECT", ws, project, null), n.version()), 403);
    assertEquals("PERSONAL", notes.getNote(member, n.id()).scope());
    UUID otherWs = mkWorkspace(outsider, "other");
    denied(() -> notes.createNote(manager, request("PROJECT", otherWs, project, null)), 400);
    denied(() -> notes.createNote(member, request("INVALID", null, null, null)), 400);
  }

  @Test void staleVersionIsRejectedAndReturnedVersionIsCurrent() {
    var n = notes.createNote(member, request("PERSONAL", null, null, null));
    var updated = notes.updateNote(member, n.id(), new UpsertNoteRequest(null, null, null, null, "Changed", null, null, null), n.version());
    assertTrue(updated.version() > n.version());
    assertEquals(noteRepo.findById(n.id()).orElseThrow().getVersion(), updated.version());
    denied(() -> notes.updateNote(member, n.id(), request("PERSONAL", null, null, null), n.version()), 409);
  }

  @Test void httpRejectsMalformedVersionAndForeignReads() throws Exception {
    var n = notes.createNote(member, request("PERSONAL", null, null, null));
    var result = mvc.perform(put("/api/v1/notes/" + n.id())
        .header("Authorization", "Bearer " + memberToken).header("If-Match", "garbage")
        .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(Map.of("title", "x")))).andReturn();
    assertEquals(400, result.getResponse().getStatus());
    assertEquals(200, doGet("/api/v1/notes/" + n.id(), memberToken).getResponse().getStatus());
  }

  @Test void workspaceRemovalRevokesProjectAccessImmediately() {
    var n = notes.createNote(manager, request("PROJECT", ws, project, null));
    assertNotNull(notes.getNote(member, n.id()));
    workspaces.removeMember(manager, ws, member);
    assertTrue(projectMembers.findMembership(project, member).isEmpty());
    denied(() -> notes.getNote(member, n.id()), 404);
    denied(() -> notes.listNotes(member, null, project, null, null), 404);
  }

  @Test void leavingWorkspaceRevokesProjectAccessImmediately() {
    workspaces.leave(member, ws);
    assertTrue(projectMembers.findMembership(project, member).isEmpty());
  }
}
