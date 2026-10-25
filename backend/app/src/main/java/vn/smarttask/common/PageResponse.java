package vn.smarttask.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * §7 cursor page response: {@code items/next_cursor/has_more} plus
 * {@code page/limit/total/totalPages} ({@code total/totalPages} are absent
 * for append-heavy feeds where counting is skipped), {@code filters_applied}
 * and {@code scope_note_key}.
 *
 * <p>Compat: {@code data} (legacy alias of {@code items}),
 * {@code nextCursor}/{@code hasMore} record accessors and {@code page_size}
 * are kept so existing callers keep compiling and old clients keep parsing.
 * Only the §7-optional properties vanish when null; legacy nulls
 * ({@code next_cursor:null}) still serialize exactly as before.
 */
public record PageResponse<T>(
    @JsonProperty("data") List<T> data,
    @JsonProperty("items") List<T> items,
    @JsonProperty("next_cursor") String nextCursor,
    @JsonProperty("has_more") boolean hasMore,
    @JsonProperty("page_size") int pageSize,
    @JsonProperty("page") @JsonInclude(JsonInclude.Include.NON_NULL) Integer page,
    @JsonProperty("limit") int limit,
    @JsonProperty("total") @JsonInclude(JsonInclude.Include.NON_NULL) Long total,
    @JsonProperty("totalPages") @JsonInclude(JsonInclude.Include.NON_NULL) Long totalPages,
    @JsonProperty("filters_applied") @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> filtersApplied,
    @JsonProperty("scope_note_key") @JsonInclude(JsonInclude.Include.NON_NULL) String scopeNoteKey) {

  public static <T> PageResponse<T> of(List<T> data, String nextCursor, boolean hasMore) {
    List<T> rows = data == null ? List.of() : data;
    return new PageResponse<>(rows, rows, nextCursor, hasMore, rows.size(),
        null, rows.size(), null, null, null, null);
  }

  /** §7 full page: total present when counted, absent for append-heavy feeds. */
  public static <T> PageResponse<T> ofPage(List<T> items, String nextCursor, boolean hasMore,
      int limit, Long total, Map<String, Object> filtersApplied, String scopeNoteKey) {
    List<T> rows = items == null ? List.of() : items;
    Long pages = null;
    if (total != null && limit > 0) {
      pages = (total + limit - 1) / limit;
    }
    return new PageResponse<>(rows, rows, nextCursor, hasMore, rows.size(),
        null, limit, total, pages, filtersApplied, scopeNoteKey);
  }
}
