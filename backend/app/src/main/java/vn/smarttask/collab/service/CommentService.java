package vn.smarttask.collab.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.smarttask.collab.entity.TaskComment;
import vn.smarttask.collab.repo.TaskCommentRepository;
import vn.smarttask.common.AppException;
import vn.smarttask.common.UuidV7;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.common.service.OutboxService;
import vn.smarttask.project.repo.ProjectMembershipRepository;
import vn.smarttask.realtime.RealtimePublisher;
import vn.smarttask.task.repo.TaskRepository;

/** CM-01..05: project guest cannot comment, depth<=1, edit/delete own, delete-other PM+reason. */
@Service
public class CommentService {
  private final TaskCommentRepository comments;
  private final TaskRepository tasks;
  private final ProjectMembershipRepository projectMembers;
  private final AuditService audit;
  private final OutboxService outbox;

  public CommentService(TaskCommentRepository comments, TaskRepository tasks,
      ProjectMembershipRepository projectMembers, AuditService audit, OutboxService outbox) {
    this.comments = comments; this.tasks = tasks;
    this.projectMembers = projectMembers; this.audit = audit; this.outbox = outbox;
  }

  /** Optional in plain-unit tests; production injects it. Signals are advisory. */
  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private RealtimePublisher realtime;

  private void signal(UUID projectId, String kind, UUID taskId) {
    if (realtime != null) realtime.project(projectId, kind, taskId);
  }

  private void requireCanComment(UUID projectId, UUID actor) {
    var m = projectMembers.findMembership(projectId, actor)
        .orElseThrow(() -> AppException.notFound("Project not found"));
    if ("GUEST".equals(m.getRole())) {
      audit.appendDenied(actor, "comment.create", "task", null, "GUEST_DENIED");
      throw AppException.forbidden("Project guest cannot comment");
    }
  }

  private boolean isManager(UUID projectId, UUID actor) {
    return projectMembers.findMembership(projectId, actor)
        .map(m -> "MANAGER".equals(m.getRole())).orElse(false);
  }

  @Transactional(readOnly = true)
  public List<TaskComment> list(UUID taskId, boolean viewerHasRead) {
    if (!viewerHasRead) return List.of();
    return comments.findByTaskIdOrderByCreatedAtAsc(taskId).stream()
        .filter(c -> c.getDeletedAt() == null)
        .toList();
  }

  @Transactional
  public TaskComment add(UUID actor, UUID taskId, String body, UUID parentCommentId) {
    var t = tasks.findById(taskId).orElseThrow(() -> AppException.notFound("Task not found"));
    requireCanComment(t.getProjectId(), actor);
    if (body == null || body.isBlank()) throw AppException.badRequest("BODY_REQUIRED", "Body required");
    int depth = 0;
    if (parentCommentId != null) {
      var parent = comments.findById(parentCommentId)
          .orElseThrow(() -> AppException.notFound("Parent comment not found"));
      if (!parent.getTaskId().equals(taskId)) {
        throw AppException.badRequest("PARENT_TASK_MISMATCH", "Parent belongs to another task");
      }
      depth = parent.getDepth() + 1;
      if (depth > 1) {
        audit.appendDenied(actor, "comment.create", "task", taskId, "COMMENT_MAX_DEPTH_EXCEEDED");
        throw AppException.unprocessable("COMMENT_MAX_DEPTH_EXCEEDED", "Comment depth max 1");
      }
    }
    TaskComment c = new TaskComment(UuidV7.generate(), taskId, actor, body.trim(), depth, parentCommentId);
    comments.save(c);
    audit.append(actor, "comment.create", "task", taskId, "ALLOWED", null, "{}");
    outbox.emit("task", taskId, "task.comment.added", "{}");
    signal(t.getProjectId(), "COMMENT_ADDED", taskId);
    return c;
  }

  @Transactional
  public TaskComment edit(UUID actor, UUID commentId, String body) {
    var c = comments.findById(commentId).orElseThrow(() -> AppException.notFound("Comment not found"));
    if (!c.getAuthorUserId().equals(actor)) {
      audit.appendDenied(actor, "comment.edit", "task", c.getTaskId(), "NOT_OWNER");
      throw AppException.forbidden("Only author can edit");
    }
    if (c.getDeletedAt() != null) throw AppException.stateConflict("COMMENT_DELETED", "Comment deleted");
    if (body == null || body.isBlank()) throw AppException.badRequest("BODY_REQUIRED", "Body required");
    var t = tasks.findById(c.getTaskId()).orElseThrow(() -> AppException.notFound("Task not found"));
    c.setBody(body.trim());
    comments.save(c);
    audit.append(actor, "comment.edit", "task", c.getTaskId(), "ALLOWED", null, "{}");
    signal(t.getProjectId(), "COMMENT_UPDATED", c.getTaskId());
    return c;
  }

  @Transactional
  public void delete(UUID actor, UUID commentId, String reason) {
    var c = comments.findById(commentId).orElseThrow(() -> AppException.notFound("Comment not found"));
    var t = tasks.findById(c.getTaskId()).orElseThrow(() -> AppException.notFound("Task not found"));
    if (c.getAuthorUserId().equals(actor)) {
      c.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
      c.setDeleteReason(reason);
      comments.save(c);
      audit.append(actor, "comment.delete", "task", c.getTaskId(), "ALLOWED", null, "{}");
      signal(t.getProjectId(), "COMMENT_DELETED", c.getTaskId());
      return;
    }
    // delete other: PM + reason required
    if (!isManager(t.getProjectId(), actor)) {
      audit.appendDenied(actor, "comment.delete", "task", c.getTaskId(), "NOT_OWNER");
      throw AppException.forbidden("Only author or PM can delete");
    }
    if (reason == null || reason.trim().length() < 3) {
      throw AppException.badRequest("DELETE_REASON_REQUIRED", "PM deleting other comment needs reason");
    }
    c.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
    c.setDeleteReason(reason.trim());
    comments.save(c);
    audit.append(actor, "comment.delete", "task", c.getTaskId(), "ALLOWED", "PM_DELETE_OTHER", "{}");
    signal(t.getProjectId(), "COMMENT_DELETED", c.getTaskId());
  }
}
