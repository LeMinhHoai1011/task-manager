package vn.smarttask.project;

import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import vn.smarttask.project.entity.ProgressLog;
import vn.smarttask.project.service.ProgressLogService.ProgressLogDto;
import vn.smarttask.project.service.ProgressLogService.UpsertProgressLogRequest;

public class ProgressLogServiceTest {

  @Test
  void progressLogEntity_clampPercentage() {
    UUID id = UUID.randomUUID();
    UUID projectId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 11, 8);

    ProgressLog log = new ProgressLog(id, projectId, date, 120, "Tóm tắt tiến độ", userId);
    assertEquals(100, log.getProgressPercent(), "Phần trăm phải bị clamp tối đa 100%");

    log.setProgressPercent(-10);
    assertEquals(0, log.getProgressPercent(), "Phần trăm phải bị clamp tối thiểu 0%");

    log.setCompletedWork("Xong 5 tính năng");
    log.setBlockers("Không có");
    log.setNextPlan("Deploy production");

    assertEquals("Xong 5 tính năng", log.getCompletedWork());
    assertEquals("Không có", log.getBlockers());
    assertEquals("Deploy production", log.getNextPlan());
    assertEquals(1, log.getVersion());
  }

  @Test
  void progressLogDto_mapping() {
    UUID id = UUID.randomUUID();
    UUID projectId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    LocalDate date = LocalDate.now();

    ProgressLog log = new ProgressLog(id, projectId, date, 85, "Cập nhật sprint", userId);
    log.setCompletedWork("Viết code backend & web");
    ProgressLogDto dto = ProgressLogDto.from(log);

    assertEquals(id, dto.id());
    assertEquals(projectId, dto.projectId());
    assertEquals(85, dto.progressPercent());
    assertEquals("Cập nhật sprint", dto.summary());
    assertEquals("Viết code backend & web", dto.completedWork());
  }

  @Test
  void upsertProgressLogRequest_contract() {
    LocalDate date = LocalDate.now();
    UpsertProgressLogRequest req = new UpsertProgressLogRequest(
        date, 90, "Summary", "Done work", "None", "Next sprint"
    );
    assertEquals(date, req.updateDate());
    assertEquals(90, req.progressPercent());
    assertEquals("Summary", req.summary());
  }
}
