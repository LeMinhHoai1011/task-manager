import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { apiFetch, unwrapPage } from "../api/client";
import { t } from "../i18n";
import { Card, EmptyState, ErrorAlert, Skeleton, StatusBadge } from "../components/ui";
import { useRealtimeEvent } from "../realtime/RealtimeContext";

type TaskSummary = { id: string; title: string; state?: string; priority?: string; due_at?: string | null; deadline?: string | null; blocked?: boolean; blocked_flag?: boolean };

function dateOnly(task: TaskSummary) { return task.due_at ?? task.deadline ?? null; }
function isOverdue(task: TaskSummary) {
  const due = dateOnly(task);
  return !!due && new Date(`${String(due).slice(0, 10)}T23:59:59`) < new Date() && task.state !== "COMPLETED" && task.state !== "CANCELLED";
}

export default function Dashboard() {
  const [tasks, setTasks] = useState<TaskSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const load = useCallback(async () => {
    setError(null); setLoading(true);
    try {
      const response = await apiFetch("/tasks", { query: { limit: 100, sort: "updated_at", order: "desc" } });
      setTasks(unwrapPage<TaskSummary>(response).items);
    } catch (e: any) { setError(e?.uiMessage ?? t("error.generic")); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { void load(); }, [load]);
  useRealtimeEvent(["task", "workflow"], () => { void load(); });

  const metrics = useMemo(() => {
    const active = tasks.filter((x) => x.state === "IN_PROGRESS").length;
    const done = tasks.filter((x) => x.state === "COMPLETED").length;
    const overdue = tasks.filter(isOverdue).length;
    const today = new Date().toISOString().slice(0, 10);
    const dueToday = tasks.filter((x) => String(dateOnly(x) ?? "").slice(0, 10) === today).length;
    return [{ label: "Tổng công việc", value: tasks.length, tone: "text-slate-900" }, { label: "Đang thực hiện", value: active, tone: "text-blue-700" }, { label: "Đã hoàn thành", value: done, tone: "text-green-700" }, { label: "Quá hạn", value: overdue, tone: "text-red-700" }, { label: "Đến hạn hôm nay", value: dueToday, tone: "text-amber-700" }];
  }, [tasks]);
  const focus = tasks.filter((x) => x.state !== "COMPLETED" && x.state !== "CANCELLED").sort((a, b) => Number(isOverdue(b)) - Number(isOverdue(a))).slice(0, 6);

  return <div className="mx-auto max-w-7xl space-y-6">
    <section className="flex flex-wrap items-end justify-between gap-3">
      <div><p className="text-sm font-medium text-primary">TỔNG QUAN</p><h1 className="page-title">Công việc của bạn</h1><p className="page-subtitle">Theo dõi tiến độ từ dữ liệu công việc bạn có quyền truy cập.</p></div>
      <Link className="btn btn-primary" to="/tasks">Xem tất cả công việc</Link>
    </section>
    {error && <ErrorAlert message={error} onRetry={() => void load()} />}
    <section className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
      {loading ? Array.from({ length: 5 }).map((_, i) => <Card key={i}><Skeleton className="h-4 w-24" /><Skeleton className="mt-3 h-8 w-12" /></Card>) : metrics.map((m) => <Card key={m.label} className="!p-4"><p className="text-sm text-slate-500">{m.label}</p><p className={`mt-2 text-3xl font-bold tabular-nums ${m.tone}`}>{m.value}</p></Card>)}
    </section>
    <section className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_320px]">
      <Card><div className="flex items-center justify-between"><div><h2 className="section-title">Việc cần chú ý</h2><p className="mt-1 text-sm text-slate-500">Sắp xếp theo tình trạng quá hạn, sau đó là lần cập nhật gần đây.</p></div><Link className="text-sm font-medium text-primary hover:underline" to="/tasks">Mở danh sách</Link></div>
        {loading ? <div className="mt-5 space-y-3">{Array.from({length: 4}).map((_, i) => <Skeleton key={i} className="h-16 w-full" />)}</div> : focus.length === 0 ? <div className="mt-5"><EmptyState title="Bạn chưa có công việc cần xử lý" description="Công việc được giao cho bạn sẽ xuất hiện tại đây." /></div> : <ul className="mt-4 divide-y divide-slate-100">{focus.map((task) => <li key={task.id} className="flex items-center gap-3 py-3"><div className="min-w-0 flex-1"><Link to={`/tasks/${task.id}`} className="block truncate font-medium text-slate-900 hover:text-primary">{task.title}</Link><p className="mt-1 text-xs text-slate-500">{dateOnly(task) ? `Hạn: ${String(dateOnly(task)).slice(0, 10)}` : "Chưa đặt hạn"}</p></div><StatusBadge state={task.blocked || task.blocked_flag ? "BLOCKED" : task.state}>{task.blocked || task.blocked_flag ? "Bị chặn" : task.state ?? "Chưa rõ"}</StatusBadge></li>)}</ul>}
      </Card>
      <Card><h2 className="section-title">Hành động nhanh</h2><div className="mt-4 grid gap-2"><Link className="btn btn-secondary justify-start" to="/projects">Mở dự án</Link><Link className="btn btn-secondary justify-start" to="/calendar">Xem lịch</Link><Link className="btn btn-secondary justify-start" to="/reports">Xem báo cáo</Link></div><div className="mt-6 rounded-lg bg-blue-50 p-4 text-sm text-blue-900"><p className="font-semibold">Dữ liệu thật, đúng phạm vi quyền</p><p className="mt-1 text-blue-800">Các chỉ số trên được tính từ danh sách công việc API trả về, không dùng dữ liệu mẫu.</p></div></Card>
    </section>
  </div>;
}
