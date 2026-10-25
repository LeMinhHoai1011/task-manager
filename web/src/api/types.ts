export interface ErrorEnvelope {
  error: {
    code: string;
    message_key: string;
    reason_code?: string;
    details?: unknown;
    correlation_id?: string;
  };
}

export interface ApiError extends Error {
  status: number;
  code: string;
  messageKey: string;
  reasonCode?: string;
  correlationId?: string;
  /** Neutral UI-safe message (never raw server text). */
  uiMessage: string;
  needsReauth?: boolean;
}

/** Success envelope backend: ApiResult { data, message_key, timestamp }. */
export interface SuccessEnvelope<T = unknown> {
  data?: T;
  message_key?: string;
  timestamp?: string;
}

/**
 * PageResponse canonical §7: items/next_cursor/has_more (+page/limit/total).
 * Legacy `data` là alias tạm của `items` (backend giữ để tương thích).
 */
export interface Page<T> {
  items: T[];
  next_cursor?: string | null;
  has_more?: boolean;
  page?: number | null;
  limit?: number;
  total?: number | null;
  /** @deprecated legacy alias của items — TODO-remove khi backend bỏ. */
  data?: T[];
}

export interface UnwrappedPage<T> {
  items: T[];
  nextCursor: string | null;
  hasMore: boolean;
  total: number | null;
}
