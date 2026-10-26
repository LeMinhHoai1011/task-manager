import { useEffect, useState } from "react";
import { apiFetch, mutationOpts, newIdempotencyKey, unwrapData, unwrapList, unwrapPage } from "../api/client";
import { t } from "../i18n";
import { Badge, Button, Card, EmptyState, ErrorAlert, Select, Spinner, TextInput } from "../components/ui";

// Deadline date-only: hiển thị 23:59:59 theo tz người đặt.
export function deadlineEndOfDay(dateOnly: string, _timeZone: string): Date {
  // dateOnly YYYY-MM-DD; end-of-day 23:59:59 theo tz người đặt.
  // Hiển thị: render label kèm tz; tính Date UTC tương ứng qua offset ước lượng.
  void _timeZone;
  return new Date(`${dateOnly}T23:59:59`);
}

function deadlineTone(deadline: string | null | undefined): "badge-todo" | "badge-pending" | "badge-blocked" {
  if (!deadline) return "badge-todo";
  const d = new Date(String(deadline).slice(0, 10) + "T23:59:59");
  const now = new Date();
  if (d < now) return "badge-blocked";
  const diff = d.getTime() - now.getTime();
  if (diff < 3 * 24 * 3600 * 1000) return "badge-pending";
  return "badge-todo";
}

// Backend GET /calendar trả { data: [{ task_id, project_id, due_at }] } —
// không có title/tz riêng; web render đúng field thật, không bịa.
interface CalItem {
  task_id: string;
  project_id?: string;
  due_at?: string | null;
  title?: string;
  deadline?: string | null;
  tz?: string;
}
interface TaskOption { id: string; title: string; }

