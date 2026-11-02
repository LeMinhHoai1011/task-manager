package vn.smarttask.security;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PlanningWorkflowPolicyTest {
  @Test
  void planningBlocksActualWorkflowControllerActions() {
    var policy = new AuthorizationPolicy();
    var actor = new AuthorizationPolicy.Actor(UUID.randomUUID(), true, false);
    var resource = new AuthorizationPolicy.Resource(UUID.randomUUID(), true, true, true);
    var planning = new AuthorizationPolicy.EffectiveState("ACTIVE", "PLANNING");
    for (String action : new String[]{"task.start", "task.submit", "workflow.start", "workflow.submit"}) {
      assertEquals(AuthorizationPolicy.Decision.FORBIDDEN,
          policy.decideWithState(actor, resource, "task:write", planning, action), action);
    }
    assertEquals(AuthorizationPolicy.Decision.ALLOW,
        policy.decideWithState(actor, resource, "task:write", planning, "task.update"));
    assertEquals(AuthorizationPolicy.Decision.ALLOW,
        policy.decideWithState(actor, resource, "task:write",
            new AuthorizationPolicy.EffectiveState("ACTIVE", "ACTIVE"), "workflow.start"));
  }
}
