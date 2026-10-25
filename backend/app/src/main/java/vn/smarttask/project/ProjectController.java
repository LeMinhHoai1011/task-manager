package vn.smarttask.project;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.smarttask.common.ApiResult;
import vn.smarttask.common.AppException;
import vn.smarttask.common.PageResponse;
import vn.smarttask.project.repo.MilestoneRepository;
import vn.smarttask.project.service.ProjectService;
import vn.smarttask.security.CurrentActor;
import vn.smarttask.security.PolicyEnforcer;
import vn.smarttask.security.RequiresPermission;

@RestController
@RequestMapping("/api/v1")
public class ProjectController {
  private final ProjectService projects;
  private final PolicyEnforcer policy;
  private final MilestoneRepository milestones;

  public ProjectController(ProjectService projects, PolicyEnforcer policy,
      MilestoneRepository milestones) {
    this.projects = projects;
    this.policy = policy;
    this.milestones = milestones;
  }

  public record ProjectDto(@JsonProperty("id") UUID id,
      @JsonProperty("workspace_id") UUID workspaceId,
      @JsonProperty("name") String name,
      @JsonProperty("status") String status,
      @JsonProperty("created_at") OffsetDateTime createdAt) {}
  public record MemberDto(@JsonProperty("user_id") UUID userId,
      @JsonProperty("role") String role) {}
  public record MilestoneDto(@JsonProperty("id") UUID id,
      @JsonProperty("project_id") UUID projectId,
      @JsonProperty("title") String title,
      @JsonProperty("due_at") OffsetDateTime dueAt) {}

  private static long ifMatch(String v) {
    if (v == null) throw AppException.ifMatchRequired();
    try {
      return Long.parseLong(v.replace("W/", "").replace("\"", "").trim());
    } catch (NumberFormatException e) {
      throw AppException.badRequest("IF_MATCH_INVALID", "If-Match invalid");
    }
  }

  private static ProjectDto toDto(vn.smarttask.project.entity.Project p) {
    return new ProjectDto(p.getId(), p.getWorkspaceId(), p.getName(), p.getStatus(), null);
  }

