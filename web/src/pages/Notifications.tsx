import { useCallback, useEffect, useState } from "react";
import { apiFetch, mutationOpts, newIdempotencyKey, unwrapPage } from "../api/client";
import { t } from "../i18n";
import { useRealtimeEvent, RealtimeIndicator } from "../realtime/RealtimeContext";
import { Button, Card, EmptyState, ErrorAlert, Spinner, TextInput } from "../components/ui";

interface Notif {
  id: string;
  kind?: string;
  title?: string;
  read?: boolean;
}

interface QuietPrefs {
  quiet_hours_enabled: boolean;
  start: string;
  end: string;
}

function isHHMM(s: string): boolean {
  return /^([01]\d|2[0-3]):[0-5]\d$/.test(s);
}

/**
 * GET /notifications hỗ trợ cursor (+since_seq/limit) — web phân trang cursor
 * qua envelope canonical. POST /read-all backend hiện chưa nhận max_seq nên
 * web gửi body rỗng tương thích (giữ nguyên, không bịa field mới).
 * Quiet hours trỏ canonical GET+PUT /reminders/preferences; backend F4 chưa có
 * -> 404 thì ẩn section + ghi chú (degrade rõ ràng, xem NOT_TESTED trong báo cáo).
 */
export default function Notifications() {
  const [items, setItems] = useState<Notif[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [quiet, setQuiet] = useState<QuietPrefs>({ quiet_hours_enabled: false, start: "22:00", end: "07:00" });
  const [quietSupported, setQuietSupported] = useState(true);
  const [quietMsg, setQuietMsg] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async (cursor?: string | null) => {
    if (cursor) setLoadingMore(true);
    else setLoading(true);
    setErr(null);
    try {
      const r = await apiFetch("/notifications", {
        query: { cursor: cursor || undefined, limit: 20 },
      });
      const page = unwrapPage<Notif>(r);
      setItems((prev) => (cursor ? [...prev, ...page.items] : page.items));
      setNextCursor(page.nextCursor);
    } catch (e: any) {
      setErr(e?.uiMessage ?? t("error.generic"));
    } finally {
      setLoading(false);
      setLoadingMore(false);
    }
  }, []);

  const loadQuiet = useCallback(async () => {
    try {
      const r = await apiFetch("/reminders/preferences");
      const d = (r as { data?: Record<string, unknown> })?.data ?? (r as Record<string, unknown>);
      const rec = (d ?? {}) as Record<string, unknown>;
      setQuiet({
        quiet_hours_enabled: rec.quiet_hours_enabled === true,
        start: typeof rec.start === "string" ? rec.start : typeof rec.quiet_start === "string" ? (rec.quiet_start as string) : "22:00",
        end: typeof rec.end === "string" ? rec.end : typeof rec.quiet_end === "string" ? (rec.quiet_end as string) : "07:00",
      });
      setQuietSupported(true);
    } catch (e: any) {
      // Backend F4 chưa triển khai -> ẩn section, không báo lỗi chặn màn hình.
      if (e?.status === 404) setQuietSupported(false);
    }
  }, []);

  useEffect(() => {
    void load(null);
    void loadQuiet();
  }, [load, loadQuiet]);

  // Realtime: notification mới -> refetch đầu trang đúng scope qua API.
  useRealtimeEvent(["notification"], () => {
    void load(null);
  });

  async function markRead(id: string) {
    setErr(null);
    try {
      await apiFetch(`/notifications/${id}/read`, {
        ...mutationOpts({ idempotencyKey: newIdempotencyKey() }),
        method: "POST",
        body: {},
      });
      setItems((prev) => prev.map((n) => (n.id === id ? { ...n, read: true } : n)));
      setNotice(t("notifications.marked"));
    } catch (e: any) {
      setErr(e?.uiMessage ?? t("error.generic"));
    }
  }

  async function markAllRead() {
    setErr(null);
    try {
      await apiFetch("/notifications/read-all", {
        ...mutationOpts({ idempotencyKey: newIdempotencyKey() }),
        method: "POST",
        body: {},
      });
      setNotice(t("notifications.marked"));
      void load(null);
    } catch (e: any) {
      setErr(e?.uiMessage ?? t("error.generic"));
    }
  }

  async function saveQuiet() {
    setQuietMsg(null);
    if (!isHHMM(quiet.start) || !isHHMM(quiet.end)) {
      setQuietMsg(t("form.invalid_time"));
      return;
    }
    try {
      await apiFetch("/reminders/preferences", {
        ...mutationOpts({ idempotencyKey: newIdempotencyKey() }),
        method: "PUT",
        body: {
          quiet_hours_enabled: quiet.quiet_hours_enabled,
          start: quiet.start,
          end: quiet.end,
        },
      });
      setQuietMsg(t("notifications.quiet_saved"));
    } catch (e: any) {
      if (e?.status === 404) {
        setQuietSupported(false);
        return;
      }
      setQuietMsg(e?.uiMessage ?? t("error.generic"));
    }
  }

  const unread = items.filter((n) => !n.read).length;

  return (
    <div className="mx-auto max-w-3xl">
      <div className="flex items-center justify-between gap-3">
        <h1 className="page-title">
          {t("nav.notifications")} {unread > 0 && <span className="badge">{unread} unread</span>}
        </h1>
        <div className="flex items-center gap-2">
          <RealtimeIndicator />
          <Button variant="secondary" size="sm" onClick={() => void markAllRead()}>{t("notifications.mark_all_read")}</Button>
        </div>
      </div>
      <ErrorAlert message={err} />
      {notice && <p role="status" className="mt-2 rounded-lg border border-green-200 bg-green-50 px-3 py-2 text-sm text-green-800">{notice}</p>}
      {quietSupported ? (
        <Card className="mt-3">
          <h2 className="text-sm font-semibold text-slate-900">{t("notifications.quiet_hours")}</h2>
          <div className="mt-2 flex flex-wrap items-end gap-2">
            <label className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={quiet.quiet_hours_enabled}
                onChange={(e) => setQuiet({ ...quiet, quiet_hours_enabled: e.target.checked })}
              />
              {t("notifications.quiet_enable")}
            </label>
            <label className="block">
              <span className="label">{t("notifications.quiet_start")} (HH:MM)</span>
              <TextInput className="!w-32" value={quiet.start} onChange={(e) => setQuiet({ ...quiet, start: e.target.value })} />
            </label>
            <label className="block">
              <span className="label">{t("notifications.quiet_end")} (HH:MM)</span>
              <TextInput className="!w-32" value={quiet.end} onChange={(e) => setQuiet({ ...quiet, end: e.target.value })} />
            </label>
            <Button variant="secondary" size="sm" onClick={() => void saveQuiet()}>
              {t("common.save")}
            </Button>
          </div>
          {quietMsg && <p role="status" className="mt-1 text-sm text-slate-600">{quietMsg}</p>}
        </Card>
      ) : (
        <p role="note" className="mt-3 text-xs text-slate-500">{t("notifications.quiet_unavailable")}</p>
      )}
      {loading ? (
        <div className="mt-3"><Spinner label={t("notifications.loading")} /></div>
      ) : items.length === 0 && !err ? (
        <div className="mt-3"><EmptyState title={t("notifications.no_items")} /></div>
      ) : (
        <ul className="mt-3 flex flex-col gap-2">
          {items.map((n) => (
            <li key={n.id} className={`flex items-center gap-2.5 rounded-xl border bg-white px-3 py-2.5 text-sm ${n.read ? "border-slate-200" : "border-blue-200 bg-blue-50/50"}`}>
              {!n.read && <span className="h-2 w-2 shrink-0 rounded-full bg-blue-600" aria-label="unread" />}
              <span className={`rounded px-1.5 py-0.5 text-xs ${n.read ? "bg-slate-100 text-slate-500" : "bg-blue-100 text-blue-800"}`}>
                {n.read ? "read" : "unread"}
              </span>
              <span className="flex-1 text-slate-800">{n.title ?? n.kind ?? n.id}</span>
              {!n.read && (
                <Button variant="ghost" size="sm" onClick={() => void markRead(n.id)}>
                  {t("notifications.mark_read")}
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
      {nextCursor && (
        <div className="mt-4 flex justify-center">
          <Button variant="secondary" disabled={loadingMore} onClick={() => void load(nextCursor)}>
            {t("common.more")}
          </Button>
        </div>
      )}
    </div>
  );
}
