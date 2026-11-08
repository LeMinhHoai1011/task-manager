package vn.smarttask.project.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.smarttask.common.AppException;
import vn.smarttask.common.UuidV7;
import vn.smarttask.project.entity.ProgressLog;
import vn.smarttask.project.repo.ProgressLogRepository;
import vn.smarttask.project.repo.ProjectMembershipRepository;
import vn.smarttask.project.repo.ProjectRepository;

@Service
public class ProgressLogService {
  private final ProgressLogRepository logs;
  private final ProjectRepository projects;
  private final ProjectMembershipRepository members;

  public ProgressLogService(ProgressLogRepository logs,
                            ProjectRepository projects,
                            ProjectMembershipRepository members) {
    this.logs = logs;
    this.projects = projects;
    this.members = members;
  }

  public record ProgressLogDto(
      UUID id,
      UUID projectId,
      LocalDate updateDate,
      int progressPercent,
      String summary,
      String completedWork,
      String blockers,
      String nextPlan,
      UUID createdBy,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt,
      long version
  ) {
    public static ProgressLogDto from(ProgressLog p) {
      return new ProgressLogDto(
          p.getId(), p.getProjectId(), p.getUpdateDate(), p.getProgressPercent(),
          p.getSummary(), p.getCompletedWork(), p.getBlockers(), p.getNextPlan(),
          p.getCreatedBy(), p.getCreatedAt(), p.getUpdatedAt(), p.getVersion()
      );
    }
  }

  public record UpsertProgressLogRequest(
      LocalDate updateDate,
      Integer progressPercent,
      String summary,
      String completedWork,
      String blockers,
      String nextPlan
  ) {}

  @Transactional(readOnly = true)
  public List<ProgressLogDto> list(UUID userId, UUID projectId, LocalDate from, LocalDate to) {
    verifyMembership(userId, projectId, false);
    List<ProgressLog> result;
    if (from != null && to != null) {
      result = logs.findByProjectIdAndDateRange(projectId, from, to);
    } else {
      result = logs.findByProjectIdOrderByUpdateDateDescCreatedAtDesc(projectId);
    }
    return result.stream().map(ProgressLogDto::from).toList();
  }

  @Transactional
  public ProgressLogDto create(UUID userId, UUID projectId, UpsertProgressLogRequest req) {
    verifyMembership(userId, projectId, true);
    if (req.summary() == null || req.summary().isBlank()) {
      throw AppException.badRequest("SUMMARY_REQUIRED", "Tóm tắt tiến độ không được để trống");
    }
    int percent = req.progressPercent() != null ? req.progressPercent() : 0;
    ProgressLog log = new ProgressLog(
        UuidV7.generate(),
        projectId,
        req.updateDate() != null ? req.updateDate() : LocalDate.now(),
        percent,
        req.summary().trim(),
        userId
    );
    log.setCompletedWork(req.completedWork());
    log.setBlockers(req.blockers());
    log.setNextPlan(req.nextPlan());

    logs.save(log);
    return ProgressLogDto.from(log);
  }

  @Transactional
  public ProgressLogDto update(UUID userId, UUID projectId, UUID logId, UpsertProgressLogRequest req, Long expectedVersion) {
    verifyMembership(userId, projectId, true);
    ProgressLog log = logs.findById(logId)
        .filter(l -> l.getProjectId().equals(projectId))
        .orElseThrow(() -> AppException.notFound("Không tìm thấy nhật ký tiến độ"));

    if (expectedVersion != null && log.getVersion() != expectedVersion) {
      throw AppException.stateConflict("VERSION_CONFLICT", "Nhật ký tiến độ đã bị thay đổi bởi phiên khác");
    }

    if (req.updateDate() != null) log.setUpdateDate(req.updateDate());
    if (req.progressPercent() != null) log.setProgressPercent(req.progressPercent());
    if (req.summary() != null && !req.summary().isBlank()) log.setSummary(req.summary().trim());
    if (req.completedWork() != null) log.setCompletedWork(req.completedWork());
    if (req.blockers() != null) log.setBlockers(req.blockers());
    if (req.nextPlan() != null) log.setNextPlan(req.nextPlan());

    logs.save(log);
    return ProgressLogDto.from(log);
  }

  @Transactional
  public void delete(UUID userId, UUID projectId, UUID logId) {
    verifyMembership(userId, projectId, true);
    ProgressLog log = logs.findById(logId)
        .filter(l -> l.getProjectId().equals(projectId))
        .orElseThrow(() -> AppException.notFound("Không tìm thấy nhật ký tiến độ"));

    logs.delete(log);
  }

  private void verifyMembership(UUID userId, UUID projectId, boolean requireWrite) {
    if (!projects.existsById(projectId)) {
      throw AppException.notFound("Không tìm thấy dự án");
    }
    var membership = members.findByProjectIdAndUserId(projectId, userId);
    if (membership.isEmpty() || !"ACTIVE".equals(membership.get().getStatus())) {
      throw AppException.forbidden("Bạn không phải thành viên của dự án này");
    }
    if (requireWrite) {
      String role = membership.get().getRole();
      if ("GUEST".equalsIgnoreCase(role)) {
        throw AppException.forbidden("Khách không có quyền cập nhật tiến độ");
      }
    }
  }
}
