package vn.smarttask.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AccessCapabilitiesTest {
  @Test void guestGrantsNeverEscalate() {
    var a=AccessController.taskCapabilities("GUEST",true,true,true,true,true,true);
    assertFalse(a.metadata());assertFalse(a.assignment());assertFalse(a.structure());
    assertFalse(a.subtask());assertFalse(a.execute());assertFalse(a.comment());assertFalse(a.review());
  }
  @Test void assignedMemberExecutesWithoutManagement() {
    var a=AccessController.taskCapabilities("MEMBER",true,true,true,true,false,false);
    assertTrue(a.execute());assertTrue(a.comment());assertFalse(a.metadata());
    assertFalse(a.assignment());assertFalse(a.review());assertFalse(a.structure());
  }
  @Test void delegatedMemberDoesNotAcquireExecutionOrReviewOnUnassignedTask() {
    var a=AccessController.taskCapabilities("MEMBER",false,true,true,true,true,true);
    assertTrue(a.metadata());assertTrue(a.assignment());assertTrue(a.subtask());
    assertFalse(a.execute());assertFalse(a.comment());assertFalse(a.review());
  }
  @Test void managerCanManageButCannotNestSubtasksOrExecutePlanning() {
    var a=AccessController.taskCapabilities("MANAGER",false,true,false,false,false,false);
    assertTrue(a.metadata());assertTrue(a.structure());assertTrue(a.assignment());
    assertFalse(a.subtask());assertFalse(a.execute());assertFalse(a.review());
  }
  @Test void archivedScopeDisablesEveryMutationEvenForManager() {
    var a=AccessController.taskCapabilities("MANAGER",true,false,true,true,true,true);
    assertFalse(a.metadata());assertFalse(a.assignment());assertFalse(a.structure());
    assertFalse(a.execute());assertFalse(a.comment());assertFalse(a.review());
    assertFalse(AccessController.writable("ARCHIVED","ACTIVE"));
    assertFalse(AccessController.writable("ACTIVE","COMPLETED"));
  }
}
