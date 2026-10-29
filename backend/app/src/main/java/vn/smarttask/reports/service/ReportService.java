package vn.smarttask.reports.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.smarttask.common.AppException;
import vn.smarttask.common.UuidV7;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.common.service.JobService;
import vn.smarttask.project.repo.MilestoneRepository;
import vn.smarttask.project.repo.ProjectMembershipRepository;
import vn.smarttask.reports.DashboardComposer;
import vn.smarttask.reports.MetricDictionary;
import vn.smarttask.reports.entity.ReportExportJob;
import vn.smarttask.reports.repo.ReportExportJobRepository;
import vn.smarttask.task.repo.TaskAssignmentRepository;
import vn.smarttask.task.repo.TaskRepository;
import vn.smarttask.workflow.repo.ApprovalRoundRepository;
import vn.smarttask.workflow.repo.WorkflowEventRepository;

/**
 * PH-6 ReportService: 02B metric dictionary, 4 dashboards (permission-scoped, PARTIAL),
 * history from workflow_event (reopen never rewrites past), durable export with TTL 24h
 * + double permission check + requester-only download. R-07 ranking banned.
 */
@Service
public class ReportService {
  private final TaskRepository tasks;
  private final TaskAssignmentRepository assignments;
  private final MilestoneRepository milestones;
  private final ApprovalRoundRepository rounds;
  private final WorkflowEventRepository workflowEvents;
  private final ProjectMembershipRepository projectMembers;
  private final ReportExportJobRepository exports;
  private final JobService jobs;
  private final AuditService audit;

  public ReportService(TaskRepository tasks, TaskAssignmentRepository assignments,
      MilestoneRepository milestones, ApprovalRoundRepository rounds,
      WorkflowEventRepository workflowEvents, ProjectMembershipRepository projectMembers,
      ReportExportJobRepository exports, JobService jobs, AuditService audit) {
    this.tasks = tasks; this.assignments = assignments; this.milestones = milestones;
    this.rounds = rounds; this.workflowEvents = workflowEvents; this.projectMembers = projectMembers;
    this.exports = exports; this.jobs = jobs; this.audit = audit;
  }

  private void requireMember(UUID projectId, UUID actor) {
    if (projectMembers.findMembership(projectId, actor).isEmpty()) {
      audit.appendDenied(actor, "report.read", "project", projectId, "NO_READ_OR_MISSING");
      throw AppException.notFound("Project not found");
    }
  }

  private List<MetricDictionary.TaskRow> taskRows(UUID projectId) {
    return tasks.findByProjectId(projectId).stream()
        .filter(t -> t.getDeletedAt() == null)
        .map(t -> new MetricDictionary.TaskRow(t.getState(), t.getDepth(), t.isBlockedFlag(),
            t.getDueAt(), t.getId()))
        .toList();
  }

