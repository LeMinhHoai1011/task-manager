package vn.smarttask.files;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class StreamTicketTest {
  @Test
  void singleUseSecondConsumeRejected() {
    AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
    Clock clock = new Clock() {
      public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
      public Clock withZone(java.time.ZoneId z) { return this; }
      public Instant instant() { return now.get(); }
    };
    StreamTicketManager m = new StreamTicketManager(clock);
    var t = m.mint(UUID.randomUUID());
    assertEquals(StreamTicketManager.ConsumeResult.OK, m.consumeWithRecheck(t.token(), true));
    assertEquals(StreamTicketManager.ConsumeResult.EXPIRED_OR_USED, m.consumeWithRecheck(t.token(), true));
  }

  @Test
  void ttl30sExpiry() {
    AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
    Clock clock = new Clock() {
      public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
      public Clock withZone(java.time.ZoneId z) { return this; }
      public Instant instant() { return now.get(); }
    };
    StreamTicketManager m = new StreamTicketManager(clock);
    var t = m.mint(UUID.randomUUID());
    assertEquals(Duration.ofSeconds(30), Duration.between(now.get(), t.expiresAt()));
    now.set(now.get().plusSeconds(31));
    assertEquals(StreamTicketManager.ConsumeResult.EXPIRED_OR_USED, m.consumeWithRecheck(t.token(), true));
  }

  @Test
  void revokeBeforeDownloadStaleTicketRejectedFreshRequest404() {
    StreamTicketManager m = new StreamTicketManager();
    var t = m.mint(UUID.randomUUID());
    // stale ticket consumed after revoke -> REVOKED -> gateway maps to 404
    assertEquals(StreamTicketManager.ConsumeResult.REVOKED, m.consumeWithRecheck(t.token(), false));
    assertEquals(FileAccessDecider.Decision.NOT_FOUND,
        FileAccessDecider.fromTicket(StreamTicketManager.ConsumeResult.REVOKED));
    // fresh preview/download request after revoke -> 404 (hide existence), access event DENIED
    var d = FileAccessDecider.decideFreshRequest(true, true, false);
    assertEquals(FileAccessDecider.Decision.NOT_FOUND, d);
    assertEquals("DENIED", FileAccessDecider.accessEventOutcome(d));
    assertEquals("ALLOWED", FileAccessDecider.accessEventOutcome(FileAccessDecider.Decision.ALLOW));
  }
}
