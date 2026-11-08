package vn.smarttask.project;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.smarttask.common.ApiResult;
import vn.smarttask.project.service.ProgressLogService;
import vn.smarttask.project.service.ProgressLogService.ProgressLogDto;
import vn.smarttask.project.service.ProgressLogService.UpsertProgressLogRequest;
import vn.smarttask.security.CurrentActor;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/progress-logs")
public class ProgressLogController {
  private final ProgressLogService progressLogs;

  public ProgressLogController(ProgressLogService progressLogs) {
    this.progressLogs = progressLogs;
  }

  @GetMapping
  public ResponseEntity<ApiResult<List<ProgressLogDto>>> list(
      @PathVariable UUID projectId,
      @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    UUID userId = CurrentActor.userId();
    var list = progressLogs.list(userId, projectId, from, to);
    return ResponseEntity.ok(ApiResult.ok(list, "progress_log.list.success"));
  }

  @PostMapping
  public ResponseEntity<ApiResult<ProgressLogDto>> create(
      @PathVariable UUID projectId,
      @RequestBody UpsertProgressLogRequest req) {
    UUID userId = CurrentActor.userId();
    return ResponseEntity.ok(ApiResult.ok(progressLogs.create(userId, projectId, req), "progress_log.create.success"));
  }

  @PutMapping("/{logId}")
  public ResponseEntity<ApiResult<ProgressLogDto>> update(
      @PathVariable UUID projectId,
      @PathVariable UUID logId,
      @RequestBody UpsertProgressLogRequest req,
      @RequestHeader(value = "If-Match", required = false) String ifMatch) {
    UUID userId = CurrentActor.userId();
    Long expectedVersion = null;
    if (ifMatch != null && !ifMatch.isBlank()) {
      try {
        expectedVersion = Long.parseLong(ifMatch.replace("\"", "").trim());
      } catch (NumberFormatException ignored) {}
    }
    return ResponseEntity.ok(ApiResult.ok(progressLogs.update(userId, projectId, logId, req, expectedVersion), "progress_log.update.success"));
  }

  @DeleteMapping("/{logId}")
  public ResponseEntity<ApiResult<Map<String, String>>> delete(
      @PathVariable UUID projectId,
      @PathVariable UUID logId) {
    UUID userId = CurrentActor.userId();
    progressLogs.delete(userId, projectId, logId);
    return ResponseEntity.ok(ApiResult.ok(Map.of("id", logId.toString()), "progress_log.delete.success"));
  }
}
