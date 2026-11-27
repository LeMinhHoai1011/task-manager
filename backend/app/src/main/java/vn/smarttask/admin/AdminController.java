package vn.smarttask.admin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.smarttask.common.ApiResult;
import vn.smarttask.common.AppException;
import vn.smarttask.common.PageResponse;
import vn.smarttask.common.repo.AuditEventRepository;
import vn.smarttask.common.repo.BackgroundJobRepository;
import vn.smarttask.admin.repo.SystemSettingRepository;
import vn.smarttask.identity.repo.AppUserRepository;
import vn.smarttask.security.CurrentActor;
import vn.smarttask.security.GrantService;
import vn.smarttask.security.RequiresPermission;

/**
 * F1 admin reads over the real repositories. Audit rows are metadata-only:
 * payload/before/after JSON is always redacted here (SA must not read
 * private content through this endpoint). Job retry re-queues the row.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

  private final AuditEventRepository audits;
  private final BackgroundJobRepository jobs;
  private final SystemSettingRepository settings;
  private final GrantService grants;
  private final AppUserRepository users;

  public AdminController(AuditEventRepository audits, BackgroundJobRepository jobs,
      SystemSettingRepository settings, GrantService grants, AppUserRepository users) {
    this.audits = audits;
    this.jobs = jobs;
    this.settings = settings;
    this.grants = grants;
    this.users = users;
  }

  private void requireAdmin() {
    var actor = CurrentActor.userId();
    grants.requireGrant(actor, "admin.read", "system", null, GrantService.SYSTEM_ADMIN);
  }

  @GetMapping("/audit")
  @RequiresPermission("admin:read")
  public ResponseEntity<ApiResult<PageResponse<Object>>> audit(
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false, defaultValue = "20") int limit) {
    requireAdmin();
    int pageSize = Math.min(Math.max(limit, 1), 100);
    // F2.5: DB-ordered keyset page over UUIDv7 ids (time-ordered), newest
    // first. Unparseable cursor falls back to the first page (legacy
    // behaviour). Same response shape as before.
    var page = org.springframework.data.domain.PageRequest.of(0, pageSize,
        org.springframework.data.domain.Sort.by(
            org.springframework.data.domain.Sort.Direction.DESC, "id"));
    org.springframework.data.domain.Page<vn.smarttask.common.entity.AuditEvent> pg;
    UUID after = null;
    if (cursor != null && !cursor.isBlank()) {
      try {
        after = UUID.fromString(cursor);
      } catch (IllegalArgumentException ignored) {
        after = null;
      }
    }
    pg = after == null ? audits.findAllByOrderByIdDesc(page)
        : audits.findByIdLessThanOrderByIdDesc(after, page);
    var actorIds = pg.getContent().stream()
        .map(vn.smarttask.common.entity.AuditEvent::getActorUserId)
        .filter(java.util.Objects::nonNull)
        .collect(Collectors.toSet());
    Map<UUID, String> nameCache = actorIds.isEmpty() ? Map.of()
        : users.findAllById(actorIds).stream()
            .collect(Collectors.toMap(u -> u.getId(), u -> u.getDisplayName() != null ? u.getDisplayName() : u.getUsername()));
    List<Object> items = pg.getContent().stream()
        .map(e -> {
          var row = new java.util.LinkedHashMap<String, Object>();
          row.put("id", e.getId().toString());
          row.put("actor_user_id", e.getActorUserId() == null ? null : e.getActorUserId().toString());
          row.put("actor_display_name", e.getActorUserId() == null ? "Hệ thống" : nameCache.getOrDefault(e.getActorUserId(), e.getActorUserId().toString()));
          row.put("action", e.getAction() == null ? "" : e.getAction());
          row.put("outcome", e.getOutcome() == null ? "" : e.getOutcome());
          row.put("reason", e.getReason() == null ? "" : e.getReason());
          row.put("created_at", e.getCreatedAt() == null ? "" : e.getCreatedAt().toString());
          row.put("payload", "[REDACTED]");
          row.put("before", "[REDACTED]");
          row.put("after", "[REDACTED]");
          return (Object) row;
        })
        .toList();
    String next = pg.hasNext() && !items.isEmpty()
        ? ((Map<?, ?>) items.get(items.size() - 1)).get("id").toString() : null;
    Long total = pg.getTotalElements();
    return ResponseEntity.ok(ApiResult.ok(PageResponse.ofPage(items, next, next != null,
        pageSize, total, null, null),
        "admin.audit.success"));
  }

  @GetMapping("/jobs")
  @RequiresPermission("admin:read")
  public ResponseEntity<ApiResult<List<Object>>> jobs() {
    requireAdmin();
    // F2.5: newest 100, DB-ordered (background_job grows unboundedly).
    var page = jobs.findAll(org.springframework.data.domain.PageRequest.of(0, 100,
        org.springframework.data.domain.Sort.by(
            org.springframework.data.domain.Sort.Direction.DESC, "createdAt")));
    List<Object> items = page.getContent().stream()
        .map(j -> (Object) Map.of(
            "id", j.getId().toString(),
            "kind", j.getKind() == null ? "" : j.getKind(),
            "status", j.getStatus() == null ? "" : j.getStatus(),
            "attempts", j.getAttempts()))
        .toList();
    return ResponseEntity.ok(ApiResult.ok(items, "admin.jobs.success"));
  }

  @PostMapping("/jobs/{id}/retry")
  @RequiresPermission("admin:write")
  public ResponseEntity<ApiResult<Object>> retryJob(@PathVariable String id) {
    var actor = CurrentActor.userId();
    grants.requireGrant(actor, "admin.job.retry", "background_job", null,
        GrantService.SYSTEM_ADMIN);
    var job = jobs.findById(java.util.UUID.fromString(id))
        .orElseThrow(() -> AppException.notFound("Job not found"));
    if (!"FAILED".equals(job.getStatus()) && !"CANCELLED".equals(job.getStatus())
        && !"DEAD".equals(job.getStatus())) {
      throw AppException.stateConflict("JOB_NOT_RETRYABLE", "Only failed jobs can be retried");
    }
    job.setStatus("QUEUED");
    job.setAttempts(0);
    jobs.save(job);
    return ResponseEntity.ok(ApiResult.ok(
        Map.of("id", job.getId().toString(), "status", job.getStatus()),
        "admin.job.retry.success"));
  }

  private static final java.util.Set<String> SENSITIVE_PREFIXES =
      java.util.Set.of("vapid.private", "secret", "credential");

  private static boolean isSensitiveKey(String key) {
    String lower = key.toLowerCase(java.util.Locale.ROOT);
    return SENSITIVE_PREFIXES.stream().anyMatch(lower::contains)
        || lower.contains("private_key") || lower.contains("password");
  }

  @GetMapping("/settings")
  @RequiresPermission("admin:read")
  public ResponseEntity<ApiResult<List<Object>>> settings() {
    requireAdmin();
    List<Object> items = settings.findAll().stream()
        .map(s -> (Object) Map.of("key", s.getKey(),
            "value", isSensitiveKey(s.getKey()) ? "********" : (s.getValue() == null ? "" : s.getValue())))
        .toList();
    return ResponseEntity.ok(ApiResult.ok(items, "admin.settings.success"));
  }
}