  /** Project dashboard: full 02B metric set for one project.
   *  MEMBER/GUEST only see metrics for tasks assigned to them. */
  @Transactional(readOnly = true, timeout = 15)
  public Map<String, Object> projectDashboard(UUID actor, UUID projectId) {
    requireMember(projectId, actor);
    // MEMBER/GUEST: only metrics for tasks assigned to them.
    var actorMembership = projectMembers.findMembership(projectId, actor).orElse(null);
    boolean actorIsMember = actorMembership != null
        && !"OWNER".equals(actorMembership.getRole())
        && !"ADMIN".equals(actorMembership.getRole())
        && !"MANAGER".equals(actorMembership.getRole());
    var rows = taskRows(projectId).stream()
        .filter(r -> !actorIsMember
            || assignments.existsByTaskIdAndUserId(r.id(), actor))
        .toList();
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    Map<String, MetricDictionary.MetricValue> m = new LinkedHashMap<>();
    m.put("PROGRESS_PERCENT", MetricDictionary.progressPercent(rows));
    var ids = rows.stream().map(MetricDictionary.TaskRow::id).distinct().toList();
    m.put("TASK_UNIQUE_TOTAL", MetricDictionary.uniqueTotal(ids));
    // F2.5: ONE grouped count query instead of a per-task assignment load.
    long assignCount = 0;
    if (!ids.isEmpty()) {
      java.util.Map<UUID, Long> byTask = new java.util.HashMap<>();
      for (var c : assignments.countActiveByTaskIds(ids)) byTask.put(c.getTaskId(), c.getCnt());
      for (var r : rows) assignCount += byTask.getOrDefault(r.id(), 0L);
    }
    m.put("TASK_ASSIGNMENT_COUNT", MetricDictionary.assignmentCount(assignCount));
    for (String s : List.of("TODO", "IN_PROGRESS", "PENDING_APPROVAL", "COMPLETED", "CANCELLED")) {
      m.put("TASK_BY_STATE_" + s, MetricDictionary.byState(rows, s));
    }
    m.put("TASK_BLOCKED_COUNT", MetricDictionary.MetricValue.okInt("TASK_BLOCKED_COUNT",
        rows.stream().filter(MetricDictionary.TaskRow::blocked).count()));
    m.put("TASK_OVERDUE_COUNT", MetricDictionary.overdueCount(rows, now));
    m.put("TASK_OVERDUE_PENDING_APPROVAL_COUNT", MetricDictionary.overduePendingApprovalCount(rows, now));
    var allRounds = new ArrayList<MetricDictionary.ApprovalRoundRow>();
    // F2.5: ONE project-wide round-status query instead of a per-task loop.
    for (String st : rounds.findStatusesByProjectId(projectId)) {
      allRounds.add(new MetricDictionary.ApprovalRoundRow(st));
    }
    m.put("APPROVAL_PENDING_COUNT", MetricDictionary.approvalCount(allRounds, "PENDING"));
    m.put("APPROVAL_APPROVED_COUNT", MetricDictionary.approvalCount(allRounds, "APPROVED"));
    m.put("APPROVAL_REJECTED_COUNT", MetricDictionary.approvalCount(allRounds, "REJECTED"));
    var dash = DashboardComposer.compose(DashboardComposer.Scope.PROJECT,
        List.of(new DashboardComposer.ScopeAccess("PROJECT", projectId.toString(), true)), m);
    return Map.of("scope", "PROJECT", "overall", dash.overall().name(), "metrics", dash.metrics());
  }

  /** Personal dashboard: three separate sets, never summed. */
  @Transactional(readOnly = true, timeout = 15)
  public Map<String, Object> personalDashboard(UUID actor) {
    // F2.5: two scoped queries (assigned ids + created rows) instead of a
    // full task scan with a per-task assignment load. Assignments surviving
    // a task soft-delete are dropped via one batch load.
    List<String> assigned = new ArrayList<>();
    List<UUID> assignedIds = assignments.findActiveTaskIdsByUser(actor);
    if (!assignedIds.isEmpty()) {
      java.util.Set<UUID> alive = new java.util.HashSet<>();
      for (var t : tasks.findAllById(assignedIds)) {
        if (t.getDeletedAt() == null) alive.add(t.getId());
      }
      for (UUID id : assignedIds) {
        if (alive.contains(id)) assigned.add(id.toString());
      }
    }
    List<String> created = new ArrayList<>();
    for (var t : tasks.findByCreatedByAndDeletedAtIsNull(actor)) {
      created.add(t.getId().toString());
    }
    var sets = new DashboardComposer.PersonalSets(assigned, created, List.of());
    return Map.of("scope", "PERSONAL", "overall", "OK",
        "metrics", DashboardComposer.personalCounts(sets));
  }

  /** Historical replay from workflow_event (append-only; reopen adds, never rewrites).
   *  MEMBER/GUEST only see history of tasks assigned to them. */
  @Transactional(readOnly = true, timeout = 15)
  public Map<String, Object> history(UUID actor, UUID taskId) {
    var t = tasks.findById(taskId).orElseThrow(() -> AppException.notFound("Task not found"));
    requireMember(t.getProjectId(), actor);
    // MEMBER/GUEST: only see history of assigned tasks.
    var actorMembership = projectMembers.findMembership(t.getProjectId(), actor).orElse(null);
    boolean actorIsMember = actorMembership != null
        && !"OWNER".equals(actorMembership.getRole())
        && !"ADMIN".equals(actorMembership.getRole())
        && !"MANAGER".equals(actorMembership.getRole());
    if (actorIsMember && !assignments.existsByTaskIdAndUserId(taskId, actor)) {
      throw AppException.notFound("Task not found");
    }
    var states = workflowEvents.findByTaskIdOrderByCreatedAtAsc(taskId).stream()
        .map(e -> e.getToState()).toList();
    return Map.of("task_id", taskId.toString(),
        "states", DashboardComposer.historicalFromWorkflowEvents(new ArrayList<>(states)));
  }

