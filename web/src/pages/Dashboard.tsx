import { useCallback, useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { apiFetch, unwrapData, unwrapPage } from "../api/client";
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

type RecentNotification = {
  id: string;
  kind?: string;
  title?: string;
  created_at?: string;
  read?: boolean;
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
  const [recentNotifications, setRecentNotifications] = useState<RecentNotification[]>([]);
  const [projectCount, setProjectCount] = useState<number>(0);

  const [scope, setScope] = useState("all");
  const [projectId, setProjectId] = useState("");
  const [teamId, setTeamId] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const requestSequence = useRef(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Quick Task Creation state
  const [quickCreateOpen, setQuickCreateOpen] = useState(false);
  const [quickTitle, setQuickTitle] = useState("");
  const [quickType, setQuickType] = useState<"personal" | "project">("personal");
  const [quickProjectId, setQuickProjectId] = useState("");
  const [projectsList, setProjectsList] = useState<Array<{ id: string; name: string }>>([]);
  const [creatingTask, setCreatingTask] = useState(false);
  const [quickMessage, setQuickMessage] = useState<string | null>(null);

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

  const loadRecentNotifications = async () => {
    try {
      const res = await apiFetch<any>(`/notifications?limit=5`);
      const page = unwrapPage<RecentNotification>(res);
      setRecentNotifications(page.items.slice(0, 5));
    } catch {}
  };

  const loadProjectCount = async () => {
    try {
      const res = await apiFetch<any>(`/projects?limit=100`);
      const items = (res?.items || res?.data?.items || []) as any[];
      setProjectCount(items.length);
      setProjectsList(items.map((p: any) => ({ id: p.id, name: p.name })));
      if (items.length > 0 && !quickProjectId) {
        setQuickProjectId(items[0].id);
      }
    } catch {}
  };

  useEffect(() => {
    void load();
    void loadPinnedNotes();
    void loadRecentNotifications();
    void loadProjectCount();
  }, [load]);

  useRealtimeEvent(["task", "workflow", "notification"], () => {
    void load();
    void loadRecentNotifications();
  });

  const handleQuickCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!quickTitle.trim()) return;
    setCreatingTask(true);
    setQuickMessage(null);
    try {
      if (quickType === "personal") {
        await apiFetch("/me/tasks", {
          method: "POST",
          body: { title: quickTitle.trim() },
        });
      } else {
        await apiFetch("/tasks", {
          method: "POST",
          body: {
            title: quickTitle.trim(),
            project_id: quickProjectId || projectsList[0]?.id,
          },
        });
      }
      setQuickTitle("");
      setQuickCreateOpen(false);
      setQuickMessage("Tạo công việc thành công!");
      void load();
      setTimeout(() => setQuickMessage(null), 3000);
    } catch (err: any) {
      setQuickMessage(err.uiMessage || "Không tạo được công việc");
    } finally {
      setCreatingTask(false);
    }
  };

  const todayStr = new Date().toLocaleDateString("vi-VN", {
    weekday: "long",
    year: "numeric",
    month: "long",
    day: "numeric",
  });

  const greeting = `${getGreeting()}, ${user?.email?.split("@")[0] ?? "Bạn"}!`;

  // 6 Primary KPI metrics matching requirements:
  // 1. Tổng công việc
  // 2. Việc đang thực hiện
  // 3. Hoàn thành
  // 4. Quá hạn
  // 5. Việc cần làm hôm nay
  // 6. Dự án đang tham gia
  const metrics = summary
    ? [
        {
          label: "Tổng công việc",
          value: summary.total,
          detail: "Toàn bộ công việc theo phạm vi",
          color: "border-t-slate-200",
          icon: "tasks",
          badgeColor: "bg-blue-50 text-blue-700 dark:bg-blue-950 dark:text-blue-300",
        },
        {
          label: "Đang thực hiện",
          value: summary.in_progress,
          detail: "Đang được xử lý tích cực",
          color: "border-t-slate-200",
          icon: "clock",
          badgeColor: "bg-blue-50 text-blue-700 dark:bg-blue-950 dark:text-blue-300",
        },
        {
          label: "Hoàn thành",
          value: summary.completed,
          detail: `${summary.completion_rate.toFixed(1)}% tỷ lệ hoàn tất`,
          color: "border-t-slate-200",
          icon: "check-circle",
          badgeColor: "bg-emerald-50 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300",
        },
        {
          label: "Quá hạn",
          value: summary.overdue,
          detail: "Cần xử lý khẩn cấp",
          color: "border-t-slate-200",
          icon: "alert-triangle",
          badgeColor: "bg-red-50 text-red-700 dark:bg-red-950 dark:text-red-300",
        },
        {
          label: "Việc hôm nay",
          value: summary.due_today,
          detail: "Hạn trong 24 giờ tới",
          color: "border-t-slate-200",
          icon: "calendar",
          badgeColor: "bg-amber-50 text-amber-700 dark:bg-amber-950 dark:text-amber-300",
        },
        {
          label: "Dự án tham gia",
          value: projectCount,
          detail: "Dự án đang cùng cộng tác",
          color: "border-t-slate-200",
          icon: "project",
          badgeColor: "bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300",
        },
      ]
    : [];

  const focus = tasks;

  return (
    <div className="dashboard-page mx-auto max-w-7xl space-y-6">
      {/* Header section */}
      <section className="flex flex-wrap items-end justify-between gap-4 border-b border-slate-200 dark:border-slate-800 pb-5">
        <div>
          <div className="text-sm text-slate-500 dark:text-slate-400 mb-2">
            {todayStr}
          </div>
          <h1 className="page-title text-3xl font-semibold tracking-tight text-slate-800 dark:text-white">
            {greeting}
          </h1>
          <p className="page-subtitle text-slate-500 dark:text-slate-400 mt-1">
            Những việc cần tập trung, tiến độ dự án và cập nhật trong ngày.
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2.5">
          <Link
            className="btn btn-secondary text-xs flex items-center gap-1.5 rounded-lg border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 shadow-xs"
            to="/notes"
          >
            <AppIcon name="files" size={15} />
            <span>Ghi chú</span>
          </Link>
          <button
            onClick={() => setQuickCreateOpen(true)}
            className="btn btn-primary text-xs flex items-center gap-1.5 rounded-lg shadow-xs"
          >
            <AppIcon name="plus" size={16} />
            <span>Tạo việc nhanh</span>
          </button>
        </div>
      </section>

      {quickMessage && (
        <div className="p-3 text-xs rounded-xl bg-emerald-50 dark:bg-emerald-950/40 border border-emerald-200 dark:border-emerald-800 text-emerald-800 dark:text-emerald-300 flex items-center gap-2">
          <AppIcon name="check-circle" size={16} />
          <span>{quickMessage}</span>
        </div>
      )}

      {error && <ErrorAlert message={error} onRetry={() => void load()} />}

      {/* Scope Filter Panel */}
      <details className="filter-panel rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 shadow-xs">
        <summary className="font-semibold text-xs cursor-pointer p-3.5 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <AppIcon name="filter" size={15} className="text-blue-600" />
            <span>
              Bộ lọc phạm vi hiển thị:{" "}
              <strong className="text-blue-600 dark:text-blue-400">
                {scope === "personal" ? "Cá nhân" : scope === "project" ? "Dự án" : "Tất cả"}
              </strong>
            </span>
          </div>
          <span className="text-[11px] text-slate-400 font-normal">Nhấn để thay đổi phạm vi & ngày</span>
        </summary>
        <div className="filter-panel-body p-4 pt-1 space-y-3 border-t border-slate-100 dark:border-slate-800">
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
          <div className="flex flex-wrap gap-3 text-xs pt-1">
            <label className="flex flex-col gap-1">
              Hạn từ ngày
              <input
                className="input text-xs"
                type="datetime-local"
                value={from}
                onChange={(e) => setFrom(e.target.value)}
              />
            </label>
            <label className="flex flex-col gap-1">
              Hạn đến ngày (không bao gồm)
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

      {/* 6 Metric KPI Grid */}
      <section
        className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3.5"
        aria-label="Thống kê công việc"
        aria-busy={loading}
      >
        {loading
          ? Array.from({ length: 6 }).map((_, i) => (
              <Card key={i} className="rounded-xl border border-slate-200 dark:border-slate-800 p-4">
                <Skeleton className="h-4 w-20" />
                <Skeleton className="mt-3 h-8 w-10" />
              </Card>
            ))
          : metrics.map((m) => (
              <Card
                key={m.label}
                className={`metric-card ${m.color} rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-4 shadow-xs relative overflow-hidden`}
              >
                <div className="flex items-center justify-between">
                  <p className="text-xs font-medium text-slate-500 dark:text-slate-400">
                    {m.label}
                  </p>
                  <span className={`p-1.5 rounded-lg ${m.badgeColor}`}>
                    <AppIcon name={m.icon} size={14} />
                  </span>
                </div>
                <p className="text-2xl font-extrabold text-slate-900 dark:text-white mt-1.5 tabular-nums">
                  {m.value}
                </p>
                <p className="text-xs text-slate-400 dark:text-slate-500 mt-1 leading-relaxed">{m.detail}</p>
              </Card>
            ))}
      </section>

      {/* Biểu đồ tiến độ theo thời gian & phân bố công việc (Progress Chart over time) */}
      {summary && !loading && (
        <Card className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-5 shadow-xs">
          <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 dark:border-slate-800 pb-3">
            <div>
              <h2 className="text-sm font-bold text-slate-900 dark:text-white flex items-center gap-2">
                <AppIcon name="report" size={16} className="text-blue-600" />
                <span>Biểu đồ tiến độ công việc theo thời gian</span>
              </h2>
              <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
                Tỷ lệ hoàn thành đạt {summary.completion_rate.toFixed(1)}% với {summary.completed} / {summary.total} công việc đã xử lý.
              </p>
            </div>
            <div className="flex items-center gap-2">
              <span className="inline-flex items-center gap-1.5 text-xs text-slate-600 dark:text-slate-300 bg-slate-50 dark:bg-slate-800 px-2.5 py-1 rounded-md border border-slate-200 dark:border-slate-700">
                <span className="h-2 w-2 rounded-full bg-emerald-500 inline-block" /> Hoàn thành ({summary.completed})
              </span>
              <span className="inline-flex items-center gap-1.5 text-xs text-slate-600 dark:text-slate-300 bg-slate-50 dark:bg-slate-800 px-2.5 py-1 rounded-md border border-slate-200 dark:border-slate-700">
                <span className="h-2 w-2 rounded-full bg-blue-500 inline-block" /> Đang làm ({summary.in_progress})
              </span>
              <span className="inline-flex items-center gap-1.5 text-xs text-slate-600 dark:text-slate-300 bg-slate-50 dark:bg-slate-800 px-2.5 py-1 rounded-md border border-slate-200 dark:border-slate-700">
                <span className="h-2 w-2 rounded-full bg-slate-400 inline-block" /> Cần làm ({summary.todo})
              </span>
              <span className="inline-flex items-center gap-1.5 text-xs text-slate-600 dark:text-slate-300 bg-slate-50 dark:bg-slate-800 px-2.5 py-1 rounded-md border border-slate-200 dark:border-slate-700">
                <span className="h-2 w-2 rounded-full bg-red-500 inline-block" /> Quá hạn ({summary.overdue})
              </span>
            </div>
          </div>

          {/* Progress Visual Bar */}
          <div className="mt-4">
            <div className="h-3 w-full rounded-full bg-slate-100 dark:bg-slate-800 overflow-hidden flex shadow-inner">
              <div
                style={{ width: `${summary.total > 0 ? (summary.completed / summary.total) * 100 : 0}%` }}
                className="bg-emerald-500 transition-all duration-500"
                title={`Hoàn thành: ${summary.completed}`}
              />
              <div
                style={{ width: `${summary.total > 0 ? (summary.in_progress / summary.total) * 100 : 0}%` }}
                className="bg-blue-600 transition-all duration-500"
                title={`Đang làm: ${summary.in_progress}`}
              />
              <div
                style={{ width: `${summary.total > 0 ? (summary.pending_approval / summary.total) * 100 : 0}%` }}
                className="bg-amber-400 transition-all duration-500"
                title={`Chờ duyệt: ${summary.pending_approval}`}
              />
              <div
                style={{ width: `${summary.total > 0 ? (summary.todo / summary.total) * 100 : 0}%` }}
                className="bg-slate-300 dark:bg-slate-700 transition-all duration-500"
                title={`Cần làm: ${summary.todo}`}
              />
            </div>
          </div>

          {/* SVG Progress Curve Simulation */}
          <div className="mt-4 pt-3 border-t border-slate-100 dark:border-slate-800 grid grid-cols-1 md:grid-cols-4 gap-4 text-xs">
            <div className="p-3 rounded-lg bg-slate-50 dark:bg-slate-800/60 border border-slate-100 dark:border-slate-800">
              <span className="text-slate-400">Đến hạn hôm nay:</span>
              <p className="font-bold text-slate-800 dark:text-white text-base mt-0.5">{summary.due_today} việc</p>
            </div>
            <div className="p-3 rounded-lg bg-slate-50 dark:bg-slate-800/60 border border-slate-100 dark:border-slate-800">
              <span className="text-slate-400">Đến hạn 7 ngày tới:</span>
              <p className="font-bold text-slate-800 dark:text-white text-base mt-0.5">{summary.due_soon} việc</p>
            </div>
            <div className="p-3 rounded-lg bg-slate-50 dark:bg-slate-800/60 border border-slate-100 dark:border-slate-800">
              <span className="text-slate-400">Chờ phê duyệt:</span>
              <p className="font-bold text-slate-800 dark:text-white text-base mt-0.5">{summary.pending_approval} việc</p>
            </div>
            <div className="p-3 rounded-lg bg-slate-50 dark:bg-slate-800/60 border border-slate-100 dark:border-slate-800 flex items-center justify-between">
              <div>
                <span className="text-slate-400">Lịch trình làm việc:</span>
                <p className="font-semibold text-blue-600 dark:text-blue-400 mt-0.5">Mở lịch chi tiết</p>
              </div>
              <Link to="/calendar" className="btn btn-primary !h-7 !px-2 text-xs">
                Xem Lịch →
              </Link>
            </div>
          </div>
        </Card>
      )}

      {/* Main 2-Column Layout: Left Focus Tasks, Right Activity & Notes */}
      <section className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1.3fr)_minmax(0,1fr)]">
        {/* Left Column: Việc cần chú ý (Focus / Today's tasks) */}
        <div className="space-y-6">
          <Card className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-5 shadow-xs">
            <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 dark:border-slate-800 pb-3">
              <div>
                <h2 className="section-title text-base font-bold text-slate-900 dark:text-white flex items-center gap-2">
                  <AppIcon name="tasks" size={18} className="text-blue-600" />
                  <span>Việc cần làm hôm nay & chú ý</span>
                </h2>
                <p className="mt-0.5 text-xs text-slate-500">
                  Ưu tiên công việc đến hạn hoặc quá hạn cần giải quyết.
                </p>
              </div>
              <Link className="text-xs font-semibold text-blue-600 dark:text-blue-400 hover:underline" to="/tasks">
                Xem tất cả →
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
                  title="Bạn chưa có công việc cần xử lý hôm nay"
                  description="Các công việc được giao hoặc tạo mới sẽ xuất hiện tại đây."
                />
              </div>
            ) : (
              <ul className="mt-2 divide-y divide-slate-100 dark:divide-slate-800">
                {focus.map((task) => (
                  <li
                    key={task.id}
                    className="flex items-center justify-between gap-3 py-3 hover:bg-slate-50 dark:hover:bg-slate-800/50 px-2 rounded-xl transition"
                  >
                    <div className="min-w-0 flex-1">
                      <Link
                        to={task.scope === "PERSONAL" ? "/my-tasks" : `/tasks/${task.id}`}
                        className="block truncate font-semibold text-sm text-slate-900 dark:text-white hover:text-blue-600"
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
                    <div className="flex items-center gap-2 shrink-0">
                      <StatusBadge
                        state={task.blocked || task.blocked_flag ? "BLOCKED" : task.state}
                      >
                        {task.blocked || task.blocked_flag ? "Bị chặn" : undefined}
                      </StatusBadge>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </div>

        {/* Right Column: Recent Activity/Notifications & Pinned Notes */}
        <div className="space-y-6">
          {/* Thông báo & Hoạt động gần đây (Recent Notifications & Activity) */}
          <Card className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-5 shadow-xs">
            <div className="flex items-center justify-between border-b border-slate-100 dark:border-slate-800 pb-2 mb-3">
              <h2 className="section-title text-sm font-bold text-slate-900 dark:text-white flex items-center gap-2">
                <AppIcon name="bell" size={16} className="text-blue-600" />
                <span>Thông báo & hoạt động gần đây</span>
              </h2>
              <Link to="/notifications" className="text-xs text-blue-600 dark:text-blue-400 font-semibold hover:underline">
                Xem tất cả
              </Link>
            </div>
            {recentNotifications.length === 0 ? (
              <p className="text-xs text-slate-400 py-3">
                Chưa có hoạt động mới nào được ghi nhận.
              </p>
            ) : (
              <ul className="space-y-2.5">
                {recentNotifications.map((notif) => (
                  <li
                    key={notif.id}
                    className="p-2.5 rounded-lg border border-slate-100 dark:border-slate-800 bg-slate-50/70 dark:bg-slate-800/40 hover:border-blue-300 dark:hover:border-slate-700 transition text-xs"
                  >
                    <div className="flex items-start justify-between gap-2">
                      <span className="font-semibold text-slate-800 dark:text-slate-200">
                        {notif.title || notif.kind || "Thông báo hệ thống"}
                      </span>
                      {!notif.read && (
                        <span className="h-2 w-2 rounded-full bg-blue-600 shrink-0 mt-1" />
                      )}
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </Card>

          {/* Ghi chú đã ghim (Pinned Notes Widget) */}
          <Card className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-5 shadow-xs">
            <div className="flex items-center justify-between border-b border-slate-100 dark:border-slate-800 pb-2 mb-3">
              <h2 className="section-title text-sm font-bold text-slate-900 dark:text-white flex items-center gap-1.5">
                <span className="text-amber-500 font-bold">★</span>
                <span>Ghi chú đã ghim</span>
              </h2>
              <Link to="/notes" className="text-xs text-blue-600 dark:text-blue-400 font-semibold hover:underline">
                Mở sổ tay
              </Link>
            </div>
            {pinnedNotes.length === 0 ? (
              <p className="text-xs text-slate-400 py-3">
                Chưa có ghi chú nào được ghim. Bạn có thể ghim ghi chú quan trọng từ trang Ghi chú.
              </p>
            ) : (
              <div className="space-y-2">
                {pinnedNotes.map((note) => (
                  <Link
                    key={note.id}
                    to={`/notes?id=${note.id}`}
                    className="block p-2.5 rounded-lg border border-slate-100 dark:border-slate-800 bg-slate-50/70 dark:bg-slate-800/40 hover:border-blue-400 transition"
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
        </div>
      </section>

      {/* Quick Task Creation Modal */}
      {quickCreateOpen && (
        <div
          role="dialog"
          aria-modal="true"
          aria-label="Tạo nhanh công việc"
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-xs p-4"
        >
          <div className="w-full max-w-md rounded-2xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 p-6 shadow-xl animate-scale-in">
            <div className="flex items-center justify-between border-b border-slate-100 dark:border-slate-800 pb-3 mb-4">
              <h3 className="font-bold text-base text-slate-900 dark:text-white flex items-center gap-2">
                <AppIcon name="plus" size={18} className="text-blue-600" />
                <span>Tạo công việc nhanh</span>
              </h3>
              <button
                onClick={() => setQuickCreateOpen(false)}
                className="text-slate-400 hover:text-slate-600 dark:hover:text-white"
              >
                <AppIcon name="close" size={18} />
              </button>
            </div>

            <form onSubmit={handleQuickCreate} className="space-y-4">
              <div>
                <label className="label text-xs">Loại công việc</label>
                <div className="grid grid-cols-2 gap-2">
                  <button
                    type="button"
                    onClick={() => setQuickType("personal")}
                    className={`px-3 py-2 text-xs font-semibold rounded-lg border transition ${
                      quickType === "personal"
                        ? "border-blue-600 bg-blue-50 text-blue-600 dark:bg-blue-950/50"
                        : "border-slate-200 dark:border-slate-800 text-slate-600 dark:text-slate-400"
                    }`}
                  >
                    Việc cá nhân
                  </button>
                  <button
                    type="button"
                    onClick={() => setQuickType("project")}
                    className={`px-3 py-2 text-xs font-semibold rounded-lg border transition ${
                      quickType === "project"
                        ? "border-blue-600 bg-blue-50 text-blue-600 dark:bg-blue-950/50"
                        : "border-slate-200 dark:border-slate-800 text-slate-600 dark:text-slate-400"
                    }`}
                  >
                    Việc dự án
                  </button>
                </div>
              </div>

              {quickType === "project" && (
                <div>
                  <label className="label text-xs">Chọn dự án</label>
                  <select
                    className="select text-xs w-full"
                    value={quickProjectId}
                    onChange={(e) => setQuickProjectId(e.target.value)}
                  >
                    {projectsList.map((p) => (
                      <option key={p.id} value={p.id}>
                        {p.name}
                      </option>
                    ))}
                  </select>
                </div>
              )}

              <div>
                <label className="label text-xs">Tiêu đề công việc</label>
                <input
                  type="text"
                  required
                  placeholder="Bạn cần làm gì?"
                  value={quickTitle}
                  onChange={(e) => setQuickTitle(e.target.value)}
                  className="input text-xs"
                  autoFocus
                />
              </div>

              <div className="flex justify-end gap-2 pt-2 border-t border-slate-100 dark:border-slate-800">
                <button
                  type="button"
                  disabled={creatingTask}
                  onClick={() => setQuickCreateOpen(false)}
                  className="btn btn-secondary text-xs"
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  disabled={creatingTask || !quickTitle.trim()}
                  className="btn btn-primary text-xs"
                >
                  {creatingTask ? "Đang tạo..." : "Tạo công việc"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
