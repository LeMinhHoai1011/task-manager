import { useCallback, useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { apiFetch, unwrapData } from "../api/client";
import { useAuth } from "../auth/AuthContext";
import { t } from "../i18n";
import { Card, EmptyState, ErrorAlert, Skeleton, StatusBadge } from "../components/ui";
import { useRealtimeEvent } from "../realtime/RealtimeContext";
import ScopeFilters from "../components/ScopeFilters";
import AppIcon from "../components/AppIcon";

type TaskSummary = {
  id: string;
  title: string;
  scope?: string;
  state?: string;
  priority?: string;
  due_at?: string | null;
  deadline?: string | null;
  blocked?: boolean;
  blocked_flag?: boolean;
};

type Overview = {
  total: number;
  todo: number;
  in_progress: number;
  pending_approval: number;
  completed: number;
  overdue: number;
  due_soon: number;
  due_today: number;
  completion_rate: number;
  focus: TaskSummary[];
  workload: { user_id: string; display_name: string; active: number; total: number }[];
};

type PinnedNote = {
  id: string;
  title: string;
  content: string;
  scope: string;
  updatedAt: string;
};

function dateOnly(task: TaskSummary) {
  return task.due_at ?? task.deadline ?? null;
}

function getGreeting(): string {
  const hour = new Date().getHours();
  if (hour < 12) return "Chào buổi sáng";
  if (hour < 18) return "Chào buổi chiều";
  return "Chào buổi tối";
}

export default function Dashboard() {
  const { user } = useAuth();
  const [tasks, setTasks] = useState<TaskSummary[]>([]);
  const [summary, setSummary] = useState<Overview | null>(null);
  const [pinnedNotes, setPinnedNotes] = useState<PinnedNote[]>([]);
  const [projectCount, setProjectCount] = useState<number>(0);

  const [scope, setScope] = useState("all");
  const [projectId, setProjectId] = useState("");
  const [teamId, setTeamId] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const requestSequence = useRef(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    const request = ++requestSequence.current;
    setError(null);
    setLoading(true);
    try {
      const response = await apiFetch("/dashboard", {
        query: {
          scope,
          projectId: projectId || undefined,
          teamId: teamId || undefined,
          from: from ? new Date(from).toISOString() : undefined,
          to: to ? new Date(to).toISOString() : undefined,
          timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
        },
      });
      if (request !== requestSequence.current) return;
      const data = unwrapData<Overview>(response);
      if (!data) throw new Error("Missing overview");
      setSummary(data);
      setTasks(data.focus);
    } catch (e: any) {
      if (request === requestSequence.current) setError(e?.uiMessage ?? t("error.generic"));
    } finally {
      if (request === requestSequence.current) setLoading(false);
    }
  }, [scope, projectId, teamId, from, to]);

  const loadPinnedNotes = async () => {
    try {
      const res = await apiFetch<any>(`/notes`);
      const items = (res?.data || res || []) as any[];
      const pinned = items.filter((n) => n.isPinned);
      setPinnedNotes(pinned.slice(0, 4));
    } catch {}
  };

  const loadProjectCount = async () => {
    try {
      const res = await apiFetch<any>(`/projects?limit=100`);
      const items = (res?.items || res?.data?.items || []) as any[];
      setProjectCount(items.length);
    } catch {}
  };

  useEffect(() => {
    void load();
    void loadPinnedNotes();
    void loadProjectCount();
  }, [load]);

  useRealtimeEvent(["task", "workflow"], () => {
    void load();
  });

  const todayStr = new Date().toLocaleDateString("vi-VN", {
    weekday: "long",
    year: "numeric",
    month: "long",
    day: "numeric",
  });

  const greeting = `${getGreeting()}, ${user?.email?.split("@")[0] ?? "Bạn"}!`;

  // 4 Primary KPI cards: Hôm nay, Quá hạn, Hoàn thành, Dự án tham gia
  const metrics = summary
    ? [
        {
          label: "Hôm nay",
          value: summary.due_today,
          detail: "Công việc đến hạn hôm nay",
          kind: "active",
        },
        {
          label: "Quá hạn",
          value: summary.overdue,
          detail: "Cần xử lý hoặc dời hạn",
          kind: "overdue",
        },
        {
          label: "Hoàn thành",
          value: summary.completed,
          detail: `${summary.completion_rate.toFixed(1)}% tỷ lệ hoàn tất`,
          kind: "done",
        },
        {
          label: "Dự án tham gia",
          value: projectCount,
          detail: "Không gian & dự án đang hoạt động",
          kind: "total",
        },
      ]
    : [];

  const focus = tasks;

  return (
    <div className="dashboard-page mx-auto max-w-7xl space-y-6">
      {/* SaaS Productivity Header */}
      <section className="flex flex-wrap items-end justify-between gap-4 border-b border-slate-200 dark:border-slate-800 pb-5">
        <div>
          <div className="text-xs font-semibold text-primary uppercase tracking-wider mb-1">
            {todayStr}
          </div>
          <h1 className="page-title text-3xl font-extrabold tracking-tight text-slate-900 dark:text-white">
            {greeting}
          </h1>
          <p className="page-subtitle text-slate-500 dark:text-slate-400 mt-1">
            Tổng quan công việc cần xử lý ngay, tiến độ dự án và ghi chú được ghim.
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2.5">
          <Link className="btn btn-secondary text-xs flex items-center gap-1.5" to="/notes">
            <AppIcon name="files" size={15} />
            <span>+ Tạo ghi chú</span>
          </Link>
          <Link className="btn btn-primary text-xs flex items-center gap-1.5" to="/my-tasks">
            <AppIcon name="plus" size={15} />
            <span>+ Thêm việc cá nhân</span>
          </Link>
        </div>
      </section>

      {error && <ErrorAlert message={error} onRetry={() => void load()} />}

      {/* Scope Filter Panel */}
      <details className="filter-panel rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 shadow-xs">
        <summary className="font-semibold text-xs cursor-pointer p-3">
          Lọc phạm vi công việc{" "}
          <span className="text-primary font-bold">
            {scope === "personal" ? "Cá nhân" : scope === "project" ? "Dự án" : "Tất cả"}
          </span>
        </summary>
        <div className="filter-panel-body p-4 pt-0 space-y-3">
          <ScopeFilters
            scope={scope}
            projectId={projectId}
            teamId={teamId}
            onChange={(s, p, t) => {
              setScope(s);
              setProjectId(p);
              setTeamId(t);
            }}
          />
          <div className="flex flex-wrap gap-3 text-xs">
            <label className="flex flex-col gap-1">
              Hạn từ
              <input
                className="input text-xs"
                type="datetime-local"
                value={from}
                onChange={(e) => setFrom(e.target.value)}
              />
            </label>
            <label className="flex flex-col gap-1">
              Hạn đến (không bao gồm)
              <input
                className="input text-xs"
                type="datetime-local"
                value={to}
                onChange={(e) => setTo(e.target.value)}
              />
            </label>
          </div>
        </div>
      </details>

      {/* 4 Large KPI Cards */}
      <section className="metric-grid" aria-label="Thống kê công việc" aria-busy={loading}>
        {loading
          ? Array.from({ length: 4 }).map((_, i) => (
              <Card key={i} className="rounded-2xl border border-slate-200 dark:border-slate-800 p-5">
                <Skeleton className="h-4 w-24" />
                <Skeleton className="mt-3 h-8 w-12" />
              </Card>
            ))
          : metrics.map((m) => (
              <Card
                key={m.label}
                className={`metric-card metric-${m.kind} rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-5 shadow-xs`}
              >
                <p className="metric-label text-xs font-semibold uppercase tracking-wider text-slate-500">
                  {m.label}
                </p>
                <p className="metric-value text-3xl font-extrabold text-slate-900 dark:text-white mt-1">
                  {m.value}
                </p>
                <p className="metric-detail text-xs text-slate-400 mt-1">{m.detail}</p>
              </Card>
            ))}
      </section>

      {/* Deadline Brief banner */}
      {summary && !loading && (
        <div className="deadline-brief p-3.5 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 shadow-xs flex flex-wrap items-center justify-between text-xs gap-3">
          <div className="flex items-center gap-4 flex-wrap">
            <span>
              <strong>{summary.due_today}</strong> đến hạn hôm nay
            </span>
            <span>•</span>
            <span>
              <strong>{summary.due_soon}</strong> đến hạn trong 7 ngày
            </span>
            <span>•</span>
            <span>
              <strong>{summary.pending_approval}</strong> đang chờ duyệt
            </span>
          </div>
          <Link to="/calendar" className="text-primary font-semibold hover:underline">
            Sắp xếp lịch làm việc →
          </Link>
        </div>
      )}

      {/* Content Layout: Left Focus Tasks, Right Pinned Notes & Workload */}
      <section className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_340px]">
        {/* Left Column: Việc cần chú ý (Focus Tasks) */}
        <div className="space-y-6">
          <Card className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-6 shadow-xs">
            <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 dark:border-slate-800 pb-3">
              <div className="min-w-0">
                <h2 className="section-title text-base font-bold text-slate-900 dark:text-white flex items-center gap-2">
                  <AppIcon name="tasks" size={18} className="text-primary" />
                  <span>Việc cần chú ý</span>
                </h2>
                <p className="mt-0.5 text-xs text-slate-500">
                  Sắp xếp theo tình trạng quá hạn, sau đó là lần cập nhật gần đây.
                </p>
              </div>
              <Link className="text-xs font-semibold text-primary hover:underline" to="/tasks">
                Mở danh sách đầy đủ →
              </Link>
            </div>

            {loading ? (
              <div className="mt-4 space-y-3">
                {Array.from({ length: 4 }).map((_, i) => (
                  <Skeleton key={i} className="h-16 w-full rounded-xl" />
                ))}
              </div>
            ) : focus.length === 0 ? (
              <div className="mt-4">
                <EmptyState
                  title="Bạn chưa có công việc cần xử lý"
                  description="Công việc được giao cho bạn sẽ xuất hiện tại đây."
                />
              </div>
            ) : (
              <ul className="mt-2 divide-y divide-slate-100 dark:divide-slate-800">
                {focus.map((task) => (
                  <li
                    key={task.id}
                    className="flex items-center justify-between gap-3 py-3 hover:bg-slate-50 dark:hover:bg-slate-850 px-2 rounded-xl transition"
                  >
                    <div className="min-w-0 flex-1">
                      <Link
                        to={task.scope === "PERSONAL" ? "/my-tasks" : `/tasks/${task.id}`}
                        className="block truncate font-semibold text-sm text-slate-900 dark:text-white hover:text-primary"
                      >
                        {task.title}
                      </Link>
                      <p className="mt-1 text-xs text-slate-400">
                        {dateOnly(task)
                          ? `📅 Hạn: ${new Date(dateOnly(task)!).toLocaleString("vi-VN", {
                              month: "numeric",
                              day: "numeric",
                              hour: "2-digit",
                              minute: "2-digit",
                            })}`
                          : "Chưa đặt hạn"}
                      </p>
                    </div>
                    <StatusBadge
                      state={task.blocked || task.blocked_flag ? "BLOCKED" : task.state}
                    >
                      {task.blocked || task.blocked_flag ? "Bị chặn" : undefined}
                    </StatusBadge>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </div>

        {/* Right Column: Pinned Notes & Workload */}
        <div className="space-y-6">
          {/* Pinned Notes Widget */}
          <Card className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-5 shadow-xs">
            <div className="flex items-center justify-between border-b border-slate-100 dark:border-slate-800 pb-2 mb-3">
              <h2 className="section-title text-sm font-bold text-slate-900 dark:text-white flex items-center gap-1.5">
                <span className="text-amber-500">★</span>
                <span>Ghi chú đã ghim</span>
              </h2>
              <Link to="/notes" className="text-xs text-primary font-semibold hover:underline">
                Xem tất cả
              </Link>
            </div>
            {pinnedNotes.length === 0 ? (
              <p className="text-xs text-slate-400 py-3">
                Chưa có ghi chú nào được ghim. Bạn có thể ghim các ghi chú quan trọng từ trang Ghi chú.
              </p>
            ) : (
              <div className="space-y-2">
                {pinnedNotes.map((note) => (
                  <Link
                    key={note.id}
                    to={`/notes?id=${note.id}`}
                    className="block p-2.5 rounded-xl border border-slate-100 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-850 hover:border-primary transition"
                  >
                    <div className="font-semibold text-xs text-slate-900 dark:text-white truncate">
                      {note.title}
                    </div>
                    <div className="text-[11px] text-slate-400 line-clamp-1 mt-0.5">
                      {note.content?.replace(/[#*`\-_]/g, "") || "Trống..."}
                    </div>
                  </Link>
                ))}
              </div>
            )}
          </Card>

          {/* Nhịp làm việc của nhóm (Workload Meter) */}
          <Card className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-5 shadow-xs">
            <h2 className="section-title text-sm font-bold text-slate-900 dark:text-white">
              Nhịp làm việc của nhóm
            </h2>
            <p className="mt-1 text-xs text-slate-500">Công việc đang mở trên tổng số được giao.</p>
            {loading ? (
              <Skeleton className="mt-4 h-24 w-full rounded-xl" />
            ) : (
              <ul className="workload-list mt-3 space-y-2.5">
                {summary?.workload.map((row) => (
                  <li key={row.user_id} className="text-xs">
                    <div className="flex justify-between gap-2 mb-1">
                      <span className="truncate font-medium text-slate-700 dark:text-slate-300">
                        {row.display_name}
                      </span>
                      <span className="shrink-0 text-[11px] text-slate-400">
                        {row.active} / {row.total}
                      </span>
                    </div>
                    <meter
                      className="w-full h-2 rounded-full overflow-hidden"
                      min={0}
                      max={Math.max(row.total, 1)}
                      value={row.active}
                      aria-label={`Công việc đang mở của ${row.display_name}`}
                    />
                  </li>
                ))}
              </ul>
            )}
            {!loading && !summary?.workload.length && (
              <p className="my-3 text-xs text-slate-400">
                Chưa có công việc được giao trong phạm vi này.
              </p>
            )}
            <p className="mt-3 border-t border-slate-100 dark:border-slate-800 pt-3 text-[11px] text-slate-400">
              Bao gồm công việc con. Một công việc có thể có nhiều người cộng tác.
            </p>
          </Card>
        </div>
      </section>
    </div>
  );
}
