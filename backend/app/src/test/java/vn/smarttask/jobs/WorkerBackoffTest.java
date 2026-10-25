package vn.smarttask.jobs;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import vn.smarttask.common.service.JobService;

/** Pure backoff math (no DB): bounded exponential, capped. */
class WorkerBackoffTest {
  @Test
  void outboxBackoffDoublesAndCaps() {
    assertEquals(Duration.ofSeconds(2), OutboxWorkerTx.backoff(1));
    assertEquals(Duration.ofSeconds(4), OutboxWorkerTx.backoff(2));
    assertEquals(Duration.ofSeconds(300), OutboxWorkerTx.backoff(10));
    assertEquals(Duration.ofSeconds(300), OutboxWorkerTx.backoff(99));
  }

  @Test
  void jobBackoffDoublesAndCaps() {
    assertEquals(Duration.ofSeconds(2), JobService.backoff(1));
    assertEquals(Duration.ofSeconds(16), JobService.backoff(4));
    assertEquals(Duration.ofSeconds(300), JobService.backoff(20));
  }
}
