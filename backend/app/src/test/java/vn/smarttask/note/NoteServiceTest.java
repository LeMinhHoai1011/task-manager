package vn.smarttask.note;

import java.time.OffsetDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import vn.smarttask.note.entity.Note;
import vn.smarttask.note.service.NoteService.NoteDto;
import vn.smarttask.note.service.NoteService.UpsertNoteRequest;

public class NoteServiceTest {

  @Test
  void noteEntity_creationAndFields() {
    UUID id = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    Note note = new Note(id, userId, "PERSONAL", "Tiêu đề V3", "# Nội dung");
    note.setTags("v3,markdown,fast");
    note.setPinned(true);

    assertEquals(id, note.getId());
    assertEquals(userId, note.getOwnerUserId());
    assertEquals("PERSONAL", note.getScope());
    assertEquals("Tiêu đề V3", note.getTitle());
    assertEquals("# Nội dung", note.getContent());
    assertEquals("v3,markdown,fast", note.getTags());
    assertTrue(note.isPinned());
    assertEquals(1, note.getVersion());
  }

  @Test
  void noteDto_mapping() {
    UUID id = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    Note note = new Note(id, userId, "PROJECT", "Dự án V3", "Tiến độ");
    note.setTags("project,important");
    note.setPinned(false);

    NoteDto dto = NoteDto.from(note);
    assertEquals(id, dto.id());
    assertEquals(userId, dto.ownerUserId());
    assertEquals("PROJECT", dto.scope());
    assertEquals("Dự án V3", dto.title());
    assertEquals("Tiến độ", dto.content());
    assertEquals("project,important", dto.tags());
    assertFalse(dto.isPinned());
  }

  @Test
  void upsertNoteRequest_recordContract() {
    UUID wsId = UUID.randomUUID();
    UUID projId = UUID.randomUUID();
    UpsertNoteRequest req = new UpsertNoteRequest(
        wsId, projId, null, "SHARED", "Ghi chú chung", "Chi tiết", "shared", true
    );
    assertEquals(wsId, req.workspaceId());
    assertEquals(projId, req.projectId());
    assertNull(req.taskId());
    assertEquals("SHARED", req.scope());
    assertEquals("Ghi chú chung", req.title());
    assertTrue(req.isPinned());
  }
}
