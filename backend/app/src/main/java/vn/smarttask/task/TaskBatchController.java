package vn.smarttask.task;

import java.util.List;
import org.springframework.web.bind.annotation.*;
import vn.smarttask.common.ApiResult;
import vn.smarttask.security.CurrentActor;
import vn.smarttask.security.RequiresPermission;
import vn.smarttask.task.service.TaskBatchService;

@RestController
@RequestMapping("/api/v1/tasks/batch")
public class TaskBatchController {
  private final TaskBatchService service;

  public TaskBatchController(TaskBatchService service) { this.service = service; }

  @PatchMapping
  @RequiresPermission("task:write")
  public ApiResult<List<TaskBatchService.Result>> apply(
      @RequestBody TaskBatchService.Request request,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    return ApiResult.ok(service.apply(CurrentActor.userId(), request, key), "task.batch.success");
  }
}