  @GetMapping("/projects")
  @RequiresPermission("project:read")
  public ResponseEntity<ApiResult<PageResponse<ProjectDto>>> listProjects(
      @RequestParam(required = false) UUID workspaceId,
      @RequestParam(required = false, name = "workspace_id") UUID workspaceIdAlias,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false, defaultValue = "20") int limit) {
    UUID actor = CurrentActor.userId();
    UUID ws = workspaceId != null ? workspaceId : workspaceIdAlias;
    if (ws != null) {
      policy.requireWorkspaceRead(actor, ws);
    }
    int pageSize = Math.min(Math.max(limit, 1), 100);
    var all = projects.listForUser(actor, ws);
    int start = 0;
    if (cursor != null && !cursor.isBlank()) {
      for (int i = 0; i < all.size(); i++) {
        if (all.get(i).getId().toString().equals(cursor)) { start = i + 1; break; }
      }
    }
    int end = Math.min(start + pageSize, all.size());
    var page = all.subList(start, end).stream().map(ProjectController::toDto).toList();
    String next = end < all.size() ? all.get(end - 1).getId().toString() : null;
    return ResponseEntity.ok(ApiResult.ok(PageResponse.ofPage(page, next, next != null,
        pageSize, (long) all.size(), null, null),
        "project.list.success"));
  }

  @PostMapping("/projects")
  @RequiresPermission("project:write")
  public ResponseEntity<ApiResult<ProjectDto>> createProject(
      @RequestBody Map<String, Object> body) {
    UUID actor = CurrentActor.userId();
    UUID wsId = UUID.fromString(String.valueOf(body.get("workspace_id")));
    policy.requireWorkspaceWrite(actor, wsId);
    var p = projects.create(actor, wsId,
        String.valueOf(body.getOrDefault("name", "p")),
        body.get("description") == null ? null : String.valueOf(body.get("description")));
    return ResponseEntity.ok(ApiResult.ok(
        new ProjectDto(p.getId(), p.getWorkspaceId(), p.getName(), p.getStatus(), null),
        "project.create.success"));
  }

  @GetMapping("/projects/{id}")
  @RequiresPermission("project:read")
  public ResponseEntity<ApiResult<ProjectDto>> getProject(@PathVariable UUID id) {
    UUID actor = CurrentActor.userId();
    policy.requireProjectRead(actor, id);
    var p = projects.get(actor, id);
    return ResponseEntity.ok(ApiResult.ok(
        new ProjectDto(p.getId(), p.getWorkspaceId(), p.getName(), p.getStatus(), null),
        "project.get.success"));
  }

  @PatchMapping("/projects/{id}")
  @RequiresPermission("project:write")
  public ResponseEntity<ApiResult<ProjectDto>> updateProject(@PathVariable UUID id,
      @RequestBody Map<String, Object> body,
      @RequestHeader(value = "If-Match", required = false) String ifMatch) {
    UUID actor = CurrentActor.userId();
    policy.requireProjectWrite(actor, id);
    var p = projects.update(actor, id,
        body.get("name") == null ? null : String.valueOf(body.get("name")),
        body.get("description") == null ? null : String.valueOf(body.get("description")),
        ifMatch(ifMatch));
    return ResponseEntity.ok(ApiResult.ok(toDto(p), "project.update.success"));
  }

  @GetMapping("/projects/{id}/members")
  @RequiresPermission("project:read")
  public ResponseEntity<ApiResult<List<MemberDto>>> members(@PathVariable UUID id) {
    UUID actor = CurrentActor.userId();
    policy.requireProjectRead(actor, id);
    var list = projects.listMembers(actor, id).stream()
        .map(m -> new MemberDto(m.getUserId(), m.getRole())).toList();
    return ResponseEntity.ok(ApiResult.ok(list, "project.members.success"));
  }

  @PostMapping("/projects/{id}/members")
  @RequiresPermission("project:write")
  public ResponseEntity<ApiResult<MemberDto>> addMember(
      @PathVariable UUID id, @RequestBody Map<String, Object> body) {
    UUID actor = CurrentActor.userId();
    policy.requireProjectWrite(actor, id);
    var m = projects.addMember(actor, id,
        UUID.fromString(String.valueOf(body.get("user_id"))),
        String.valueOf(body.getOrDefault("role", "MEMBER")));
    return ResponseEntity.ok(ApiResult.ok(new MemberDto(m.getUserId(), m.getRole()),
        "project.member.add.success"));
  }

  @DeleteMapping("/projects/{id}/members/{memberId}")
  @RequiresPermission("project:write")
  public ResponseEntity<ApiResult<Object>> removeMember(
      @PathVariable UUID id, @PathVariable UUID memberId) {
    UUID actor = CurrentActor.userId();
    policy.requireProjectWrite(actor, id);
    projects.removeMember(actor, id, memberId);
    return ResponseEntity.ok(ApiResult.ok(null, "project.member.remove.success"));
  }

  @PatchMapping("/projects/{id}/approval")
  @RequiresPermission("project:write")
  public ResponseEntity<ApiResult<Object>> approvalToggle(
      @PathVariable UUID id, @RequestBody Map<String, Object> body) {
    UUID actor = CurrentActor.userId();
    policy.requireProjectWrite(actor, id);
    boolean enable = Boolean.parseBoolean(String.valueOf(body.getOrDefault("enabled", "true")));
    long version = Long.parseLong(String.valueOf(body.getOrDefault("version", "1")));
    projects.approvalToggle(actor, id, enable, version);
    return ResponseEntity.ok(ApiResult.ok(null, "project.approval.success"));
  }

  @GetMapping("/projects/{id}/milestones")
  @RequiresPermission("project:read")
  public ResponseEntity<ApiResult<List<MilestoneDto>>> milestones(@PathVariable UUID id) {
    UUID actor = CurrentActor.userId();
    policy.requireProjectRead(actor, id);
    var list = milestones.findByProjectId(id).stream()
        .map(m -> new MilestoneDto(m.getId(), m.getProjectId(), m.getTitle(), m.getDueAt()))
        .toList();
    return ResponseEntity.ok(ApiResult.ok(list, "project.milestones.success"));
  }

  @PostMapping("/projects/{id}/milestones")
  @RequiresPermission("project:write")
  public ResponseEntity<ApiResult<MilestoneDto>> createMilestone(
      @PathVariable UUID id, @RequestBody Map<String, Object> body) {
    UUID actor = CurrentActor.userId();
    policy.requireProjectWrite(actor, id);
    var m = projects.createMilestone(actor, id,
        String.valueOf(body.getOrDefault("title", "m")), null);
    return ResponseEntity.ok(ApiResult.ok(new MilestoneDto(m.getId(), m.getProjectId(),
        m.getTitle(), m.getDueAt()), "project.milestone.create.success"));
  }

  @DeleteMapping("/milestones/{milestoneId}")
  @RequiresPermission("project:write")
  public ResponseEntity<ApiResult<Object>> deleteMilestone(
      @PathVariable UUID milestoneId) {
    UUID actor = CurrentActor.userId();
    var m = milestones.findById(milestoneId)
        .orElseThrow(() -> AppException.notFound("Milestone not found"));
    policy.requireProjectWrite(actor, m.getProjectId());
    projects.deleteMilestone(actor, milestoneId);
    return ResponseEntity.ok(ApiResult.ok(null, "project.milestone.delete.success"));
  }
}
