package vn.smarttask.common;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** §7 PageResponse + ErrorEnvelope enrichment (absent-when-null optionals). */
class ContractEnvelopeTest {
  @Test
  void pageCarriesItemsAndTotals() {
    var p = PageResponse.ofPage(List.of("a", "b"), "cur", true, 2, 5L, Map.of("state", "TODO"), null);
    assertEquals(List.of("a", "b"), p.items());
    assertEquals(List.of("a", "b"), p.data()); // legacy alias kept
    assertEquals("cur", p.nextCursor());
    assertTrue(p.hasMore());
    assertEquals(2, p.limit());
    assertEquals(5L, p.total());
    assertEquals(3L, p.totalPages());
    assertEquals("TODO", p.filtersApplied().get("state"));
  }

  @Test
  void pageTotalsAbsentForAppendHeavyFeeds() {
    var p = PageResponse.ofPage(List.of("a"), null, false, 20, null, null, null);
    assertNull(p.total());
    assertNull(p.totalPages());
  }

  @Test
  void legacyOfKeepsOldAccessors() {
    var p = PageResponse.of(List.of("x"), null, false);
    assertEquals(1, p.data().size());
    assertFalse(p.hasMore());
  }

  @Test
  void envelopeCarriesEnrichment() {
    var ex = AppException.versionConflict("stale", 7L)
        .withWinningDecision("APPROVED").withResourceState("PENDING_APPROVAL");
    var e = ErrorEnvelope.of(ex, "cid");
    assertEquals("VERSION_CONFLICT", e.error().code());
    assertEquals(7L, e.error().resourceVersion());
    assertEquals("APPROVED", e.error().winningDecision());
    assertEquals("PENDING_APPROVAL", e.error().resourceState());
  }

  @Test
  void plainEnvelopeHasNoExtras() {
    var e = ErrorEnvelope.of("RESOURCE_NOT_FOUND", "resource.not_found", "NO_READ_OR_MISSING",
        null, "cid");
    assertNull(e.error().reauthRequired());
    assertNull(e.error().retryAfterSeconds());
    assertNull(e.error().resourceVersion());
    assertNull(e.error().winningDecision());
    assertNull(e.error().resourceState());
  }

  @Test
  void ifMatchMissingIs428WithSameCode() {
    var ex = AppException.ifMatchRequired();
    assertEquals(428, ex.getHttpStatus().value());
    assertEquals("IF_MATCH_REQUIRED", ex.getCode());
  }

  @Test
  void reauthRequiredSetsFlag() {
    assertTrue(AppException.reauthRequired("x").getReauthRequired());
    assertTrue(ErrorEnvelope.of(AppException.reauthRequired("x"), "c")
        .error().reauthRequired());
  }
}
