package vn.smarttask.note.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
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
import vn.smarttask.project.repo.ProjectRepository;
import vn.smarttask.task.repo.TaskRepository;
import vn.smarttask.security.PolicyEnforcer;

@Service
public class NoteService {
  private final NoteRepository notes;
  private final ProjectMembershipRepository projectMembers;
  private final WorkspaceMembershipRepository wsMembers;
  private final ProjectRepository projects;
  private final TaskRepository tasks;
  private final PolicyEnforcer policy;

  public NoteService(NoteRepository notes,
                     ProjectMembershipRepository projectMembers,
                     WorkspaceMembershipRepository wsMembers, ProjectRepository projects,
                     TaskRepository tasks, PolicyEnforcer policy) {
    this.notes = notes;
    this.projectMembers = projectMembers;
    this.wsMembers = wsMembers;
    this.projects = projects;
    this.tasks = tasks;
    this.policy = policy;
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
    if (projectId != null) policy.requireProjectRead(userId, projectId);
    if (taskId != null) authorizeTask(userId, taskId, false);
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

    // Repository results are candidates, never an authorization decision.
    list = list.stream().filter(n -> canRead(userId, n))
        .filter(n -> taskId == null || taskId.equals(n.getTaskId()))
        .filter(n -> scope == null || "ALL".equalsIgnoreCase(scope) || scope.equalsIgnoreCase(n.getScope()))
        .toList();
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
    String scope = normalizeScope(req.scope() == null ? "PERSONAL" : req.scope());

    Note note = new Note(UuidV7.generate(), userId, scope, req.title().trim(), req.content());
    note.setWorkspaceId(req.workspaceId());
    note.setProjectId(req.projectId());
    note.setTaskId(req.taskId());
    validateTarget(userId, note);
    note.setTags(req.tags());
    if (req.isPinned() != null) {
      note.setPinned(req.isPinned());
    }
    return NoteDto.from(notes.saveAndFlush(note));
  }

