package vn.smarttask.note;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.smarttask.common.ApiResult;
import vn.smarttask.note.service.NoteService;
import vn.smarttask.note.service.NoteService.NoteDto;
import vn.smarttask.note.service.NoteService.UpsertNoteRequest;
import vn.smarttask.security.CurrentActor;

@RestController
@RequestMapping("/api/v1/notes")
public class NoteController {
  private final NoteService notes;

  public NoteController(NoteService notes) {
    this.notes = notes;
  }

  @GetMapping
  public ResponseEntity<ApiResult<List<NoteDto>>> list(
      @RequestParam(value = "scope", required = false) String scope,
      @RequestParam(value = "project_id", required = false) UUID projectId,
      @RequestParam(value = "task_id", required = false) UUID taskId,
      @RequestParam(value = "search", required = false) String search) {
    UUID userId = CurrentActor.userId();
    var list = notes.listNotes(userId, scope, projectId, taskId, search);
    return ResponseEntity.ok(ApiResult.ok(list, "notes.list.success"));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResult<NoteDto>> get(@PathVariable UUID id) {
    UUID userId = CurrentActor.userId();
    return ResponseEntity.ok(ApiResult.ok(notes.getNote(userId, id), "notes.get.success"));
  }

  @PostMapping
  public ResponseEntity<ApiResult<NoteDto>> create(@RequestBody UpsertNoteRequest req) {
    UUID userId = CurrentActor.userId();
    return ResponseEntity.ok(ApiResult.ok(notes.createNote(userId, req), "notes.create.success"));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResult<NoteDto>> update(
      @PathVariable UUID id,
      @RequestBody UpsertNoteRequest req,
      @RequestHeader(value = "If-Match", required = false) String ifMatch) {
    UUID userId = CurrentActor.userId();
    Long expectedVersion = null;
    if (ifMatch != null && !ifMatch.isBlank()) {
      try {
        expectedVersion = Long.parseLong(ifMatch.replace("\"", "").trim());
      } catch (NumberFormatException ex) {
        throw vn.smarttask.common.AppException.badRequest("INVALID_IF_MATCH", "Invalid If-Match version");
      }
    }
    return ResponseEntity.ok(ApiResult.ok(notes.updateNote(userId, id, req, expectedVersion), "notes.update.success"));
  }

  @PatchMapping("/{id}/pin")
  public ResponseEntity<ApiResult<NoteDto>> togglePin(@PathVariable UUID id) {
    UUID userId = CurrentActor.userId();
    return ResponseEntity.ok(ApiResult.ok(notes.togglePin(userId, id), "notes.pin.success"));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResult<Map<String, String>>> delete(@PathVariable UUID id) {
    UUID userId = CurrentActor.userId();
    notes.deleteNote(userId, id);
    return ResponseEntity.ok(ApiResult.ok(Map.of("id", id.toString()), "notes.delete.success"));
  }

  @PostMapping("/{id}/restore")
  public ResponseEntity<ApiResult<NoteDto>> restore(@PathVariable UUID id) {
    UUID userId = CurrentActor.userId();
    return ResponseEntity.ok(ApiResult.ok(notes.restoreNote(userId, id), "notes.restore.success"));
  }
}
