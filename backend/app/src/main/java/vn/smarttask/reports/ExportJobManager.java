package vn.smarttask.reports;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PH-6 durable export jobs: TTL 24h, requester-only download, permission rechecked
 * BOTH at creation and at delivery; revoke in between -> CANCELLED_REVOKED (404 on fetch).
 * Clock injectable. Pure domain.
 */
public class ExportJobManager {
  public static final Duration TTL = Duration.ofHours(24);

  public enum Status { QUEUED, RUNNING, REAUTHORIZING, GENERATED, DELIVERED, FAILED, CANCELLED_REVOKED, EXPIRED }

  public record Job(UUID id, String reportCode, String scopeType, UUID scopeId, String format,
      UUID requestedBy, Status status, OffsetDateTime expiresAt, boolean authorizedAtGeneration,
      boolean authorizedAtDelivery) {}

  private final Map<UUID, Job> jobs = new ConcurrentHashMap<>();
  private final Clock clock;

  public ExportJobManager(Clock clock) { this.clock = clock; }
  public ExportJobManager() { this(java.time.Clock.systemUTC()); }

  public Job request(UUID requester, String reportCode, String scopeType, UUID scopeId,
      String format, boolean authorizedNow) {
    if (!authorizedNow) throw new IllegalStateException("PERMISSION_REVOKED_AT_DELIVERY");
    if (!format.matches("PDF|XLSX|CSV")) throw new IllegalArgumentException("EXPORT_FORMAT_UNSUPPORTED");
    Job j = new Job(UUID.randomUUID(), reportCode, scopeType, scopeId, format, requester,
        Status.QUEUED, OffsetDateTime.now(clock).plus(TTL), true, false);
    jobs.put(j.id(), j);
    return j;
  }

  public Job markGenerated(UUID id) {
    return transition(id, Status.GENERATED);
  }

  /** Delivery rechecks permission AND requester identity AND TTL. */
  public Job deliver(UUID id, UUID caller, boolean authorizedNow) {
    Job j = require(id);
    if (!j.requestedBy().equals(caller)) throw new IllegalStateException("RESOURCE_NOT_FOUND");
    if (OffsetDateTime.now(clock).isAfter(j.expiresAt())) {
      return transition(id, Status.EXPIRED);
    }
    if (!authorizedNow) {
      return transition(id, Status.CANCELLED_REVOKED);
    }
    Job upd = new Job(j.id(), j.reportCode(), j.scopeType(), j.scopeId(), j.format(),
        j.requestedBy(), Status.DELIVERED, j.expiresAt(), j.authorizedAtGeneration(), true);
    jobs.put(id, upd);
    return upd;
  }

  /** Fetch after revoke/expiry -> 404 semantics for the gateway. */
  public Job fetchForDownload(UUID id, UUID caller, boolean authorizedNow) {
    Job j = require(id);
    if (!j.requestedBy().equals(caller)) throw new IllegalStateException("RESOURCE_NOT_FOUND");
    if (j.status() == Status.CANCELLED_REVOKED) throw new IllegalStateException("RESOURCE_NOT_FOUND");
    if (OffsetDateTime.now(clock).isAfter(j.expiresAt())) throw new IllegalStateException("EXPORT_EXPIRED");
    if (j.status() != Status.GENERATED && j.status() != Status.DELIVERED) {
      throw new IllegalStateException("EXPORT_NOT_READY");
    }
    if (!authorizedNow) {
      jobs.put(id, new Job(j.id(), j.reportCode(), j.scopeType(), j.scopeId(), j.format(),
          j.requestedBy(), Status.CANCELLED_REVOKED, j.expiresAt(), j.authorizedAtGeneration(), false));
      throw new IllegalStateException("RESOURCE_NOT_FOUND");
    }
    return j;
  }

  private Job require(UUID id) {
    Job j = jobs.get(id);
    if (j == null) throw new IllegalStateException("RESOURCE_NOT_FOUND");
    return j;
  }

  private Job transition(UUID id, Status s) {
    Job j = require(id);
    Job upd = new Job(j.id(), j.reportCode(), j.scopeType(), j.scopeId(), j.format(),
        j.requestedBy(), s, j.expiresAt(), j.authorizedAtGeneration(), j.authorizedAtDelivery());
    jobs.put(id, upd);
    return upd;
  }
}
