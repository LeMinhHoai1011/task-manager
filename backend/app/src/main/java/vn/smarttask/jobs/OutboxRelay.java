package vn.smarttask.jobs;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import vn.smarttask.common.entity.OutboxEvent;

/**
 * F3 outbox relay worker: claims PENDING rows (FOR UPDATE SKIP LOCKED, batch)
 * and dispatches each through the handler registry in its own short tx.
 * Crash-safe (claim owner + expiry, stale reclaimed), idempotent (ledger
 * dedup), retry with bounded backoff, DEAD terminal + alert audit past max.
 */
@Component
public class OutboxRelay {
  public static final int BATCH = 50;
  public static final int MAX_ATTEMPTS = 10;
  public static final Duration CLAIM_TTL = Duration.ofSeconds(30);

  private final OutboxWorkerTx tx;
  private final List<OutboxHandler> handlers;

  public OutboxRelay(OutboxWorkerTx tx, List<OutboxHandler> handlers) {
    this.tx = tx; this.handlers = handlers;
  }

  /** One relay pass; returns outcome counts. No tx held here (per-event txs only). */
  public Map<String, Integer> processBatch(String owner) {
    Map<String, Integer> counts = new LinkedHashMap<>();
    counts.put("DISPATCHED", 0);
    counts.put("SKIP", 0);
    counts.put("RETRY", 0);
    counts.put("DEAD", 0);
    List<OutboxEvent> batch = tx.claim(BATCH, owner, CLAIM_TTL);
    for (OutboxEvent e : batch) {
      String outcome = tx.dispatch(e, handlers, MAX_ATTEMPTS);
      counts.merge(outcome, 1, Integer::sum);
    }
    return counts;
  }

  public Map<String, Integer> processBatch() {
    return processBatch(OutboxWorkerTx.ownerId());
  }
}
