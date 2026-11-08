package vn.smarttask.note.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.smarttask.common.AppException;
import vn.smarttask.common.UuidV7;
import vn.smarttask.note.entity.Note;
import vn.smarttask.note.repo.NoteRepository;
import vn.smarttask.project.repo.ProjectMembershipRepository;
import vn.smarttask.workspace.repo.WorkspaceMembershipRepository;

@Service
public class NoteService {
  private final NoteRepository notes;
  private final ProjectMembershipRepository projectMembers;
  private final WorkspaceMembershipRepository wsMembers;

  public NoteService(NoteRepository notes,
                     ProjectMembershipRepository projectMembers,
                     WorkspaceMembershipRepository wsMembers) {
    this.notes = notes;
    this.projectMembers = projectMembers;
    this.wsMembers = wsMembers;
  }

  public record NoteDto(
      UUID id,
      UUID workspaceId,
      UUID projectId,
      UUID taskId,
      UUID ownerUserId,
      String scope,
      String title,
      String content,
      String tags,
      boolean isPinned,
      long version,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt
  ) {
    public static NoteDto from(Note n) {
      return new NoteDto(
          n.getId(), n.getWorkspaceId(), n.getProjectId(), n.getTaskId(),
          n.getOwnerUserId(), n.getScope(), n.getTitle(), n.getContent(),
          n.getTags(), n.isPinned(), n.getVersion(), n.getCreatedAt(), n.getUpdatedAt()
      );
    }
  }

  public record UpsertNoteRequest(
      UUID workspaceId,
      UUID projectId,
      UUID taskId,
      String scope,
      String title,
      String content,
      String tags,
      Boolean isPinned
  ) {}

  @Transactional(readOnly = true)
  public List<NoteDto> listNotes(UUID userId, String scope, UUID projectId, UUID taskId, String search) {
    List<Note> list;
    if (projectId != null) {
      list = notes.findByProjectId(projectId);
    } else if (taskId != null) {
      list = notes.findByTaskId(taskId);
    } else if ("PERSONAL".equalsIgnoreCase(scope)) {
      list = notes.findPersonalNotes(userId);
    } else {
      List<UUID> activeProjectIds = projectMembers.findActiveProjectIdsByUser(userId);
      List<UUID> activeWsIds = wsMembers.findByUserId(userId).stream()
          .filter(m -> "ACTIVE".equals(m.getStatus()))
          .map(m -> m.getWorkspaceId())
          .toList();
      list = notes.findAccessibleNotes(userId,
          activeProjectIds.isEmpty() ? List.of(UUID.randomUUID()) : activeProjectIds,
          activeWsIds.isEmpty() ? List.of(UUID.randomUUID()) : activeWsIds);
    }

    if (search != null && !search.isBlank()) {
      String q = search.trim().toLowerCase();
      list = list.stream().filter(n ->
          (n.getTitle() != null && n.getTitle().toLowerCase().contains(q)) ||
          (n.getContent() != null && n.getContent().toLowerCase().contains(q)) ||
          (n.getTags() != null && n.getTags().toLowerCase().contains(q))
      ).toList();
    }

    return list.stream().map(NoteDto::from).toList();
  }

  @Transactional(readOnly = true)
  public NoteDto getNote(UUID userId, UUID noteId) {
    Note n = findAndAuthorize(userId, noteId, false);
    return NoteDto.from(n);
  }

  @Transactional
  public NoteDto createNote(UUID userId, UpsertNoteRequest req) {
    if (req.title() == null || req.title().isBlank()) {
      throw AppException.badRequest("TITLE_REQUIRED", "Tiêu đề ghi chú không được để trống");
    }
    String scope = req.scope() != null ? req.scope().toUpperCase() : "PERSONAL";
    if (!List.of("PERSONAL", "PROJECT", "SHARED", "TASK").contains(scope)) {
      scope = "PERSONAL";
    }

    Note note = new Note(UuidV7.generate(), userId, scope, req.title().trim(), req.content());
    note.setWorkspaceId(req.workspaceId());
    note.setProjectId(req.projectId());
    note.setTaskId(req.taskId());
    note.setTags(req.tags());
    if (req.isPinned() != null) {
      note.setPinned(req.isPinned());
    }
    notes.save(note);
    return NoteDto.from(note);
  }

