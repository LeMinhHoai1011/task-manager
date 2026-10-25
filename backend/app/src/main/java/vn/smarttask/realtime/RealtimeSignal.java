package vn.smarttask.realtime;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * F4 realtime signal: the ONLY server-&gt;client realtime payload.
 *
 * <p>A signal carries routing keys ({@code kind, scope_type, scope_id,
 * object_id, seq, at}) and NEVER private content: no title, no body, no
 * description, no payload. A client receiving a signal re-fetches the object
 * through the normal REST API (which re-checks READ permission at click
 * time), so post-revoke redaction stays meaningful.
 */
public record RealtimeSignal(
    @JsonProperty("kind") String kind,
    @JsonProperty("scope_type") String scopeType,
    @JsonProperty("scope_id") UUID scopeId,
    @JsonProperty("object_id") UUID objectId,
    @JsonProperty("seq") long seq,
    @JsonProperty("at") OffsetDateTime at) {

  public RealtimeSignal {
    if (kind == null || kind.isBlank()) throw new IllegalArgumentException("kind required");
    if (scopeType == null || scopeType.isBlank()) {
      throw new IllegalArgumentException("scope_type required");
    }
  }

  /** Builds the wire map with exactly the six routing keys (asserted in tests). */
  public Map<String, Object> toMap() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("kind", kind);
    m.put("scope_type", scopeType);
    m.put("scope_id", scopeId == null ? null : scopeId.toString());
    m.put("object_id", objectId == null ? null : objectId.toString());
    m.put("seq", seq);
    m.put("at", at == null ? null : at.toString());
    return m;
  }

  public static RealtimeSignal of(String kind, String scopeType, UUID scopeId, UUID objectId,
      long seq) {
    return new RealtimeSignal(kind, scopeType, scopeId, objectId, seq,
        OffsetDateTime.now(ZoneOffset.UTC));
  }
}
