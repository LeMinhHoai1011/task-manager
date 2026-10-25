// Wire realtime SSE vào React:
// - Ticket lấy qua REST (POST /realtime/ticket, session), KHÔNG kèm access token.
// - Mỗi reconnect lấy ticket mới (socket.ts tự làm); client KHÔNG tin payload,
//   chỉ dùng kind/scope để refetch đúng scope qua API.
// - RealtimeIndicator hiển thị trạng thái kết nối (online/offline).
import React, { createContext, useContext, useEffect, useMemo, useRef, useState } from "react";
import { useAuth } from "../auth/AuthContext";
import { t } from "../i18n";
import { connectRealtime, type RealtimeEvent } from "./socket";

interface RealtimeState {
  connected: boolean;
  lastEvent: RealtimeEvent | null;
}

const RealtimeContext = createContext<RealtimeState>({ connected: false, lastEvent: null });

export function RealtimeProvider({ children }: { children: React.ReactNode }) {
  const [connected, setConnected] = useState(false);
  const [lastEvent, setLastEvent] = useState<RealtimeEvent | null>(null);
  const { token } = useAuth();

  useEffect(() => {
    // Chỉ kết nối khi đã đăng nhập; phụ thuộc token để login/logout (không cần
    // reload trang) mở/đóng stream. Socket tự reconnect ticket mới khi rớt.
    if (!token) return;
    const conn = connectRealtime({
      onStatus: (ok) => setConnected(ok),
      // Không tin payload: phát lại event để screen tự refetch qua API theo quyền mới.
      onEvent: (ev) => setLastEvent(ev),
    });
    return () => conn.close();
  }, [token]);

  const value = useMemo(() => ({ connected, lastEvent }), [connected, lastEvent]);
  return <RealtimeContext.Provider value={value}>{children}</RealtimeContext.Provider>;
}

/**
 * Đăng ký refetch khi có realtime event đúng scope.
 * @param scopes tiền tố scope quan tâm, rỗng = mọi event. Ví dụ ["tasks"], ["notifications"].
 */
export function useRealtimeEvent(scopes: string[], cb: (ev: RealtimeEvent) => void): void {
  const { lastEvent } = useContext(RealtimeContext);
  const ref = useRef(cb);
  ref.current = cb;
  const key = scopes.join(",");
  useEffect(() => {
    if (!lastEvent) return;
    if (!key) {
      ref.current(lastEvent);
      return;
    }
    const scope = (lastEvent.scopeType ?? "").toLowerCase();
    const kind = (lastEvent.kind ?? "").toLowerCase();
    const wanted = key.toLowerCase().split(",");
    if (wanted.some((s) => (s && (scope.startsWith(s) || kind.startsWith(s))) || (!scope && !kind))) {
      ref.current(lastEvent);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [lastEvent]);
}

export function useRealtimeStatus(): boolean {
  return useContext(RealtimeContext).connected;
}

export function RealtimeIndicator() {
  const connected = useRealtimeStatus();
  return (
    <span
      role="status"
      aria-live="polite"
      title={connected ? t("realtime.online") : t("realtime.offline")}
      className={`inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-xs font-medium ${
        connected ? "bg-green-100 text-green-800" : "bg-slate-200 text-slate-600"
      }`}
    >
      <span
        aria-hidden
        className={`h-2 w-2 rounded-full ${connected ? "bg-green-600" : "bg-slate-500"}`}
      />
      {connected ? t("realtime.online") : t("realtime.offline")}
    </span>
  );
}

/** Test-only: ép trạng thái realtime mà không cần stream thật. */
export function TestRealtimeProvider({
  children,
  connected = true,
  lastEvent = null,
}: {
  children: React.ReactNode;
  connected?: boolean;
  lastEvent?: RealtimeEvent | null;
}) {
  const value = useMemo(() => ({ connected, lastEvent }), [connected, lastEvent]);
  return <RealtimeContext.Provider value={value}>{children}</RealtimeContext.Provider>;
}