  @Transactional
  public NoteDto updateNote(UUID userId, UUID noteId, UpsertNoteRequest req, Long expectedVersion) {
    Note note = findAndAuthorize(userId, noteId, true);
    if (expectedVersion != null && note.getVersion() != expectedVersion) {
      throw AppException.stateConflict("VERSION_CONFLICT", "Ghi chú đã bị thay đổi bởi phiên khác");
    }
    if (req.title() != null && !req.title().isBlank()) {
      note.setTitle(req.title().trim());
    }
    if (req.content() != null) {
      note.setContent(req.content());
    }
    if (req.tags() != null) {
      note.setTags(req.tags());
    }
    if (req.scope() != null) {
      note.setScope(req.scope().toUpperCase());
    }
    if (req.isPinned() != null) {
      note.setPinned(req.isPinned());
    }
    if (req.projectId() != null) {
      note.setProjectId(req.projectId());
    }
    if (req.taskId() != null) {
      note.setTaskId(req.taskId());
    }
    notes.save(note);
    return NoteDto.from(note);
  }

  @Transactional
  public NoteDto togglePin(UUID userId, UUID noteId) {
    Note note = findAndAuthorize(userId, noteId, true);
    note.setPinned(!note.isPinned());
    notes.save(note);
    return NoteDto.from(note);
  }

  @Transactional
  public void deleteNote(UUID userId, UUID noteId) {
    Note note = findAndAuthorize(userId, noteId, true);
    note.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
    notes.save(note);
  }

  @Transactional
  public NoteDto restoreNote(UUID userId, UUID noteId) {
    Note note = notes.findById(noteId).orElseThrow(() -> AppException.notFound("Không tìm thấy ghi chú"));
    if (!note.getOwnerUserId().equals(userId)) {
      throw AppException.forbidden("Chỉ chủ sở hữu mới có quyền khôi phục");
    }
    note.setDeletedAt(null);
    notes.save(note);
    return NoteDto.from(note);
  }

  private Note findAndAuthorize(UUID userId, UUID noteId, boolean requireWrite) {
    Note note = notes.findByIdAndDeletedAtIsNull(noteId)
        .orElseThrow(() -> AppException.notFound("Không tìm thấy ghi chú"));

    if (note.getOwnerUserId().equals(userId)) {
      return note;
    }

    if ("PERSONAL".equals(note.getScope())) {
      throw AppException.notFound("Không tìm thấy ghi chú");
    }

    if ("PROJECT".equals(note.getScope()) && note.getProjectId() != null) {
      boolean isMember = projectMembers.findByProjectIdAndUserId(note.getProjectId(), userId)
          .map(m -> "ACTIVE".equals(m.getStatus()))
          .orElse(false);
      if (!isMember) {
        throw AppException.forbidden("Không có quyền truy cập ghi chú dự án này");
      }
      return note;
    }

    if ("SHARED".equals(note.getScope()) && note.getWorkspaceId() != null) {
      boolean isWsMember = wsMembers.findByWorkspaceIdAndUserId(note.getWorkspaceId(), userId)
          .map(m -> "ACTIVE".equals(m.getStatus()))
          .orElse(false);
      if (!isWsMember) {
        throw AppException.forbidden("Không có quyền truy cập ghi chú không gian này");
      }
      return note;
    }

    if (requireWrite) {
      throw AppException.forbidden("Bạn không có quyền chỉnh sửa ghi chú này");
    }
    return note;
  }
}
