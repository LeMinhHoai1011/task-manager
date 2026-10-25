package vn.smarttask.jobs;

import java.util.List;
import vn.smarttask.common.entity.OutboxEvent;

/**
 * F3 outbox consumer: exactly one bean per handler-key family.
 * Implementations must be idempotent (dispatch ledger may SKIP them) and
 * must never perform network I/O inside the dispatch tx (stub gateways only).
 */
public interface OutboxHandler {
  /** Prefixes matched against {@code OutboxEvent.handler} (e.g. {@code notification.}). */
  List<String> prefixes();

  /** Lower wins when several handlers match. */
  default int order() { return 100; }

  void handle(OutboxEvent event);
}
