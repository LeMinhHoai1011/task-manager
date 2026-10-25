package vn.smarttask.files;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PH-3 single-use stream ticket, TTL 30s, injectable clock.
 * consume() is single-use: second call rejects even within TTL.
 * Revocation is enforced by caller via hasReadAccess at mint AND at consume;
 * this class exposes consumeWithRecheck for the gateway.
 */
public class StreamTicketManager {
  public static final Duration TTL = Duration.ofSeconds(30);

  private record Entry(UUID attachmentId, Instant expiresAt, boolean[] used) {}

  private final Map<String, Entry> tickets = new ConcurrentHashMap<>();
  private final Clock clock;

  public StreamTicketManager(Clock clock) { this.clock = clock; }
  public StreamTicketManager() { this(Clock.systemUTC()); }

  public record Ticket(String token, Instant expiresAt) {}

  public Ticket mint(UUID attachmentId) {
    String token = UUID.randomUUID().toString();
    Instant exp = clock.instant().plus(TTL);
    tickets.put(token, new Entry(attachmentId, exp, new boolean[]{false}));
    return new Ticket(token, exp);
  }

  public enum ConsumeResult { OK, EXPIRED_OR_USED, REVOKED }

  /** Gateway per-request auth: hasReadAccessNow re-evaluated at consume time. */
  public ConsumeResult consumeWithRecheck(String token, boolean hasReadAccessNow) {
    Entry e = tickets.get(token);
    if (e == null) return ConsumeResult.EXPIRED_OR_USED;
    synchronized (e.used()) {
      if (e.used()[0]) return ConsumeResult.EXPIRED_OR_USED;
      if (clock.instant().isAfter(e.expiresAt())) {
        tickets.remove(token);
        return ConsumeResult.EXPIRED_OR_USED;
      }
      if (!hasReadAccessNow) {
        // stale ticket: revoke consumed as denied, remove so it can never be replayed
        tickets.remove(token);
        return ConsumeResult.REVOKED;
      }
      e.used()[0] = true;
      tickets.remove(token);
      return ConsumeResult.OK;
    }
  }

  public UUID peekAttachment(String token) {
    Entry e = tickets.get(token);
    return e == null ? null : e.attachmentId();
  }
}
