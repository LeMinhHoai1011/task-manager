package vn.smarttask.task;

import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import vn.smarttask.common.ApiResult;
import vn.smarttask.security.CurrentActor;
import vn.smarttask.task.service.PersonalTaskRecurrenceService;

@RestController
@RequestMapping("/api/v1/me/task-recurrences/{id}")
public class PersonalTaskRecurrenceController {
  private final PersonalTaskRecurrenceService service;

  public PersonalTaskRecurrenceController(PersonalTaskRecurrenceService service) {
    this.service = service;
  }

  public record Schedule(int intervalDays, long version) {}

  @GetMapping
  public ApiResult<?> status(@PathVariable UUID id) {
    return ApiResult.ok(service.status(CurrentActor.userId(), id), "personal.detail.success");
  }

  @PutMapping
  public ApiResult<?> start(@PathVariable UUID id, @RequestBody Schedule schedule) {
    return ApiResult.ok(service.start(CurrentActor.userId(), id, schedule.intervalDays(), schedule.version()),
        "personal.update.success");
  }

  @DeleteMapping
  public ApiResult<?> stop(@PathVariable UUID id) {
    return ApiResult.ok(service.stop(CurrentActor.userId(), id), "personal.update.success");
  }
}