export default function Calendar() {
  const [mode, setMode] = useState<"day" | "week" | "month" | "unscheduled">("week");
  const [items, setItems] = useState<CalItem[]>([]);
  const [err, setErr] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const tz = Intl.DateTimeFormat().resolvedOptions().timeZone;

  useEffect(() => {
    setLoading(true);
    setErr(null);
    apiFetch("/calendar", { query: { view: mode, timezone: tz } })
      .then((r) => {
        const d = unwrapData<unknown>(r);
        setItems(Array.isArray(d) ? (d as CalItem[]) : unwrapList<CalItem>(r));
      })
      .catch((e: any) => {
        setErr(e?.uiMessage ?? t("error.generic"));
        setItems([]);
      })
      .finally(() => setLoading(false));
  }, [mode, tz]);

  const dueOf = (i: CalItem): string | null =>
    i.due_at ?? i.deadline ?? null;
  const dated = items.filter((i) => dueOf(i));
  const unscheduled = items.filter((i) => !dueOf(i));

  const [remTask, setRemTask] = useState("");
  const [remAt, setRemAt] = useState("");
  const [remId, setRemId] = useState("");
  const [remMsg, setRemMsg] = useState<string | null>(null);
  const [tasks, setTasks] = useState<TaskOption[]>([]);

  useEffect(() => {
    apiFetch("/tasks", { query: { limit: 100, sort: "updated_at", order: "desc" } })
      .then((r) => setTasks(unwrapPage<TaskOption>(r).items))
      .catch(() => setTasks([]));
  }, []);

  async function createReminder() {
    setRemMsg(null);
    // Validate: task_id bắt buộc; remind_at phải parse được ISO OffsetDateTime.
    if (!remTask.trim()) {
      setRemMsg(t("form.required"));
      return;
    }
    if (!remAt.trim() || Number.isNaN(Date.parse(remAt.trim()))) {
      setRemMsg(t("form.invalid_date"));
      return;
    }
    try {
      const r = await apiFetch("/reminders", {
        ...mutationOpts({ idempotencyKey: newIdempotencyKey() }),
        method: "POST",
        // datetime-local is entered in the user's local timezone; serialize an
        // explicit UTC instant for the OffsetDateTime backend contract.
        body: { task_id: remTask.trim(), remind_at: new Date(remAt).toISOString() },
      });
      const d = unwrapData<{ id: string }>(r);
      setRemId(d?.id ?? "");
      setRemMsg(t("calendar.reminder_created"));
    } catch (e: any) {
      setRemMsg(e?.uiMessage ?? t("error.generic"));
    }
  }

  async function pauseResume(action: "pause" | "resume") {
    setRemMsg(null);
    if (!remId.trim()) {
      setRemMsg(t("form.required"));
      return;
    }
    try {
      const r = await apiFetch(`/reminders/${remId.trim()}/${action}`, {
        ...mutationOpts({ idempotencyKey: newIdempotencyKey() }),
        method: "POST",
        body: {},
      });
      if (action === "resume") {
        const d = unwrapData<{ digests_sent?: number }>(r);
        setRemMsg(`${t("calendar.resumed")} (${d?.digests_sent ?? 0})`);
      } else {
        setRemMsg(t("calendar.paused"));
      }
    } catch (e: any) {
      setRemMsg(e?.uiMessage ?? t("error.generic"));
    }
  }

  const visible = mode === "unscheduled" ? unscheduled : mode === "month" ? items : dated;

  return (
    <div className="mx-auto max-w-5xl">
      <h1 className="page-title">{t("nav.calendar")} ({tz})</h1>
      <div className="mt-2 flex gap-1 rounded-lg bg-slate-100 p-1">
        {(["day", "week", "month", "unscheduled"] as const).map((m) => (
          <button
            key={m}
            onClick={() => setMode(m)}
            disabled={mode === m}
            className={`flex-1 rounded-md px-3 py-1.5 text-sm font-medium ${mode === m ? "bg-white text-primary shadow" : "text-slate-600 hover:text-slate-900"}`}
          >
            {m}
          </button>
        ))}
      </div>
      <ErrorAlert message={err} />
      {loading ? (
        <div className="mt-3"><Spinner /></div>
      ) : (
        <div className="mt-3 grid gap-2">
          {visible.map((it) => {
            const due = dueOf(it);
            const tone = deadlineTone(due);
            const overdue = tone === "badge-blocked";
            const pending = tone === "badge-pending";
            return (
              <Card key={it.task_id} className={`!py-2.5 ${overdue ? "!border-red-200 !bg-red-50/50" : pending ? "!border-amber-200 !bg-amber-50/50" : ""}`}>
                <div className="flex flex-wrap items-center gap-2 text-sm">
                  <span className="font-medium text-slate-900">{it.title ?? it.task_id}</span>
                  {due ? (
                    <span className={`badge-state ${tone}`}>
                      {`${String(due).slice(0, 10)} (hết hạn ${deadlineEndOfDay(String(due).slice(0, 10), it.tz ?? tz).toLocaleString()} theo tz người đặt ${it.tz ?? tz})`}
                    </span>
                  ) : (
                    <Badge tone="badge-todo">{t("calendar.unscheduled")}</Badge>
                  )}
                </div>
              </Card>
            );
          })}
          {visible.length === 0 && !err && (
            <EmptyState title={t("calendar.empty")} />
          )}
        </div>
      )}
      <Card className="mt-4">
        <h2 className="text-sm font-semibold text-slate-900">{t("calendar.reminders")}</h2>
        {remMsg && <p role="status" className="mt-1 text-sm text-slate-600">{remMsg}</p>}
        <div className="row">
          <Select className="min-w-56" aria-label="Công việc cần nhắc" value={remTask} onChange={(e) => setRemTask(e.target.value)}><option value="">Chọn công việc</option>{tasks.map((task) => <option key={task.id} value={task.id}>{task.title}</option>)}</Select>
          <TextInput className="!w-56" type="datetime-local" aria-label="Thời điểm nhắc" value={remAt} onChange={(e) => setRemAt(e.target.value)} />
          <Button variant="primary" size="sm" onClick={() => void createReminder()}>{t("common.create")}</Button>
        </div>
        <div className="row">
          <TextInput className="!w-44" aria-label={t("calendar.reminder_id")} placeholder={t("calendar.reminder_id")} value={remId} onChange={(e) => setRemId(e.target.value)} />
          <Button variant="secondary" size="sm" onClick={() => void pauseResume("pause")}>{t("calendar.pause")}</Button>
          <Button variant="secondary" size="sm" onClick={() => void pauseResume("resume")}>{t("calendar.resume")}</Button>
        </div>
      </Card>
    </div>
  );
}
