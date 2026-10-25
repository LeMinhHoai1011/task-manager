package vn.smarttask.files;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * F1.6 stream-bytes integration (NOT_TESTED without Docker): mint a ticket
 * for a committed attachment, GET /stream/{ticket} and assert real bytes
 * (content-length + content-type match the committed version), single-use
 * (second GET -&gt; 410), revoke-safe recheck, and FILE_OBJECT_MISSING 409
 * when the object row has no bytes in MinIO/local root. Requires real
 * PostgreSQL + MinIO (Testcontainers harness, blocked: Docker unavailable).
 */
@Disabled("needs-docker-daemon: stream bytes must run against PostgreSQL + MinIO")
class StreamBytesIT {
  @Test
  void streamReturnsCommittedBytesOnce() {
    // Placeholder: commit attachment with known bytes, stream, compare bytes.
  }

  @Test
  void streamMissingObjectIs409() {
    // Placeholder: delete the object from storage, stream, expect FILE_OBJECT_MISSING.
  }
}
