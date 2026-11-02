import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
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
const kindLabels:Record<string,string>={TASK_ASSIGNED:"Bạn được giao công việc",TASK_REASSIGNED:"Người phụ trách công việc đã thay đổi",TASK_DEADLINE_SET:"Deadline công việc đã thay đổi",TASK_DEADLINE_SOON:"Công việc sắp đến hạn",TASK_DEADLINE_OVERDUE:"Công việc đã quá hạn",TASK_COMMENT_ADDED:"Có bình luận mới trong công việc",TASK_APPROVAL_SUBMITTED:"Có công việc được nộp duyệt",TASK_WORKFLOW_T_01:"Công việc đã bắt đầu",TASK_WORKFLOW_T_02:"Công việc đang chờ duyệt",TASK_WORKFLOW_T_03:"Công việc đã hoàn thành",TASK_WORKFLOW_T_04:"Công việc đã được duyệt",TASK_WORKFLOW_T_05:"Công việc cần chỉnh sửa",TASK_REMINDER_FIRE:"Đến giờ nhắc công việc",TEAM_MEMBER_ADDED:"Bạn đã được thêm hoặc cập nhật vai trò trong nhóm",WORKSPACE_INVITED:"Bạn có lời mời tham gia Workspace"};

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
  const navigate=useNavigate();
  async function openTask(id:string) {
    try {const result=await apiFetch<{data:{path:string}}>(`/notifications/${id}/target`);await markRead(id);navigate(result.data.path);}
    catch {setErr("Bạn không còn quyền mở công việc hoặc thông báo không có đích hợp lệ.");}
  }
  const [items, setItems] = useState<Notif[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [quiet, setQuiet] = useState<QuietPrefs>({ quiet_hours_enabled: false, start: "22:00", end: "07:00" });
  const [quietSupported, setQuietSupported] = useState(true);
  const [quietTimezone,setQuietTimezone]=useState("UTC");
  const [quietMsg, setQuietMsg] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [muted,setMuted]=useState(false);
  useEffect(()=>{void apiFetch<{data:{type:string;muted:boolean}[]}>("/notifications/preferences").then(r=>setMuted(r.data.some(p=>p.type==="*"&&p.muted))).catch(()=>{});},[]);
  const [filter, setFilter] = useState<"all" | "unread" | "task" | "reminder" | "system">("all");
  const requestSequence = useRef(0);

  const load = useCallback(async (cursor?: string | null) => {
    const sequence = ++requestSequence.current;
    if (cursor) setLoadingMore(true);
    else setLoading(true);
    setErr(null);
    try {
      const r = await apiFetch("/notifications", {
        query: { cursor: cursor || undefined, limit: 20, filter },
      });
      const page = unwrapPage<Notif>(r);
      if (sequence !== requestSequence.current) return;
      setItems((prev) => (cursor ? [...prev, ...page.items] : page.items));
      setNextCursor(page.nextCursor);
    } catch (e: any) {
      if (sequence !== requestSequence.current) return;
      setErr(e?.uiMessage ?? t("error.generic"));
    } finally {
      if (sequence === requestSequence.current) {
        setLoading(false);
        setLoadingMore(false);
      }
    }
  }, [filter]);

  const loadQuiet = useCallback(async () => {
    try {
      const r = await apiFetch("/reminders/preferences");
      const d = (r as { data?: Record<string, unknown> })?.data ?? (r as Record<string, unknown>);
      const rec = (d ?? {}) as Record<string, unknown>;
      setQuietTimezone(typeof rec.timezone==="string"?rec.timezone:"UTC");
      setQuiet({
        quiet_hours_enabled: rec.quiet_hours_enabled === true,
        start: typeof rec.quiet_hours_start === "string" ? rec.quiet_hours_start : "22:00",
        end: typeof rec.quiet_hours_end === "string" ? rec.quiet_hours_end : "07:00",
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
          quiet_hours_start: quiet.start,
          quiet_hours_end: quiet.end,
          timezone:quietTimezone,
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
  const visible = filter === "unread" ? items.filter((n) => !n.read) : items;

  return (
    <div className="mx-auto max-w-3xl">
      <div className="flex items-center justify-between gap-3">
        <h1 className="page-title">
          {t("nav.notifications")} {unread > 0 && <span className="badge">{unread} chưa đọc trong danh sách</span>}
        </h1>
        <div className="flex items-center gap-2">
          <RealtimeIndicator />
          <Button variant="secondary" size="sm" onClick={() => void markAllRead()}>{t("notifications.mark_all_read")}</Button>
        </div>
      </div>
      <ErrorAlert message={err} />
      <label className="flex items-center gap-2 py-3"><input type="checkbox" checked={muted} onChange={e=>{const next=e.target.checked;void apiFetch("/notifications/preferences",{method:"PUT",body:{type:"*",muted:next}}).then(()=>setMuted(next)).catch(()=>setErr("Không lưu được cài đặt thông báo"));}}/>Tạm tắt thông báo thông thường (thông báo bảo mật vẫn hoạt động)</label>
      {notice && <p role="status" className="mt-2 rounded-lg border border-green-200 bg-green-50 px-3 py-2 text-sm text-green-800">{notice}</p>}
      <div className="segmented mt-3" role="group" aria-label="Lọc thông báo">
        <button className={filter === "all" ? "segmented-active" : ""} onClick={() => setFilter("all")}>Tất cả</button>
        <button className={filter === "unread" ? "segmented-active" : ""} onClick={() => setFilter("unread")}>Chưa đọc</button>
        <button className={filter === "task" ? "segmented-active" : ""} onClick={() => setFilter("task")}>Công việc</button>
        <button className={filter === "reminder" ? "segmented-active" : ""} onClick={() => setFilter("reminder")}>Nhắc việc</button>
        <button className={filter === "system" ? "segmented-active" : ""} onClick={() => setFilter("system")}>Hệ thống</button>
      </div>
      {quietSupported ? (
        <Card className="mt-3">
          <h2 className="text-sm font-semibold text-slate-900">{t("notifications.quiet_hours")}</h2>
          <div className="mt-2 flex flex-wrap items-end gap-2">
            <label>Múi giờ yên lặng<select className="input" aria-label="Múi giờ yên lặng" value={quietTimezone} onChange={e=>setQuietTimezone(e.target.value)}>{Array.from(new Set([quietTimezone,Intl.DateTimeFormat().resolvedOptions().timeZone,"UTC","Asia/Ho_Chi_Minh","Asia/Bangkok","America/New_York","Europe/London"])).map(zone=><option key={zone}>{zone}</option>)}</select></label>
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
      ) : visible.length === 0 && !err ? (
        <div className="mt-3"><EmptyState title={t("notifications.no_items")} /></div>
      ) : (
        <ul className="mt-3 flex flex-col gap-2">
          {visible.map((n) => (
            <li key={n.id} className={`flex items-center gap-2.5 rounded-xl border bg-white px-3 py-2.5 text-sm ${n.read ? "border-slate-200" : "border-blue-200 bg-blue-50/50"}`}>
              {!n.read && <span className="h-2 w-2 shrink-0 rounded-full bg-blue-600" aria-label="unread" />}
              <span className={`rounded px-1.5 py-0.5 text-xs ${n.read ? "bg-slate-100 text-slate-500" : "bg-blue-100 text-blue-800"}`}>
                {n.read ? "Đã đọc" : "Chưa đọc"}
              </span>
              <span className="flex-1 text-slate-800">{n.title ?? kindLabels[n.kind??""] ?? n.kind ?? n.id}</span>
              {(n.kind??"").startsWith("TASK_") && <Button variant="ghost" size="sm" onClick={()=>void openTask(n.id)}>Mở công việc</Button>}
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
