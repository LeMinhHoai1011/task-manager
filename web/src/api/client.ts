// Fetch wrapper cho gateway /api/v1.
// - Gắn Authorization: Bearer <opaque token>
// - X-Correlation-Id (uuid), X-Client-Type: WEB
// - Mutation workflow/upload/export: gửi If-Match + Idempotency-Key
// - Parse ErrorEnvelope, KHÔNG render text server; map status -> UI trung tính.

import type { ApiError, ErrorEnvelope, SuccessEnvelope, UnwrappedPage } from "./types";

const BASE = (import.meta.env.VITE_API_BASE ?? "/api/v1").replace(/\/$/, "");
/** Base URL dùng chung cho fetch/SSE/download (mặc định same-origin /api/v1). */
export const API_BASE = BASE;
const TOKEN_KEY = "tm.access_token";
const REFRESH_KEY = "tm.refresh_token";

/** Phát khi access token đổi (login/refresh/logout) để AuthContext/Realtime cập nhật. */
export const TOKENS_EVENT = "tm:tokens";
/** Phát khi refresh thất bại: phiên thực sự hết hạn, cần đăng nhập lại. */
export const SESSION_EXPIRED_EVENT = "tm:session-expired";

export function getToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function getRefreshToken(): string | null {
  try {
    return localStorage.getItem(REFRESH_KEY);
  } catch {
    return null;
  }
}

function notifyTokens(accessToken: string | null) {
  if (typeof window === "undefined") return;
  window.dispatchEvent(new CustomEvent(TOKENS_EVENT, { detail: { accessToken } }));
}

/**
 * Lưu cặp token. `refresh === undefined` giữ nguyên refresh token hiện có;
 * truyền `null` để xoá (dùng khi logout/refresh bị từ chối).
 */
export function setTokens(accessToken: string | null, refreshToken?: string | null) {
  try {
    if (accessToken) localStorage.setItem(TOKEN_KEY, accessToken);
    else localStorage.removeItem(TOKEN_KEY);
    if (refreshToken !== undefined) {
      if (refreshToken) localStorage.setItem(REFRESH_KEY, refreshToken);
      else localStorage.removeItem(REFRESH_KEY);
    }
  } catch {
    /* ignore */
  }
  notifyTokens(accessToken);
}

/** Giữ tương thích chỗ gọi cũ; không đụng refresh token. */
export function setToken(t: string | null) {
  setTokens(t);
}

/** Xoá cả access + refresh. `notifyExpired` bật khi refresh thất bại (không phải logout). */
export function clearTokens(notifyExpired = false) {
  try {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(REFRESH_KEY);
  } catch {
    /* ignore */
  }
  notifyTokens(null);
  if (notifyExpired && typeof window !== "undefined") {
    window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
  }
}

let refreshInFlight: Promise<string | null> | null = null;

/**
 * Đổi refresh token lấy access token mới. Backend rotate cả refresh token và
 * session (single-session), nên chỉ gọi khi API trả 401 — không gọi định kỳ
 * (refresh sẽ đóng SSE cũ, client tự reconnect bằng ticket mới).
 */
export async function refreshAccessToken(): Promise<string | null> {
  if (refreshInFlight) return refreshInFlight;
  const refresh = getRefreshToken();
  if (!refresh) return null;

  refreshInFlight = (async () => {
    try {
      const res = await fetch(`${BASE}/auth/refresh`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json",
          "X-Client-Type": "WEB",
        },
        body: JSON.stringify({ refresh_token: refresh }),
      });
      if (!res.ok) return null;
      const json = (await res.json()) as unknown;
      const data = (json && typeof json === "object" && "data" in json
        ? (json as { data?: unknown }).data
        : json) as { access_token?: unknown; refresh_token?: unknown } | null;
      const access = typeof data?.access_token === "string" ? data.access_token : null;
      const nextRefresh = typeof data?.refresh_token === "string" ? data.refresh_token : null;
      if (!access) return null;
      setTokens(access, nextRefresh);
      return access;
    } catch {
      return null;
    }
  })().finally(() => {
    refreshInFlight = null;
  });

  return refreshInFlight;
}

