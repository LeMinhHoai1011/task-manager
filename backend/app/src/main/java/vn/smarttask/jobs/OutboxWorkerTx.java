package vn.smarttask.jobs;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.smarttask.common.AppException;
import vn.smarttask.common.entity.OutboxDispatch;
import vn.smarttask.common.entity.OutboxEvent;
import vn.smarttask.common.repo.OutboxDispatchRepository;
import vn.smarttask.common.repo.OutboxEventRepository;
import vn.smarttask.common.service.AuditService;

/**
 * F3 outbox transactional steps. Each method is its own short transaction;
 * nothing is ever dispatched before the producer tx commits (the relay only
 * reads {@code PENDING} rows post-commit).
 */
@Service
public class OutboxWorkerTx {
  /** Bounded backoff: 2^attempt seconds, capped at 5 minutes. */
  public static Duration backoff(int attempt) {
    long exp = Math.min(Math.max(attempt, 1), 9);
    return Duration.ofSeconds(Math.min(1L << exp, 300L));
  }

  private final OutboxEventRepository events;
  private final OutboxDispatchRepository ledger;
  private final AuditService audit;

  public OutboxWorkerTx(OutboxEventRepository events, OutboxDispatchRepository ledger,
      AuditService audit) {
    this.events = events; this.ledger = ledger; this.audit = audit;
  }

  /** Claim a batch (owner + expiry; crash-safe: stale claims are reclaimable). */
  @Transactional
  public List<OutboxEvent> claim(int limit, String owner, Duration claimTtl) {
    List<OutboxEvent> due = events.claimDue(limit);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    for (OutboxEvent e : due) {
      e.setClaimedBy(owner);
      e.setClaimedUntil(now.plus(claimTtl));
      events.save(e);
    }
    return due;
  }

  /**
   * Dispatch one claimed event: ledger-hit -> SKIP (duplicate delivery);
   * handler 404 (gone scope/user) -> DEAD terminal, no retry; other failures
   * -> attempts++ with backoff; past max -> DEAD terminal + alert audit.
   */
  @Transactional
  public String dispatch(OutboxEvent event, List<OutboxHandler> handlers, int maxAttempts) {
    OutboxEvent e = events.findById(event.getId()).orElse(null);
    if (e == null || !"PENDING".equals(e.getStatus())) return "SKIP";
    String key = e.getHandler() == null ? e.getType() : e.getHandler();
    OutboxDispatch.Key dk = new OutboxDispatch.Key(e.getId(), key);
    if (ledger.findById(dk).isPresent()) {
      markDispatched(e, "duplicate delivery SKIP (ledger hit)");
      return "SKIP";
    }
    OutboxHandler h = resolve(handlers, key);
    try {
      h.handle(e);
    } catch (AppException ae) {
      if (ae.getHttpStatus() != null && ae.getHttpStatus().value() == 404) {
        return markDead(e, "GONE:" + ae.getReasonCode());
      }
      return noteFailure(e, ae.getReasonCode(), maxAttempts);
    } catch (Exception ex) {
      return noteFailure(e, "HANDLER_FAILED", maxAttempts);
    }
    ledger.save(new OutboxDispatch(e.getId(), key));
    markDispatched(e, null);
    return "DISPATCHED";
  }

  private void markDispatched(OutboxEvent e, String note) {
    e.setStatus("DISPATCHED");
    e.setPublished(true);
    e.setClaimedBy(null);
    e.setClaimedUntil(null);
    e.setLastError(null);
    events.save(e);
    audit.append(null, "outbox.dispatch", "outbox_event", e.getId(), "ALLOWED",
        e.getHandler(), note == null ? "{}" : "{\"note\":\"" + note + "\"}");
  }

  private String noteFailure(OutboxEvent e, String errorCode, int maxAttempts) {
    e.setAttempts(e.getAttempts() + 1);
    e.setLastError(errorCode);
    e.setClaimedBy(null);
    e.setClaimedUntil(null);
    if (e.getAttempts() >= maxAttempts) {
      return markDead(e, errorCode);
    }
    e.setNextAttemptAt(OffsetDateTime.now(ZoneOffset.UTC).plus(backoff(e.getAttempts())));
    events.save(e);
    audit.append(null, "outbox.retry", "outbox_event", e.getId(), "ALLOWED", errorCode,
        "{\"attempt\":" + e.getAttempts() + "}");
    return "RETRY";
  }

  private String markDead(OutboxEvent e, String errorCode) {
    e.setStatus("DEAD");
    e.setLastError(errorCode);
    e.setClaimedBy(null);
    e.setClaimedUntil(null);
    events.save(e);
    // Terminal alert audit (outcome FAILED: operational, not an authz decision).
    audit.append(null, "outbox.event.dead", "outbox_event", e.getId(), "FAILED", errorCode,
        "{\"handler\":\"" + e.getHandler() + "\",\"attempts\":" + e.getAttempts() + "}");
    return "DEAD";
  }

  static OutboxHandler resolve(List<OutboxHandler> handlers, String key) {
    String k = key == null ? "" : key;
    return handlers.stream()
        .filter(h -> h.prefixes().stream().anyMatch(k::startsWith))
        .min(Comparator.comparingInt(OutboxHandler::order))
        .orElseThrow(() -> new IllegalStateException("No outbox handler for " + k));
  }

  /** Test hook: stable worker identity. */
  public static String ownerId() {
    return "relay-" + UUID.randomUUID().toString().substring(0, 8);
  }
}
