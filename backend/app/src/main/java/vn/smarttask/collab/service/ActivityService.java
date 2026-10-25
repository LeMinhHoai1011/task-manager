package vn.smarttask.collab.service;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.smarttask.collab.entity.ActivityEvent;
import vn.smarttask.collab.entity.TaskComment;
import vn.smarttask.collab.repo.ActivityEventRepository;
import vn.smarttask.collab.repo.TaskCommentRepository;
import vn.smarttask.common.UuidV7;

/**
 * PH-3 Activity timeline: append-only; readers get summary_key + redacted payload.
 * If the viewer lost access, payload is masked at READ time (no rewrite of history).
 */
@Service
public class ActivityService {
  private final ActivityEventRepository events;
  private final TaskCommentRepository comments;

  public ActivityService(ActivityEventRepository events, TaskCommentRepository comments) {
    this.events = events; this.comments = comments;
  }

  @Transactional
  public ActivityEvent append(UUID taskId, UUID projectId, UUID actor, String kind, String payloadJson) {
    ActivityEvent e = new ActivityEvent(UuidV7.generate(), taskId, projectId, actor, kind,
        payloadJson == null ? "{}" : payloadJson);
    return events.save(e);
  }

  /** Timeline read with redaction flag when viewer lost access (history itself untouched). */
  public record TimelineEntry(UUID id, String kind, String payloadJson, boolean redacted) {}

  @Transactional(readOnly = true)
  public List<TimelineEntry> timeline(UUID taskId, boolean viewerHasRead) {
    return events.findByTaskIdOrderByCreatedAtAsc(taskId).stream()
        .map(e -> new TimelineEntry(e.getId(), e.getKind(),
            viewerHasRead ? e.getPayload() : "{\"redacted\":true}", !viewerHasRead))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<TaskComment> comments(UUID taskId, boolean viewerHasRead) {
    var all = comments.findByTaskIdOrderByCreatedAtAsc(taskId);
    if (viewerHasRead) return all;
    return List.of();
  }
}
