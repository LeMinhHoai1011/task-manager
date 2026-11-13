import { useEffect, useState, useRef, useMemo } from "react";
import { apiFetch } from "../api/client";
import PersonalChecklist from "./PersonalChecklist";
import { Card, StatusBadge, stateLabel } from "../components/ui";
import FilterBar from "../components/FilterBar";
import AppIcon from "../components/AppIcon";

type Task = {
  id: string;
  title: string;
  state: string;
  version: number;
  description?: string;
  notes?: string;
  priority?: string;
  startAt?: string | null;
  dueAt?: string | null;
  dueIsDateOnly?: boolean;
  dueTimezone?: string;
  dueDateInput?: string;
};

function deadlineDate(task: Task) {
  if (task.dueDateInput !== undefined) return task.dueDateInput;
  if (!task.dueAt) return "";
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: task.dueTimezone || "UTC",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(new Date(task.dueAt));
  return ["year", "month", "day"].map((type) => parts.find((p) => p.type === type)?.value).join("-");
}

function localDateTime(value?: string | null) {
  if (!value) return "";
  const date = new Date(value);
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
}

export default function MyTasks() {
  const priorityNames: Record<string,string> = { LOW: "Thấp", MEDIUM: "Trung bình", HIGH: "Cao", URGENT: "Khẩn cấp" };
  const sortNames: Record<string,string> = { createdAt: "Mới nhất", dueAt: "Hạn hoàn thành", title: "Tiêu đề", priority: "Ưu tiên cao trước" };
  const [items, setItems] = useState<Task[]>([]);
  const [title, setTitle] = useState("");
  const pendingCreate = useRef<{ title: string; key: string } | null>(null);
  const [page, setPage] = useState(0);
  const [last, setLast] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [query, setQuery] = useState("");
  const [appliedQuery, setAppliedQuery] = useState("");
  const [state, setState] = useState("");
  const [deleted, setDeleted] = useState(false);
  const [sort, setSort] = useState("createdAt");
  const [priority, setPriority] = useState("");
  const [tag, setTag] = useState("");
  const [overdue, setOverdue] = useState(false);
  const [editing, setEditing] = useState<Task | null>(null);
  const [detailId, setDetailId] = useState<string | null>(null);
  const [summary, setSummary] = useState<Record<string, number> | null>(null);
  const [viewMode, setViewMode] = useState<"list" | "kanban" | "calendar">("list");
  const requestSequence = useRef(0);
  const summarySequence = useRef(0);

  async function loadSummary() {
    const sequence = ++summarySequence.current;
    try {
      const result = await apiFetch<{ data: Record<string, number> }>("/me/tasks/summary");
      if (sequence === summarySequence.current) setSummary(result.data);
    } catch {
      if (sequence === summarySequence.current) setError("Không tải được thống kê.");
    }
  }

  async function load() {
    const sequence = ++requestSequence.current;
    setBusy(true);
    setError("");
    try {
      const result = await apiFetch<{ data: { content: Task[]; last: boolean } }>("/me/tasks", {
        query: { page, size: 20, q: appliedQuery, state, deleted, sort, priority, tag, overdue },
      });
      if (sequence !== requestSequence.current) return;
      setItems(result.data.content);
      setLast(result.data.last);
      void loadSummary();
    } catch {
      if (sequence === requestSequence.current) setError("Không tải được công việc. Vui lòng thử lại.");
    } finally {
      if (sequence === requestSequence.current) setBusy(false);
    }
  }

  useEffect(() => {
    void load();
    return () => {
      requestSequence.current++;
      summarySequence.current++;
    };
  }, [page, state, deleted, sort, priority, overdue, appliedQuery, tag]);

  async function start(task: Task) {
    setBusy(true);
    setError("");
    try {
      await apiFetch(`/me/tasks/${task.id}/start`, {
        method: "POST",
        ifMatch: String(task.version),
      });
      await load();
    } catch {
      setError("Không bắt đầu được công việc. Hãy tải lại dữ liệu.");
    } finally {
      setBusy(false);
    }
  }

  async function change(task: Task, action: "save" | "delete" | "restore") {
    setBusy(true);
    setError("");
    try {
      await apiFetch(`/me/tasks/${task.id}${action === "restore" ? "/restore" : ""}`, {
        method: action === "save" ? "PATCH" : action === "delete" ? "DELETE" : "POST",
        ifMatch: String(task.version),
        ...(action === "save"
          ? {
              body: {
                title: task.title,
                description: task.description ?? null,
                notes: task.notes ?? null,
                priority: task.priority ?? "MEDIUM",
                startAt: task.startAt || null,
                dueAt: task.dueIsDateOnly
                  ? deadlineDate(task)
                    ? `${deadlineDate(task)}T00:00:00Z`
                    : null
                  : task.dueAt || null,
                dueIsDateOnly: !!task.dueIsDateOnly,
                dueTimezone: task.dueTimezone || Intl.DateTimeFormat().resolvedOptions().timeZone,
              },
            }
          : {}),
      });
      setEditing(null);
      await load();
    } catch {
      setError("Không lưu được thay đổi. Có thể công việc đã được sửa ở nơi khác; hãy tải lại.");
    } finally {
      setBusy(false);
    }
  }

  async function create(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    if (!pendingCreate.current || pendingCreate.current.title !== title)
      pendingCreate.current = { title, key: crypto.randomUUID() };
    try {
      await apiFetch("/me/tasks", { body: { title }, idempotencyKey: pendingCreate.current.key });
      pendingCreate.current = null;
      setTitle("");
      await load();
    } catch {
      setError("Không tạo được công việc.");
    } finally {
      setBusy(false);
    }
  }

  async function toggle(task: Task) {
    setBusy(true);
    setError("");
    try {
      await apiFetch(`/me/tasks/${task.id}/completion`, {
        method: "PATCH",
        ifMatch: String(task.version),
        body: { completed: task.state !== "COMPLETED" },
      });
      await load();
    } catch {
      setError("Không lưu được trạng thái. Hãy tải lại trước khi thử tiếp.");
    } finally {
      setBusy(false);
    }
  }

  // Kanban grouping
  const kanbanGroups = useMemo(() => {
    return {
      TODO: items.filter((t) => t.state === "TODO"),
      IN_PROGRESS: items.filter((t) => t.state === "IN_PROGRESS"),
      COMPLETED: items.filter((t) => t.state === "COMPLETED"),
    };
  }, [items]);

  return (
    <section className="personal-tasks personal-workspace mx-auto max-w-6xl space-y-5">
      {/* Page Heading & View mode Switcher */}
      <header className="page-heading flex flex-wrap items-center justify-between gap-4 border-b border-slate-200 dark:border-slate-800 pb-4">
        <div>
          <h1 className="page-title text-2xl font-bold tracking-tight text-slate-900 dark:text-white flex items-center gap-2">
            <AppIcon name="personal" size={24} className="text-blue-600" />
            <span>Việc của tôi</span>
          </h1>
          <p className="page-subtitle text-xs text-slate-500 dark:text-slate-400 mt-0.5">
            Không gian quản lý công việc, kế hoạch cá nhân, ghi chú và checklist riêng tư.
          </p>
        </div>

        <div className="flex items-center gap-3">
          {/* Segmented View Switcher: List, Kanban, Calendar */}
          <div className="segmented">
            <button
              type="button"
              onClick={() => setViewMode("list")}
              className={`flex items-center gap-1.5 ${viewMode === "list" ? "segmented-active" : ""}`}
            >
              <AppIcon name="list" size={15} />
              <span className="hidden sm:inline">Danh sách</span>
            </button>
            <button
              type="button"
              onClick={() => setViewMode("kanban")}
              className={`flex items-center gap-1.5 ${viewMode === "kanban" ? "segmented-active" : ""}`}
            >
              <AppIcon name="kanban" size={15} />
              <span className="hidden sm:inline">Bảng Kanban</span>
            </button>
            <button
              type="button"
              onClick={() => setViewMode("calendar")}
              className={`flex items-center gap-1.5 ${viewMode === "calendar" ? "segmented-active" : ""}`}
            >
              <AppIcon name="calendar" size={15} />
              <span className="hidden sm:inline">Lịch trình</span>
            </button>
          </div>

          <button
            className="btn btn-secondary text-xs flex items-center gap-1.5"
            disabled={busy}
            onClick={() => void load()}
          >
            <AppIcon name="refresh" size={14} />
            <span>Làm mới</span>
          </button>
        </div>
      </header>

      {/* KPI Summary Cards */}
      {summary && (
        <div
          className="personal-summary grid grid-cols-2 md:grid-cols-5 gap-3 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-4 shadow-xs"
          aria-label="Thống kê cá nhân"
        >
          <div className="p-2 border-r border-slate-100 dark:border-slate-800">
            <span className="text-xs text-slate-500 font-medium">Tổng công việc</span>
            <strong className="text-2xl font-bold text-slate-900 dark:text-white mt-1 block">
              {summary.total}
            </strong>
          </div>
          <div className="p-2 border-r border-slate-100 dark:border-slate-800">
            <span className="text-xs text-slate-500 font-medium">Cần làm</span>
            <strong className="text-2xl font-bold text-blue-600 dark:text-blue-400 mt-1 block">
              {summary.TODO}
            </strong>
          </div>
          <div className="p-2 border-r border-slate-100 dark:border-slate-800">
            <span className="text-xs text-slate-500 font-medium">Đang làm</span>
            <strong className="text-2xl font-bold text-indigo-600 dark:text-indigo-400 mt-1 block">
              {summary.IN_PROGRESS}
            </strong>
          </div>
          <div className="p-2 border-r border-slate-100 dark:border-slate-800">
            <span className="text-xs text-slate-500 font-medium">Hoàn thành</span>
            <strong className="text-2xl font-bold text-emerald-600 dark:text-emerald-400 mt-1 block">
              {summary.COMPLETED}
            </strong>
          </div>
          <div className="p-2">
            <span className="text-xs text-slate-500 font-medium">Quá hạn</span>
            <strong className="text-2xl font-bold text-red-600 dark:text-red-400 mt-1 block">
              {summary.overdue}
            </strong>
          </div>
        </div>
      )}

      {/* Quick Add Form */}
      {!deleted && (
        <form
          onSubmit={create}
          className="quick-create flex items-center gap-3 p-3 rounded-xl border border-blue-200 dark:border-slate-800 bg-white dark:bg-slate-900 shadow-xs"
        >
          <span className="quick-create-plus text-blue-600 font-bold text-xl ml-1" aria-hidden="true">
            +
          </span>
          <input
            aria-label="Tiêu đề công việc"
            className="input flex-1 !border-0 !shadow-none !bg-transparent text-sm focus:!ring-0 px-1"
            placeholder="Bạn cần làm gì tiếp theo? (Nhấn Enter để thêm nhanh)"
            value={title}
            maxLength={200}
            required
            onChange={(e) => setTitle(e.target.value)}
          />
          <button
            className="btn btn-primary text-xs shrink-0 px-4"
            disabled={busy || !title.trim()}
          >
            Thêm việc
          </button>
        </form>
      )}

      <FilterBar
        busy={busy}
        resultLabel={`Đang hiển thị ${items.length} công việc`}
        activeFilters={[
          ...(appliedQuery ? [{ key: "query", label: `Tìm: ${appliedQuery}`, onRemove: () => { setQuery(""); setAppliedQuery(""); setPage(0); } }] : []),
          ...(state ? [{ key: "state", label: stateLabel(state), onRemove: () => { setState(""); setPage(0); } }] : []),
          ...(priority ? [{ key: "priority", label: `Ưu tiên: ${priorityNames[priority]}`, onRemove: () => { setPriority(""); setPage(0); } }] : []),
          ...(tag ? [{ key: "tag", label: `Nhãn: ${tag}`, onRemove: () => { setTag(""); setPage(0); } }] : []),
          ...(overdue ? [{ key: "overdue", label: "Chỉ việc quá hạn", onRemove: () => { setOverdue(false); setPage(0); } }] : []),
          ...(deleted ? [{ key: "deleted", label: "Thùng rác", onRemove: () => { setDeleted(false); setPage(0); } }] : []),
          ...(sort !== "createdAt" ? [{ key: "sort", label: `Sắp xếp: ${sortNames[sort]}`, onRemove: () => { setSort("createdAt"); setPage(0); } }] : []),
        ]}
        onReset={() => {
          setQuery(""); setAppliedQuery(""); setState(""); setPriority(""); setTag("");
          setOverdue(false); setDeleted(false); setSort("createdAt"); setPage(0);
        }}
        advanced={<>
          <div className="filter-grid">
            <label className="filter-field"><span>Độ ưu tiên</span>
              <select aria-label="Ưu tiên" className="select" value={priority} onChange={e => { setPage(0); setPriority(e.target.value); }}>
                <option value="">Tất cả mức ưu tiên</option>
                {Object.entries(priorityNames).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
              </select>
            </label>
            <label className="filter-field"><span>Nhãn công việc</span>
              <input aria-label="Nhãn" className="input" placeholder="Ví dụ: cá nhân, kế hoạch…" value={tag} onChange={e => { setTag(e.target.value); setPage(0); }} />
            </label>
          </div>
          <div className="filter-checks">
            <label><input type="checkbox" className="checkbox" checked={overdue} onChange={e => { setPage(0); setOverdue(e.target.checked); }} />Chỉ việc quá hạn</label>
            <label><input type="checkbox" className="checkbox" checked={deleted} onChange={e => { setPage(0); setDeleted(e.target.checked); }} />Xem thùng rác</label>
          </div>
        </>}
      >
        <form className="filter-search" onSubmit={e => {
          e.preventDefault();
          setPage(0);
          if (appliedQuery === query.trim() && page === 0) void load();
          else setAppliedQuery(query.trim());
        }}>
          <label className="filter-field"><span>Tìm công việc</span>
            <span className="filter-search-box"><AppIcon name="search" size={16} />
              <input aria-label="Tìm công việc" className="input" value={query} onChange={e => setQuery(e.target.value)} placeholder="Tìm theo tiêu đề hoặc mô tả…" />
            </span>
          </label>
          <button type="submit" className="btn btn-primary" disabled={busy}>Tìm</button>
        </form>
        <label className="filter-field"><span>Trạng thái</span>
          <select aria-label="Trạng thái" className="select" value={state} onChange={e => { setPage(0); setState(e.target.value); }}>
            <option value="">Tất cả trạng thái</option>
            <option value="TODO">Cần làm</option><option value="IN_PROGRESS">Đang làm</option><option value="COMPLETED">Hoàn thành</option>
          </select>
        </label>
        <label className="filter-field"><span>Sắp xếp</span>
          <select aria-label="Sắp xếp" className="select" value={sort} onChange={e => { setPage(0); setSort(e.target.value); }}>
            {Object.entries(sortNames).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select>
        </label>
      </FilterBar>

      {error && (
        <div role="alert" className="alert-error flex items-center justify-between">
          <span>{error}</span>
          <button className="btn btn-sm btn-secondary" onClick={() => void load()}>
            Tải lại
          </button>
        </div>
      )}

      {busy && (
        <p role="status" className="text-xs text-slate-500 animate-pulse">
          Đang xử lý…
        </p>
      )}

      {!busy && !error && items.length === 0 && (
        <div className="empty-state">
          <h2 className="section-title text-base font-bold text-slate-900 dark:text-white">
            {deleted ? "Thùng rác đang trống" : "Chưa có công việc phù hợp"}
          </h2>
          <p className="mt-2 text-xs text-slate-500">
            {deleted
              ? "Công việc đã xóa sẽ xuất hiện tại đây để khôi phục."
              : "Thêm công việc ở trên hoặc thay đổi bộ lọc để xem kết quả."}
          </p>
        </div>
      )}

      {/* VIEW MODE 1: LIST VIEW */}
      {viewMode === "list" && items.length > 0 && (
        <ul className="personal-task-list rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 shadow-xs divide-y divide-slate-100 dark:divide-slate-800">
          {items.map((task) => (
            <li
              key={task.id}
              className="personal-task-row flex flex-wrap items-center justify-between gap-3 p-3.5 hover:bg-slate-50 dark:hover:bg-slate-800/50 transition"
            >
              <div className="task-row-content flex-1 min-w-[260px]">
                <label className="task-row-title flex items-center gap-2.5 cursor-pointer">
                  <input
                    type="checkbox"
                    className="checkbox"
                    disabled={busy || deleted}
                    checked={task.state === "COMPLETED"}
                    onChange={() => void toggle(task)}
                  />
                  <span
                    className={`font-semibold text-sm ${
                      task.state === "COMPLETED"
                        ? "line-through text-slate-400 dark:text-slate-500"
                        : "text-slate-900 dark:text-white"
                    }`}
                  >
                    {task.title}
                  </span>
                </label>
                <div className="task-row-meta flex flex-wrap items-center gap-2 text-xs text-slate-400 mt-1 ml-6">
                  <StatusBadge state={task.state} />
                  <span className={`priority-dot priority-${task.priority?.toLowerCase()}`}>
                    {(
                      {
                        LOW: "Thấp",
                        MEDIUM: "Trung bình",
                        HIGH: "Cao",
                        URGENT: "Khẩn cấp",
                      } as Record<string, string>
                    )[task.priority ?? "MEDIUM"] ?? task.priority}
                  </span>
                  <span>
                    📅{" "}
                    {task.dueAt
                      ? task.dueIsDateOnly
                        ? deadlineDate(task)
                        : new Date(task.dueAt).toLocaleString("vi-VN", {
                            month: "numeric",
                            day: "numeric",
                            hour: "2-digit",
                            minute: "2-digit",
                          })
                      : "Chưa đặt hạn"}
                  </span>
                </div>
              </div>

              {deleted ? (
                <button
                  disabled={busy}
                  className="btn btn-secondary text-xs"
                  onClick={() => void change(task, "restore")}
                >
                  Khôi phục
                </button>
              ) : (
                <div className="task-row-actions flex items-center gap-2">
                  {task.state === "TODO" && (
                    <button
                      disabled={busy}
                      aria-label={`Bắt đầu: ${task.title}`}
                      className="btn btn-primary !h-7 !px-2.5 text-xs"
                      onClick={() => void start(task)}
                    >
                      Bắt đầu
                    </button>
                  )}
                  <button
                    disabled={busy}
                    className="btn btn-secondary !h-7 !px-2.5 text-xs"
                    onClick={() => setEditing({ ...task })}
                  >
                    Sửa chi tiết
                  </button>
                  <button
                    className="task-delete text-xs text-red-600 hover:text-red-700 px-2 py-1"
                    disabled={busy}
                    onClick={() => void change(task, "delete")}
                  >
                    Chuyển vào thùng rác
                  </button>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}

      {/* VIEW MODE 2: KANBAN BOARD */}
      {viewMode === "kanban" && items.length > 0 && (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4" aria-label="Bảng Kanban cá nhân">
          {(
            [
              { key: "TODO", title: "Cần làm", items: kanbanGroups.TODO, badge: "badge-todo" },
              {
                key: "IN_PROGRESS",
                title: "Đang làm",
                items: kanbanGroups.IN_PROGRESS,
                badge: "badge-in-progress",
              },
              {
                key: "COMPLETED",
                title: "Hoàn thành",
                items: kanbanGroups.COMPLETED,
                badge: "badge-completed",
              },
            ] as const
          ).map((col) => (
            <Card
              key={col.key}
              className="rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50/70 dark:bg-slate-900/60 p-3.5 flex flex-col min-h-[400px]"
            >
              <div className="flex items-center justify-between pb-2 mb-3 border-b border-slate-200 dark:border-slate-800">
                <span className={`badge-state ${col.badge}`}>{col.title}</span>
                <span className="text-xs font-bold text-slate-500">{col.items.length}</span>
              </div>
              <div className="space-y-2.5 flex-1">
                {col.items.length === 0 ? (
                  <p className="text-xs text-slate-400 text-center py-8">Chưa có mục nào</p>
                ) : (
                  col.items.map((task) => (
                    <div
                      key={task.id}
                      className="p-3 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-850 shadow-xs hover:border-blue-400 transition"
                    >
                      <div className="flex items-start justify-between gap-2">
                        <span
                          className={`font-semibold text-xs leading-snug ${
                            task.state === "COMPLETED" ? "line-through text-slate-400" : ""
                          }`}
                        >
                          {task.title}
                        </span>
                        <button
                          onClick={() => setEditing({ ...task })}
                          className="text-slate-400 hover:text-blue-600 text-xs"
                          title="Sửa"
                        >
                          <AppIcon name="edit" size={13} />
                        </button>
                      </div>

                      <div className="mt-2 flex flex-wrap items-center gap-1.5 text-[11px] text-slate-400">
                        <span className={`priority-dot priority-${task.priority?.toLowerCase()}`}>
                          {task.priority}
                        </span>
                        {task.dueAt && <span>📅 {task.dueAt.slice(0, 10)}</span>}
                      </div>

                      <div className="mt-2.5 pt-2 border-t border-slate-100 dark:border-slate-800 flex items-center justify-between text-xs">
                        {task.state !== "COMPLETED" ? (
                          <button
                            onClick={() => void toggle(task)}
                            className="text-[11px] text-emerald-600 font-semibold hover:underline"
                          >
                            ✓ Đánh dấu xong
                          </button>
                        ) : (
                          <button
                            onClick={() => void toggle(task)}
                            className="text-[11px] text-slate-400 hover:underline"
                          >
                            Mở lại việc
                          </button>
                        )}
                        <button
                          onClick={() => setEditing({ ...task })}
                          className="text-[11px] text-blue-600 hover:underline"
                        >
                          Chi tiết
                        </button>
                      </div>
                    </div>
                  ))
                )}
              </div>
            </Card>
          ))}
        </div>
      )}

      {/* VIEW MODE 3: CALENDAR VIEW */}
      {viewMode === "calendar" && items.length > 0 && (
        <Card className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-5 shadow-xs">
          <h3 className="font-bold text-sm text-slate-900 dark:text-white mb-3 flex items-center gap-2">
            <AppIcon name="calendar" size={16} className="text-blue-600" />
            <span>Kế hoạch theo hạn hoàn thành</span>
          </h3>
          <div className="space-y-3">
            {items
              .slice()
              .sort((a, b) => (a.dueAt || "9999").localeCompare(b.dueAt || "9999"))
              .map((task) => (
                <div
                  key={task.id}
                  className="flex items-center justify-between gap-3 p-3 rounded-lg border border-slate-100 dark:border-slate-800 bg-slate-50/70 dark:bg-slate-800/40 text-xs"
                >
                  <div className="flex items-center gap-3">
                    <span className="font-bold text-blue-600 dark:text-blue-400 min-w-[80px]">
                      {task.dueAt ? task.dueAt.slice(0, 10) : "Chưa đặt"}
                    </span>
                    <span className="font-semibold text-slate-800 dark:text-white">{task.title}</span>
                  </div>
                  <div className="flex items-center gap-2">
                    <StatusBadge state={task.state} />
                    <button
                      onClick={() => setEditing({ ...task })}
                      className="text-blue-600 hover:underline text-xs"
                    >
                      Sửa
                    </button>
                  </div>
                </div>
              ))}
          </div>
        </Card>
      )}

      {/* Checklist & Personal Workspace */}
      {!deleted && (
        <section className="card checklist-workspace rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-5 shadow-xs">
          <h2 className="section-title text-sm font-bold text-slate-900 dark:text-white">
            Chia nhỏ để dễ hoàn thành
          </h2>
          <p className="page-subtitle text-xs text-slate-500">
            Chọn một công việc để quản lý checklist và ghi chú chi tiết.
          </p>
          <label className="block mt-3 text-xs text-slate-600 dark:text-slate-400">
            Checklist của công việc
            <select
              className="select text-xs mt-1 w-full max-w-md"
              value={detailId ?? ""}
              onChange={(e) => setDetailId(e.target.value || null)}
            >
              <option value="">Chọn công việc</option>
              {items.map((task) => (
                <option key={task.id} value={task.id}>
                  {task.title}
                </option>
              ))}
            </select>
          </label>
          {detailId && (
            <div className="mt-4 pt-3 border-t border-slate-100 dark:border-slate-800">
              <PersonalChecklist
                key={`${detailId}:${items.find((task) => task.id === detailId)?.version ?? 0}`}
                id={detailId}
                onChanged={() => void load()}
              />
            </div>
          )}
        </section>
      )}

      {/* Quick Edit Dialog Modal */}
      {editing && (
        <div
          role="dialog"
          aria-modal="true"
          aria-label="Sửa công việc"
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-xs p-4"
        >
          <form
            className="w-full max-w-lg rounded-2xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 p-6 shadow-xl space-y-4 max-h-[90vh] overflow-y-auto"
            onSubmit={(e) => {
              e.preventDefault();
              void change(editing, "save");
            }}
          >
            <div className="flex items-center justify-between border-b border-slate-100 dark:border-slate-800 pb-3">
              <h2 className="text-base font-bold text-slate-900 dark:text-white flex items-center gap-2">
                <AppIcon name="edit" size={18} className="text-blue-600" />
                <span>Sửa công việc</span>
              </h2>
              <button
                type="button"
                onClick={() => setEditing(null)}
                className="text-slate-400 hover:text-slate-600 dark:hover:text-white"
              >
                <AppIcon name="close" size={18} />
              </button>
            </div>

            <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300">
              Tiêu đề
              <input
                required
                maxLength={200}
                className="input text-xs mt-1"
                value={editing.title}
                onChange={(e) => setEditing({ ...editing, title: e.target.value })}
              />
            </label>

            <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300">
              Mô tả
              <textarea
                className="textarea text-xs mt-1"
                rows={3}
                value={editing.description ?? ""}
                onChange={(e) => setEditing({ ...editing, description: e.target.value })}
              />
            </label>

            <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300">
              Ghi chú
              <textarea
                className="textarea text-xs mt-1"
                rows={2}
                value={editing.notes ?? ""}
                onChange={(e) => setEditing({ ...editing, notes: e.target.value })}
              />
            </label>

            <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300">
              Ưu tiên
              <select
                className="select text-xs mt-1 w-full"
                value={editing.priority ?? "MEDIUM"}
                onChange={(e) => setEditing({ ...editing, priority: e.target.value })}
              >
                {["LOW", "MEDIUM", "HIGH", "URGENT"].map((p) => (
                  <option key={p} value={p}>
                    {p}
                  </option>
                ))}
              </select>
            </label>

            <label className="flex items-center gap-2 text-xs text-slate-700 dark:text-slate-300 cursor-pointer">
              <input
                type="checkbox"
                className="checkbox"
                checked={!!editing.dueIsDateOnly}
                onChange={(e) =>
                  setEditing({
                    ...editing,
                    dueIsDateOnly: e.target.checked,
                    dueTimezone:
                      editing.dueTimezone || Intl.DateTimeFormat().resolvedOptions().timeZone,
                  })
                }
              />
              <span>Hạn chỉ có ngày (không chỉ định giờ)</span>
            </label>

            {editing.dueIsDateOnly && (
              <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300">
                Ngày hết hạn
                <input
                  type="date"
                  className="input text-xs mt-1"
                  value={deadlineDate(editing)}
                  onChange={(e) => setEditing({ ...editing, dueDateInput: e.target.value })}
                />
              </label>
            )}

            <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300">
              Thời điểm bắt đầu (giờ địa phương)
              <input
                type="datetime-local"
                className="input text-xs mt-1"
                value={localDateTime(editing.startAt)}
                onChange={(e) =>
                  setEditing({
                    ...editing,
                    startAt: e.target.value ? new Date(e.target.value).toISOString() : null,
                  })
                }
              />
            </label>

            {!editing.dueIsDateOnly && (
              <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300">
                Hạn hoàn thành (giờ địa phương)
                <input
                  type="datetime-local"
                  className="input text-xs mt-1"
                  value={localDateTime(editing.dueAt)}
                  onChange={(e) =>
                    setEditing({
                      ...editing,
                      dueAt: e.target.value ? new Date(e.target.value).toISOString() : null,
                    })
                  }
                />
              </label>
            )}

            <div className="flex justify-end gap-2 pt-3 border-t border-slate-100 dark:border-slate-800">
              <button
                type="button"
                disabled={busy}
                onClick={() => setEditing(null)}
                className="btn btn-secondary text-xs"
              >
                Hủy
              </button>
              <button disabled={busy} className="btn btn-primary text-xs">
                Lưu
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Pagination Footer */}
      <div className="flex items-center justify-between pt-3 border-t border-slate-100 dark:border-slate-800 text-xs">
        <button
          className="btn btn-secondary text-xs"
          disabled={busy || page === 0}
          onClick={() => setPage(page - 1)}
        >
          ← Trang trước
        </button>
        <span className="text-slate-500 font-medium">Trang {page + 1}</span>
        <button
          className="btn btn-secondary text-xs"
          disabled={busy || last}
          onClick={() => setPage(page + 1)}
        >
          Trang sau →
        </button>
      </div>
    </section>
  );
}
