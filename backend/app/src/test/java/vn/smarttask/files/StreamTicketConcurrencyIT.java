package vn.smarttask.files;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * GATE FILE-01 concurrency (integration, NOT_TESTED without Docker):
 * fire N concurrent GET /stream/{ticket} against PostgreSQL and assert
 * exactly one 200 and N-1 non-200, with a single ALLOWED file_access_event.
 * Requires the Testcontainers harness (blocked: Docker daemon unavailable).
 */
@Disabled("needs-docker-daemon: concurrent consume must run against PostgreSQL")
class StreamTicketConcurrencyIT {
  @Test
  void concurrentDoubleConsumeYieldsSingleWinner() {
    // Placeholder: implement with N threads + barrier once Docker is available.
  }
}
