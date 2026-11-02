package vn.smarttask.task;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import vn.smarttask.common.AppException;
class TaskVersionTest {
  @Test void parsesLegacyAndQuotedVersions() {
    for (String value : new String[]{"7", "\"7\"", "W/\"7\""}) assertEquals(7, TaskVersion.valueOf(value).value());
    for (String value : new String[]{"*", "-1", "0", "1,2", "broken"}) assertThrows(AppException.class, () -> TaskVersion.valueOf(value));
  }
}
