package vn.smarttask.task;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import vn.smarttask.common.ApiResult;
import vn.smarttask.common.AppException;
import vn.smarttask.security.CurrentActor;
import vn.smarttask.task.service.TaskResponsibilityService;
@RestController
@RequestMapping("/api/v1/tasks/{id}")
public class TaskResponsibilityController {
  private final TaskResponsibilityService service;
  public TaskResponsibilityController(TaskResponsibilityService service) { this.service=service; }
  public record Transfer(UUID userId) {}
  @GetMapping("/primary-assignee")
  public ApiResult<?> current(@PathVariable UUID id) { return ApiResult.ok(service.current(CurrentActor.userId(),id),"task.primary.current"); }
  @GetMapping("/assignment-candidates")
  public ApiResult<?> candidates(@PathVariable UUID id) { return ApiResult.ok(service.candidates(CurrentActor.userId(),id),"task.assignment.candidates"); }
  @PostMapping("/primary-assignee")
  public ApiResult<?> transfer(@PathVariable UUID id,@RequestBody Transfer body,@RequestHeader("If-Match") TaskVersion version) {
    if(body.userId()==null) throw AppException.badRequest("USER_REQUIRED","User required");
    return ApiResult.ok(service.transfer(CurrentActor.userId(),id,body.userId(),version.value()),"task.primary.success");
  }
}
