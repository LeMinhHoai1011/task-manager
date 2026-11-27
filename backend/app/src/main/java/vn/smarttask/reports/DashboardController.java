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
  @GetMapping("/today")
  public ApiResult<?> today(@RequestParam(defaultValue="today") String bucket,
      @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="UTC") String timezone) {
    var rows = overview.today(CurrentActor.userId(),timezone,bucket,page);
    return ApiResult.ok(java.util.Map.of("items", rows.subList(0, Math.min(20, rows.size())),
        "has_more", rows.size() > 20), "dashboard.success");
  }
  @GetMapping
  public ApiResult<?> summary(@RequestParam(defaultValue="all") String scope,
      @RequestParam(required=false) UUID projectId,@RequestParam(required=false) UUID teamId,
      @RequestParam(required=false) OffsetDateTime from,@RequestParam(required=false) OffsetDateTime to,
      @RequestParam(defaultValue="UTC") String timezone) {
    return ApiResult.ok(overview.summary(CurrentActor.userId(),scope,projectId,teamId,from,to,timezone),"dashboard.success");
  }
}
