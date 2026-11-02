package vn.smarttask.reports;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import vn.smarttask.common.ApiResult;
import vn.smarttask.security.CurrentActor;
import vn.smarttask.task.service.TaskOverviewService;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {
  private final TaskOverviewService overview;
  public DashboardController(TaskOverviewService overview){this.overview=overview;}
  @GetMapping
  public ApiResult<?> summary(@RequestParam(defaultValue="all") String scope,
      @RequestParam(required=false) UUID projectId,@RequestParam(required=false) UUID teamId,
      @RequestParam(required=false) OffsetDateTime from,@RequestParam(required=false) OffsetDateTime to,
      @RequestParam(defaultValue="UTC") String timezone) {
    return ApiResult.ok(overview.summary(CurrentActor.userId(),scope,projectId,teamId,from,to,timezone),"dashboard.success");
  }
}
