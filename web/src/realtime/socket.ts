// Realtime transport: Server-Sent Events (SSE), không phải WebSocket.
//
// Backend F4 chọn SSE (xem RealtimeRegistry: spring-websocket 6.1.x không có
// trong offline repo, trộn 6.2.7 với Spring 6.1 có nguy cơ linkage error).
// Endpoint: GET /api/v1/realtime/stream?ticket=<single-use>.
//
// Bảo mật:
// - Ticket lấy qua REST đã auth (POST /api/v1/realtime/ticket), TTL 60s,
//   single-use, gắn session. KHÔNG bao giờ đưa access/refresh token vào query.
// - Mỗi reconnect phải lấy ticket mới. Vì EventSource tự reconnect cùng URL
//   (ticket đã dùng) nên client đọc stream bằng fetch + AbortController.
// - Server chỉ gửi signal định tuyến {kind, scope_type, scope_id, object_id,
//   seq, at}; client KHÔNG tin payload mà refetch qua API đã kiểm quyền.
import { API_BASE, apiFetch, getToken } from "../api/client";

export interface RealtimeEvent {
  seq: number;
  kind: string;
  scopeType?: string;
  scopeId?: string;
  objectId?: string;
  at?: string;
}

export interface RealtimeOptions {
  /** Gọi khi có signal: client phải refetch từ API theo quyền mới. */
  onEvent?: (ev: RealtimeEvent) => void;
  onStatus?: (connected: boolean) => void;
  /** Lấy ticket handshake (mặc định: POST /api/v1/realtime/ticket bằng session). */
  getTicket?: () => Promise<string | null>;
}

async function defaultGetTicket(): Promise<string | null> {
  try {
    const res = await apiFetch<{ data: { ticket: string } }>("/realtime/ticket", {
      method: "POST",
      body: {},
    });
    const ticket = (res as unknown as { data?: { ticket?: string } })?.data?.ticket
      ?? (res as unknown as { ticket?: string })?.ticket;
    return typeof ticket === "string" && ticket.length > 0 ? ticket : null;
  } catch {
    return null;
  }
}

/** Parse một SSE frame (các dòng đã tách bởi '\n', chưa gồm dòng trống cuối). */
export function parseSseFrame(frame: string): RealtimeEvent | null {
  let event = "message";
  const data: string[] = [];
  for (const rawLine of frame.split("\n")) {
    const line = rawLine.replace(/\r$/, "");
    if (line === "" || line.startsWith(":")) continue; // comment/keep-alive
    const idx = line.indexOf(":");
    const field = idx === -1 ? line : line.slice(0, idx);
    let value = idx === -1 ? "" : line.slice(idx + 1);
    if (value.startsWith(" ")) value = value.slice(1);
    if (field === "event") event = value;
    else if (field === "data") data.push(value);
  }
  if (event !== "signal" || data.length === 0) return null;
  try {
    const parsed = JSON.parse(data.join("\n")) as Record<string, unknown>;
    if (typeof parsed.kind !== "string") return null;
    return {
      seq: typeof parsed.seq === "number" ? parsed.seq : 0,
      kind: parsed.kind,
      scopeType: typeof parsed.scope_type === "string" ? parsed.scope_type : undefined,
      scopeId: typeof parsed.scope_id === "string" ? parsed.scope_id : undefined,
      objectId: typeof parsed.object_id === "string" ? parsed.object_id : undefined,
      at: typeof parsed.at === "string" ? parsed.at : undefined,
    };
  } catch {
    return null;
  }
}

export function connectRealtime(opts: RealtimeOptions = {}) {
  let controller: AbortController | null = null;
  let closed = false;
  let timer: number | null = null;
  let generation = 0;
  let attempt = 0;

  function schedule() {
    if (closed || navigator.onLine === false) return;
    if (timer !== null) window.clearTimeout(timer);
    attempt += 1;
    const delay = Math.min(attempt * 2, 30) * 1000;
    // Ticket single-use: reconnect luôn lấy ticket mới trước khi mở stream.
    timer = window.setTimeout(() => {
      timer = null;
      void connect();
    }, delay);
  }

  async function connect() {
    if (closed) return;
    if(navigator.onLine===false){opts.onStatus?.(false);return;}
    const gen = ++generation;
    try {
      controller?.abort();
    } catch {
      /* ignore */
    }
    controller = new AbortController();
    opts.onStatus?.(false);

    const ticket = await (opts.getTicket ?? defaultGetTicket)();
    if (closed || gen !== generation) return;
    if (!ticket) {
      // Ticket null vì phiên hết hạn: apiFetch đã xoá token và phát
      // SESSION_EXPIRED_EVENT. Không reconnect vô hạn khi không còn phiên.
      if (!getToken()) return;
      schedule();
      return;
    }

    try {
      // Query CHỈ mang ticket single-use; không có Authorization header/token.
      const res = await fetch(
        `${API_BASE}/realtime/stream?ticket=${encodeURIComponent(ticket)}`,
        {
          method: "GET",
          headers: { Accept: "text/event-stream" },
          signal: controller.signal,
          cache: "no-store",
        },
      );
      if (!res.ok || !res.body) {
        schedule();
        return;
      }
      opts.onStatus?.(true);
      attempt = 0;

      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";
      for (;;) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        let boundary = buffer.indexOf("\n\n");
        while (boundary !== -1) {
          const frame = buffer.slice(0, boundary);
          buffer = buffer.slice(boundary + 2);
          const ev = parseSseFrame(frame);
          if (ev) opts.onEvent?.(ev);
          boundary = buffer.indexOf("\n\n");
        }
      }
    } catch {
      // Abort do resync/close hoặc lỗi mạng: rơi xuống nhánh reconnect.
    }
    if (closed || gen !== generation) return;
    opts.onStatus?.(false);
    schedule();
  }

  function offline(){
    generation++;
    if(timer!==null){window.clearTimeout(timer);timer=null;}
    controller?.abort();opts.onStatus?.(false);
  }
  function online(){if(!closed){attempt=0;void connect();}}
  window.addEventListener("offline",offline);
  window.addEventListener("online",online);
  void connect();

  return {
    resync() {
      // Server không giữ cursor: refetch dựa trên sự kiện; mở lại stream bằng ticket mới.
      if (timer !== null) {
        window.clearTimeout(timer);
        timer = null;
      }
      void connect();
    },
    close() {
      closed = true;
      window.removeEventListener("offline",offline);
      window.removeEventListener("online",online);
      generation += 1;
      if (timer !== null) window.clearTimeout(timer);
      try {
        controller?.abort();
      } catch {
        /* ignore */
      }
    },
  };
}
