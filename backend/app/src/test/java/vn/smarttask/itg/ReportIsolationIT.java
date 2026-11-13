package vn.smarttask.itg;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.smarttask.reports.MetricDictionary.MetricValue;
import vn.smarttask.reports.service.ReportService;
import vn.smarttask.task.entity.TaskAssignment;
import vn.smarttask.workflow.entity.ApprovalRound;
import static org.junit.jupiter.api.Assertions.*;

class ReportIsolationIT extends ItgBase {
  @Autowired ReportService reports;
  @SuppressWarnings("unchecked")
  long metric(UUID actor, UUID project, String code) {
    var values = (Map<String, MetricValue>) reports.projectDashboard(actor, project).get("metrics");
    return values.get(code).intValue();
  }
  @Test void approvalsAndHistoryOnlyUseCurrentlyAssignedTasks() {
    var manager = activeUser("rmgr", "Audit-Strong-135!");
    var member = activeUser("rmember", "Audit-Strong-135!");
    var ws = mkWorkspace(manager.getId(), "report");
    var project = mkActiveProject(manager.getId(), ws, "report");
    addProjectMember(manager.getId(), project.getId(), member.getId(), "MEMBER");
    var visible = mkTask(manager.getId(), project.getId(), "Visible");
    var hidden = mkTask(manager.getId(), project.getId(), "Hidden");
    var assigned = assignments.saveAndFlush(new TaskAssignment(UUID.randomUUID(), visible.getId(), member.getId()));
    for (var task : java.util.List.of(visible, hidden)) {
      var round = new ApprovalRound(UUID.randomUUID(), task.getId(), 1, manager.getId());
      round.setStatus("APPROVED"); rounds.saveAndFlush(round);
    }
    assertEquals(2, metric(manager.getId(), project.getId(), "APPROVAL_APPROVED_COUNT"));
    assertEquals(1, metric(member.getId(), project.getId(), "APPROVAL_APPROVED_COUNT"));
    assigned.setStatus("REMOVED"); assignments.saveAndFlush(assigned);
    assertEquals(0, metric(member.getId(), project.getId(), "TASK_UNIQUE_TOTAL"));
    assertEquals(0, metric(member.getId(), project.getId(), "APPROVAL_APPROVED_COUNT"));
    assertEquals(404, assertThrows(vn.smarttask.common.AppException.class,
        () -> reports.history(member.getId(), visible.getId())).getHttpStatus().value());
  }
}