export function newCorrelationId(): string {
  try {
    if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
      return crypto.randomUUID();
    }
  } catch {
    /* fallthrough */
  }
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 14)}`;
}

export function newIdempotencyKey(): string {
  return newCorrelationId();
}

/** Neutral UI message theo status. 404 gộp "không tìm thấy hoặc không có quyền". */
export function statusToUiMessage(status: number, locale: string = "vi"): string {
  const vi = locale.startsWith("en") ? false : true;
  switch (status) {
    case 401:
      return vi ? "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại." : "Session expired. Please sign in again.";
    case 403:
      return vi ? "Bạn không có quyền thực hiện thao tác này." : "You do not have permission for this action.";
    case 404:
      return vi ? "Không tìm thấy hoặc không có quyền." : "Not found or no permission.";
    case 409:
      return vi ? "Dữ liệu đã thay đổi. Vui lòng tải lại và thử lại." : "Data has changed. Please reload and retry.";
    case 422:
      return vi ? "Dữ liệu không hợp lệ. Vui lòng kiểm tra lại." : "Invalid data. Please check and retry.";
    case 423:
      return vi ? "Tài khoản đang bị khóa tạm thời." : "Account is temporarily locked.";
    case 428:
      return vi ? "Cần xác thực lại để tiếp tục." : "Re-authentication required to continue.";
    case 429:
      return vi ? "Quá nhiều yêu cầu. Vui lòng thử lại sau." : "Too many requests. Please try again later.";
    default:
      if (status >= 500)
        return vi ? "Hệ thống đang bận. Vui lòng thử lại sau." : "System is busy. Please try again later.";
      return vi ? "Có lỗi xảy ra. Vui lòng thử lại." : "Something went wrong. Please try again.";
  }
}

export type ApiOptions = {
  method?: string;
  body?: unknown;
  /** ETag/version cho optimistic concurrency. */
  ifMatch?: string;
  /** Idempotency-Key cho mutation workflow/upload/export. */
  idempotencyKey?: string;
  /** Content-Type override (FormData => không set). */
  contentType?: string | null;
  query?: Record<string, string | number | boolean | undefined | null>;
  locale?: string;
  /** Token override (cho retry reauth). */
  token?: string | null;
  /** Không thử refresh khi 401 (login/refresh/reauth hoặc caller tự xử lý). */
  skipRefresh?: boolean;
  signal?: AbortSignal;
};

function buildQuery(q?: ApiOptions["query"]): string {
  if (!q) return "";
  const sp = new URLSearchParams();
  for (const [k, v] of Object.entries(q)) {
    if (v === undefined || v === null || v === "") continue;
    sp.append(k, String(v));
  }
  const s = sp.toString();
  return s ? `?${s}` : "";
}

const NO_REFRESH_PATHS = ["/auth/login", "/auth/register", "/auth/refresh", "/auth/reauth"];

function canRefresh(path: string, opts: ApiOptions): boolean {
  if (opts.skipRefresh || opts.token !== undefined) return false;
  if (!getRefreshToken()) return false;
  return !NO_REFRESH_PATHS.some((p) => path.startsWith(p));
}

/**
 * Gọi fetch; nếu 401 và còn refresh token thì refresh một lần rồi retry đúng
 * request (giữ nguyên method/body/Idempotency-Key). Refresh lỗi -> xoá token
 * và phát SESSION_EXPIRED_EVENT để UI về màn đăng nhập.
 */
async function fetchWithRefresh(
  url: string,
  init: RequestInit,
  path: string,
  opts: ApiOptions,
  headers: Record<string, string>,
): Promise<Response> {
  const res = await fetch(url, { ...init, headers });
  if (res.status !== 401 || !canRefresh(path, opts)) return res;

  const next = await refreshAccessToken();
  if (!next) {
    clearTokens(true);
    return res;
  }
  const retried = await fetch(url, {
    ...init,
    headers: { ...headers, Authorization: `Bearer ${next}` },
  });
  if (retried.status === 401) clearTokens(true);
  return retried;
}

export async function apiFetch<T = unknown>(path: string, opts: ApiOptions = {}): Promise<T> {
  const token = opts.token !== undefined ? opts.token : getToken();
  const correlationId = newCorrelationId();
  const locale = opts.locale ?? localStorage.getItem("tm.locale") ?? "vi";
  const headers: Record<string, string> = {
    "X-Correlation-Id": correlationId,
    "X-Client-Type": "WEB",
    Accept: "application/json",
  };
  if (token) headers["Authorization"] = `Bearer ${token}`;
  if (opts.ifMatch) headers["If-Match"] = opts.ifMatch;
  if (opts.idempotencyKey) headers["Idempotency-Key"] = opts.idempotencyKey;

  let body: BodyInit | undefined;
  const isForm = typeof FormData !== "undefined" && opts.body instanceof FormData;
  if (opts.body !== undefined) {
    if (isForm) {
      body = opts.body as BodyInit;
    } else if (typeof opts.body === "string") {
      body = opts.body;
      if (opts.contentType !== null) headers["Content-Type"] = opts.contentType ?? "application/json";
    } else {
      body = JSON.stringify(opts.body);
      headers["Content-Type"] = "application/json";
    }
  }

  const url = `${BASE}${path}${buildQuery(opts.query)}`;
  let res: Response;
  try {
    res = await fetchWithRefresh(
      url,
      {
        method: opts.method ?? (opts.body !== undefined ? "POST" : "GET"),
        body,
        signal: opts.signal,
      },
      path,
      opts,
      headers,
    );
  } catch {
    const e = new Error(statusToUiMessage(0, locale)) as ApiError;
    e.status = 0;
    e.code = "NETWORK_ERROR";
    e.messageKey = "error.network";
    e.uiMessage = e.message;
    throw e;
  }

  if (res.status === 204) return undefined as T;

  const text = await res.text();
  let json: unknown = null;
  if (text) {
    try {
      json = JSON.parse(text);
    } catch {
      json = null;
    }
  }

  if (res.ok) return (json ?? undefined) as T;

  // Parse ErrorEnvelope; correlation_id ưu tiên top-level, fallback error.correlation_id.
  let code = `HTTP_${res.status}`;
  let messageKey = "error.generic";
  let reasonCode: string | undefined;
  let serverCorrelation = correlationId;
  const env = json as (ErrorEnvelope & { correlation_id?: unknown }) | null;
  if (env && typeof env === "object") {
    if ("error" in env && env.error && typeof env.error === "object") {
      if (typeof env.error.code === "string") code = env.error.code;
      if (typeof env.error.message_key === "string") messageKey = env.error.message_key;
      if (typeof env.error.reason_code === "string") reasonCode = env.error.reason_code;
      if (typeof env.error.correlation_id === "string") serverCorrelation = env.error.correlation_id;
    }
    // correlation_id top-level trước, fallback error.correlation_id, cuối cùng local.
    const topCorr = (env as { correlation_id?: unknown }).correlation_id;
    if (typeof topCorr === "string" && topCorr) serverCorrelation = topCorr;
  }

  const err = new Error(statusToUiMessage(res.status, locale)) as ApiError;
  err.status = res.status;
  err.code = code;
  err.messageKey = messageKey;
  err.reasonCode = reasonCode;
  err.correlationId = serverCorrelation;
  err.uiMessage = err.message;
  err.needsReauth = res.status === 428 || reasonCode === "REAUTH_REQUIRED";
  throw err;
}

/** Helper cho mutation workflow/upload/export: luôn gắn If-Match (nếu có) + Idempotency-Key. */
export function mutationOpts(extra: Omit<ApiOptions, "method"> & { method?: string } = {}): ApiOptions {
  return {
    ...extra,
    idempotencyKey: extra.idempotencyKey ?? newIdempotencyKey(),
  };
}

/**
 * Bóc SuccessEnvelope canonical `{ data }` của backend (ApiResult).
 * Mọi endpoint JSON đều trả envelope này; không bao giờ render message_key/text server.
 */
export function unwrapData<T>(json: unknown): T | undefined {
  if (json && typeof json === "object" && "data" in (json as Record<string, unknown>)) {
    return (json as SuccessEnvelope<T>).data;
  }
  return json as T | undefined;
}

/**
 * Bóc PageResponse canonical: đọc `data.items`, fallback legacy `data.data`
 * (alias tạm backend giữ cho client cũ).
 * TODO-remove: bỏ nhánh fallback `data.data` khi backend F4 xóa alias legacy.
 */
export function unwrapPage<T>(json: unknown): UnwrappedPage<T> {
  const page = unwrapData<Record<string, unknown> | T[]>(json);
  const obj = (page && typeof page === "object" && !Array.isArray(page)
    ? (page as Record<string, unknown>)
    : null) ?? {};
  const items = Array.isArray((obj as { items?: unknown }).items)
    ? ((obj as { items: T[] }).items)
    // TODO-remove: legacy alias — backend PageResponse.data, xóa khi backend bỏ.
    : Array.isArray((obj as { data?: unknown }).data)
      ? ((obj as unknown as { data: T[] }).data)
      : Array.isArray(page)
        ? (page as T[])
        : [];
  const nextCursor =
    typeof obj.next_cursor === "string" && obj.next_cursor ? obj.next_cursor : null;
  const hasMore =
    typeof obj.has_more === "boolean" ? obj.has_more : nextCursor !== null;
  const total = typeof obj.total === "number" ? (obj.total as number) : null;
  return { items, nextCursor, hasMore, total };
}

/**
 * Bóc list endpoint: data có thể là mảng thuần (checklist/timelogs/reminders),
 * PageResponse, hoặc { items }. Dùng cho màn hình, không mock.
 */
export function unwrapList<T>(json: unknown): T[] {
  const data = unwrapData<unknown>(json);
  if (Array.isArray(data)) return data as T[];
  return unwrapPage<T>(json).items;
}

/** Headers dùng chung cho cả apiFetch và apiDownload (auth + correlation). */
function baseHeaders(token: string | null): Record<string, string> {
  const headers: Record<string, string> = {
    "X-Correlation-Id": newCorrelationId(),
    "X-Client-Type": "WEB",
    Accept: "application/json",
  };
  if (token) headers["Authorization"] = `Bearer ${token}`;
  return headers;
}

function toApiError(status: number, code: string, locale: string, correlationId?: string): ApiError {
  const err = new Error(statusToUiMessage(status, locale)) as ApiError;
  err.status = status;
  err.code = code;
  err.messageKey = "error.generic";
  err.correlationId = correlationId;
  err.uiMessage = err.message;
  return err;
}

export interface DownloadResult {
  blob: Blob;
  filename: string;
  contentType: string | null;
}

/**
 * Tải file/export dạng binary (GET /export-jobs/{id}/download trả bytes thuần,
 * KHÔNG phải JSON). Dùng blob + objectURL phía caller, không set state JSON.
 * Lỗi dạng JSON ErrorEnvelope được parse như apiFetch; 409 EXPORT_NOT_READY
 * nghĩa là job chưa xong (caller hiển thị retry trung tính).
 */
export async function apiDownload(path: string, opts: ApiOptions = {}): Promise<DownloadResult> {
  const token = opts.token !== undefined ? opts.token : getToken();
  const locale = opts.locale ?? localStorage.getItem("tm.locale") ?? "vi";
  const url = `${BASE}${path}${buildQuery(opts.query)}`;
  const headers = baseHeaders(token);
  let res: Response;
  try {
    res = await fetchWithRefresh(
      url,
      { method: opts.method ?? "GET", signal: opts.signal },
      path,
      opts,
      headers,
    );
  } catch {
    throw toApiError(0, "NETWORK_ERROR", locale);
  }
  if (res.ok) {
    const blob = await res.blob();
    const cd = res.headers.get("Content-Disposition") ?? res.headers.get("content-disposition");
    let filename = "download";
    if (cd) {
      const m = /filename\*?=(?:UTF-8''|")?([^";\n]+)/i.exec(cd);
      if (m) {
        try {
          filename = decodeURIComponent(m[1].replace(/"$/g, ""));
        } catch {
          filename = m[1];
        }
      }
    }
    return { blob, filename, contentType: res.headers.get("Content-Type") };
  }
  let code = `HTTP_${res.status}`;
  let correlation: string | undefined;
  try {
    const env = (await res.json()) as ErrorEnvelope & { correlation_id?: unknown };
    if (env?.error && typeof env.error === "object") {
      if (typeof env.error.code === "string") code = env.error.code;
      if (typeof env.error.correlation_id === "string") correlation = env.error.correlation_id;
    }
    const topCorr = (env as { correlation_id?: unknown })?.correlation_id;
    if (typeof topCorr === "string" && topCorr) correlation = topCorr;
  } catch {
    /* body lỗi không phải JSON — giữ code mặc định */
  }
  throw toApiError(res.status, code, locale, correlation);
}

/** Lưu blob ra file trình duyệt qua objectURL (revoke sau khi click). */
export function saveBlobAsFile(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  try {
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    a.remove();
  } finally {
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
}
