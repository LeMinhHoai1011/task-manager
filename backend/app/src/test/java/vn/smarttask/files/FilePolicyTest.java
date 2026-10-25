package vn.smarttask.files;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FilePolicyTest {
  private static final Set<String> ALLOW = Set.of("application/pdf", "image/png");

  @Test
  void pendingApprovalBlocksViaAnyPath() {
    var r = FilePolicy.checkUpload("application/pdf", 100, ALLOW, 1024, "PENDING_APPROVAL");
    assertFalse(r.allowed());
    assertEquals("EXECUTION_LOCKED_PENDING_APPROVAL", r.reasonCode());
  }

  @Test
  void allowlistRejectsUnknownType() {
    var r = FilePolicy.checkUpload("application/x-msdownload", 100, ALLOW, 1024, "IN_PROGRESS");
    assertFalse(r.allowed());
    assertEquals("FILE_TYPE_NOT_ALLOWED", r.reasonCode());
  }

  @Test
  void maxSizeRejectsOversize() {
    var r = FilePolicy.checkUpload("application/pdf", FilePolicy.maxBytesFromMb(50) + 1, ALLOW,
        FilePolicy.maxBytesFromMb(50), "TODO");
    assertFalse(r.allowed());
    assertEquals("FILE_TOO_LARGE", r.reasonCode());
  }

  @Test
  void scanWithoutEngineIsUnsupportedNotProtected() {
    assertEquals("UNSUPPORTED", FilePolicy.scanStub(false));
  }
}