  /** Durable export: permission checked at request AND at delivery; TTL 24h. */
  @Transactional
  public ReportExportJob requestExport(UUID actor, String reportCode, String scopeType, UUID scopeId,
      LocalDate from, LocalDate to, String format, String idempotencyKey) {
    if (MetricDictionary.isBanned(reportCode)) {
      throw AppException.forbidden("Individual ranking exports are forbidden (R-07)");
    }
    if (!format.matches("PDF|XLSX|CSV")) {
      throw AppException.badRequest("EXPORT_FORMAT_UNSUPPORTED", "Bad format");
    }
    var existing = exports.findByIdempotencyKey(idempotencyKey);
    if (existing.isPresent()) return existing.get();
    // check #1 at creation
    checkExportAccess(actor, scopeType, scopeId);
    ReportExportJob job = new ReportExportJob(UuidV7.generate(), reportCode, scopeType, scopeId,
        from, to, format, actor, idempotencyKey,
        OffsetDateTime.now(ZoneOffset.UTC).plusHours(24));
    job.setAuthorizedAtGeneration(OffsetDateTime.now(ZoneOffset.UTC));
    // NB: save() returns the managed copy (assigned-UUID entities take the
    // merge path); later mutations must target it, otherwise re-saving the
    // detached original writes created_at back as NULL.
    job = exports.save(job);
    var bj = jobs.enqueue("REPORT_EXPORT", "{\"export_job_id\":\"" + job.getId() + "\"}", null,
        "export-" + job.getId());
    job.setBackgroundJobId(bj.getId());
    // F1: no worker relay yet — the job stays QUEUED until F3 picks it up.
    // fetchForDownload answers 409 EXPORT_NOT_READY meanwhile (never an echo).
    job.setStatus("QUEUED");
    exports.save(job);
    audit.append(actor, "report.export.request", "report", job.getId(), "ALLOWED", null, "{}");
    return job;
  }

  /** Worker/delivery completion: check #2 at delivery. Revoked -> CANCELLED_REVOKED. */
  @Transactional
  public ReportExportJob completeExport(UUID jobId, long rowCount) {
    ReportExportJob job = exports.findById(jobId)
        .orElseThrow(() -> AppException.notFound("Export job not found"));
    try {
      checkExportAccess(job.getRequestedBy(), job.getScopeType(), job.getScopeId());
    } catch (AppException e) {
      job.setStatus("CANCELLED_REVOKED");
      exports.save(job);
      audit.appendDenied(job.getRequestedBy(), "report.export.deliver", "report", jobId,
          "PERMISSION_REVOKED_AT_DELIVERY");
      return job;
    }
    job.setStatus("GENERATED");
    job.setRowCount(rowCount);
    job.setAuthorizedAtDelivery(OffsetDateTime.now(ZoneOffset.UTC));
    exports.save(job);
    return job;
  }

  /** Requester-only download with TTL + recheck. Revoked/expired -> 404. */
  @Transactional(readOnly = true, timeout = 15)
  public ReportExportJob fetchForDownload(UUID actor, UUID jobId) {
    ReportExportJob job = exports.findById(jobId)
        .orElseThrow(() -> AppException.notFound("Export job not found"));
    if (!job.getRequestedBy().equals(actor)) throw AppException.notFound("Export job not found");
    if ("CANCELLED_REVOKED".equals(job.getStatus())) throw AppException.notFound("Export job not found");
    if (OffsetDateTime.now(ZoneOffset.UTC).isAfter(job.getExpiresAt())) {
      throw new AppException("EXPORT_EXPIRED", "report.export.expired", "EXPORT_EXPIRED",
          org.springframework.http.HttpStatus.GONE, "Export expired");
    }
    if (!"GENERATED".equals(job.getStatus()) && !"DELIVERED".equals(job.getStatus())) {
      throw AppException.stateConflict("EXPORT_NOT_READY", "Export not ready");
    }
    try {
      checkExportAccess(actor, job.getScopeType(), job.getScopeId());
    } catch (AppException e) {
      throw AppException.notFound("Export job not found");
    }
    return job;
  }

  private void checkExportAccess(UUID actor, String scopeType, UUID scopeId) {
    if ("PROJECT".equals(scopeType) && scopeId != null) {
      requireMember(scopeId, actor);
    } else if ("PERSONAL".equals(scopeType)) {
      // own scope only
      if (scopeId != null && !scopeId.equals(actor)) throw AppException.notFound("Scope not found");
    }
    // WORKSPACE/SYSTEM narrow in later milestone; default allow members
  }
}