  @Transactional
  public NoteDto updateNote(UUID userId, UUID noteId, UpsertNoteRequest req, Long expectedVersion) {
    Note note = findAndAuthorize(userId, noteId, true);
    if (expectedVersion != null && note.getVersion() != expectedVersion) {
      throw AppException.stateConflict("VERSION_CONFLICT", "Ghi chú đã bị thay đổi bởi phiên khác");
    }
    boolean moving = (req.scope() != null && !req.scope().equalsIgnoreCase(note.getScope()))
        || (req.projectId() != null && !req.projectId().equals(note.getProjectId()))
        || (req.taskId() != null && !req.taskId().equals(note.getTaskId()))
        || (req.workspaceId() != null && !req.workspaceId().equals(note.getWorkspaceId()));
    if (moving && !note.getOwnerUserId().equals(userId)) {
      throw AppException.forbidden("Only the owner can move a note");
    }
    if (req.title() != null && req.title().isBlank()) {
      throw AppException.badRequest("TITLE_REQUIRED", "Title is required");
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
      note.setScope(normalizeScope(req.scope()));
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
    if (req.workspaceId() != null) note.setWorkspaceId(req.workspaceId());
    validateTarget(userId, note);
    return NoteDto.from(notes.saveAndFlush(note));
  }

  @Transactional
  public NoteDto togglePin(UUID userId, UUID noteId) {
    Note note = findAndAuthorize(userId, noteId, true);
    note.setPinned(!note.isPinned());
    return NoteDto.from(notes.saveAndFlush(note));
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
    authorize(userId, note, true);
    note.setDeletedAt(null);
    return NoteDto.from(notes.saveAndFlush(note));
  }

  private Note findAndAuthorize(UUID userId, UUID noteId, boolean requireWrite) {
    Note note = notes.findByIdAndDeletedAtIsNull(noteId)
        .orElseThrow(() -> AppException.notFound("Không tìm thấy ghi chú"));

    authorize(userId, note, requireWrite);
    return note;
  }

  private boolean canRead(UUID userId, Note note) {
    try {
      authorize(userId, note, false);
      return true;
    } catch (AppException ex) {
      if (ex.getHttpStatus().value() == 403 || ex.getHttpStatus().value() == 404) return false;
      throw ex;
    }
  }

  private void authorize(UUID userId, Note note, boolean write) {
    switch (note.getScope()) {
      case "PERSONAL" -> {
        if (!note.getOwnerUserId().equals(userId)) throw AppException.notFound("Note not found");
      }
      case "PROJECT" -> {
        if (note.getProjectId() == null) throw AppException.notFound("Note not found");
        if (write) policy.requireProjectWrite(userId, note.getProjectId());
        else policy.requireProjectRead(userId, note.getProjectId());
      }
      case "TASK" -> {
        if (note.getTaskId() == null) throw AppException.notFound("Note not found");
        authorizeTask(userId, note.getTaskId(), write);
      }
      case "SHARED" -> {
        if (note.getWorkspaceId() == null) throw AppException.notFound("Note not found");
        policy.requireWorkspaceRead(userId, note.getWorkspaceId());
        if (write) {
          policy.requireWorkspaceWrite(userId, note.getWorkspaceId());
          if (!note.getOwnerUserId().equals(userId)) {
            policy.requireWorkspaceManagement(userId, note.getWorkspaceId());
          }
        }
      }
      default -> throw AppException.notFound("Note not found");
    }
  }

  private void authorizeTask(UUID userId, UUID taskId, boolean write) {
    var task = tasks.findById(taskId).filter(t -> t.getDeletedAt() == null)
        .orElseThrow(() -> AppException.notFound("Task not found"));
    if ("PERSONAL".equals(task.getScope())) {
      if (!userId.equals(task.getOwnerUserId())) throw AppException.notFound("Task not found");
    } else if (write) policy.requireTaskWrite(userId, taskId);
    else policy.requireTaskRead(userId, taskId);
  }

  private String normalizeScope(String scope) {
    String value = scope.toUpperCase(Locale.ROOT);
    if (!List.of("PERSONAL", "PROJECT", "SHARED", "TASK").contains(value)) {
      throw AppException.badRequest("INVALID_SCOPE", "Unknown note scope");
    }
    return value;
  }

  private void validateTarget(UUID userId, Note note) {
    switch (note.getScope()) {
      case "PERSONAL" -> {
        note.setWorkspaceId(null); note.setProjectId(null); note.setTaskId(null);
      }
      case "PROJECT" -> {
        if (note.getProjectId() == null) throw AppException.badRequest("PROJECT_REQUIRED", "Project is required");
        policy.requireProjectWrite(userId, note.getProjectId());
        var project = projects.findById(note.getProjectId()).orElseThrow(() -> AppException.notFound("Project not found"));
        if (note.getWorkspaceId() != null && !note.getWorkspaceId().equals(project.getWorkspaceId())) {
          throw AppException.badRequest("SCOPE_MISMATCH", "Project does not belong to workspace");
        }
        note.setWorkspaceId(project.getWorkspaceId()); note.setTaskId(null);
      }
      case "SHARED" -> {
        if (note.getWorkspaceId() == null) throw AppException.badRequest("WORKSPACE_REQUIRED", "Workspace is required");
        note.setProjectId(null); note.setTaskId(null);
      }
      case "TASK" -> {
        if (note.getTaskId() == null) throw AppException.badRequest("TASK_REQUIRED", "Task is required");
        authorizeTask(userId, note.getTaskId(), true);
        var task = tasks.findById(note.getTaskId()).orElseThrow(() -> AppException.notFound("Task not found"));
        if (note.getProjectId() != null && !note.getProjectId().equals(task.getProjectId())) {
          throw AppException.badRequest("SCOPE_MISMATCH", "Task does not belong to project");
        }
        note.setProjectId(task.getProjectId());
        UUID workspaceId = task.getProjectId() == null ? null : projects.findById(task.getProjectId())
            .orElseThrow(() -> AppException.notFound("Project not found")).getWorkspaceId();
        if (note.getWorkspaceId() != null && !note.getWorkspaceId().equals(workspaceId)) {
          throw AppException.badRequest("SCOPE_MISMATCH", "Task does not belong to workspace");
        }
        note.setWorkspaceId(workspaceId);
      }
      default -> throw AppException.badRequest("INVALID_SCOPE", "Unknown note scope");
    }
    authorize(userId, note, true);
    if (note.getTitle().length() > 255 || (note.getTags() != null && note.getTags().length() > 1000)) {
      throw AppException.badRequest("NOTE_TOO_LONG", "Note title or tags exceed the limit");
    }
  }
}
