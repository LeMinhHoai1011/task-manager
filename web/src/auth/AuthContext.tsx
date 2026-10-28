import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import {
  SESSION_EXPIRED_EVENT,
  TOKENS_EVENT,
  apiFetch,
  clearTokens,
  getToken,
  refreshAccessToken,
  setTokens,
} from "../api/client";
import type { ApiError } from "../api/types";

export interface SessionUser {
  id: string;
  email: string;
  role: string;
}

interface PendingReauth {
  /** Retry đúng request cũ, giữ nguyên Idempotency-Key. */
  retry: () => Promise<unknown>;
  message: string;
}

interface AuthState {
  user: SessionUser | null;
  token: string | null;
  sessionNotice: string | null;
  reauth: PendingReauth | null;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  refresh: () => Promise<void>;
  revoke: () => Promise<void>;
  /** Gọi khi API trả 428 REAUTH_REQUIRED: mở modal, giữ Idempotency-Key cũ retry. */
  requireReauth: (retry: () => Promise<unknown>, message?: string) => void;
  confirmReauth: (password: string) => Promise<void>;
  dismissReauth: () => void;
  dismissNotice: () => void;
  /** Đánh dấu request thất bại do single-session (login mới đá phiên cũ). */
  notifySessionReplaced: () => void;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<SessionUser | null>(null);
  const [token, setTok] = useState<string | null>(() => getToken());
  const [sessionNotice, setSessionNotice] = useState<string | null>(null);
  const [reauth, setReauth] = useState<PendingReauth | null>(null);
  const pendingRetry = useRef<(() => Promise<unknown>) | null>(null);

  // apiFetch có thể refresh token ở tầng dưới; lắng nghe để React state/SSE
  // dùng token mới, và đưa về login khi refresh thất bại.
  useEffect(() => {
    const onTokens = (e: Event) => {
      const detail = (e as CustomEvent<{ accessToken?: string | null }>).detail;
      setTok(detail?.accessToken ?? null);
    };
    const onExpired = () => {
      setTok(null);
      setUser(null);
      setSessionNotice("auth.session_expired");
    };
    window.addEventListener(TOKENS_EVENT, onTokens);
    window.addEventListener(SESSION_EXPIRED_EVENT, onExpired);
    return () => {
      window.removeEventListener(TOKENS_EVENT, onTokens);
      window.removeEventListener(SESSION_EXPIRED_EVENT, onExpired);
    };
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    // Backend LoginRequest dùng key `username` (tự resolve username-hoặc-email
    // phía server) + client_type. Giữ tương thích: gửi username, không gửi key lạ.
    const res = await apiFetch<{
      data?: { access_token?: string; refresh_token?: string; user_id?: string; role?: string };
      access_token?: string;
      refresh_token?: string;
      role?: string;
      session_replaced?: boolean;
    }>("/auth/login", {
      method: "POST",
      body: { username: email, password, client_type: "WEB" },
      skipRefresh: true,
    });
    const data = res.data;
    const accessToken = data?.access_token ?? res.access_token;
    if (!accessToken) throw new Error("Missing access token");
    const refreshToken = data?.refresh_token ?? res.refresh_token ?? null;
    const role = data?.role ?? res.role ?? "USER";
    // Lưu cả refresh token: access token hết hạn sau 1 ngày, refresh 7 ngày.
    setTokens(accessToken, refreshToken);
    setTok(accessToken);
    setUser({ id: data?.user_id ?? "", email, role });
    if (res.session_replaced) {
      setSessionNotice("auth.session_replaced");
    }
  }, []);

  const logout = useCallback(async () => {
    try {
      await apiFetch("/auth/logout", { method: "POST" });
    } catch {
      /* logout best-effort */
    }
    clearTokens();
    setTok(null);
    setUser(null);
  }, []);

  const refresh = useCallback(async () => {
    const accessToken = await refreshAccessToken();
    if (!accessToken) throw new Error("Session expired");
    setTok(accessToken);
  }, []);

  const revoke = useCallback(async () => {
    try {
      await apiFetch("/auth/logout", { method: "POST" });
    } catch {
      /* revoke best-effort via logout */
    }
    clearTokens();
    setTok(null);
    setUser(null);
  }, []);

  const requireReauth = useCallback((retry: () => Promise<unknown>, message?: string) => {
    pendingRetry.current = retry;
    setReauth({ retry, message: message ?? "auth.reauth_desc" });
  }, []);

  const confirmReauth = useCallback(async (password: string) => {
    // Re-xác thực rồi retry đúng request cũ (giữ Idempotency-Key cũ bên trong closure retry).
    await apiFetch("/auth/reauth", { method: "POST", body: { password } });
    const fn = pendingRetry.current;
    pendingRetry.current = null;
    setReauth(null);
    if (fn) await fn();
  }, []);

  const dismissReauth = useCallback(() => {
    pendingRetry.current = null;
    setReauth(null);
  }, []);

  const dismissNotice = useCallback(() => setSessionNotice(null), []);

  const notifySessionReplaced = useCallback(() => {
    clearTokens();
    setTok(null);
    setUser(null);
    setSessionNotice("auth.session_replaced");
  }, []);

  const value = useMemo<AuthState>(
    () => ({
      user,
      token,
      sessionNotice,
      reauth,
      login,
      logout,
      refresh,
      revoke,
      requireReauth,
      confirmReauth,
      dismissReauth,
      dismissNotice,
      notifySessionReplaced,
    }),
    [user, token, sessionNotice, reauth, login, logout, refresh, revoke, requireReauth, confirmReauth, dismissReauth, dismissNotice, notifySessionReplaced]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}

/** Type-guard lỗi single-session: backend đá phiên cũ khi login mới. */
export function isSessionReplacedError(e: unknown): boolean {
  const err = e as ApiError;
  return !!err && (err.status === 401 && (err.code === "SESSION_REPLACED" || err.reasonCode === "SESSION_REPLACED"));
}
