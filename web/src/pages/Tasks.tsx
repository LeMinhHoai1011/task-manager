import { useCallback, useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { apiFetch, unwrapPage, unwrapData, mutationOpts, newIdempotencyKey } from "../api/client";
import { t } from "../i18n";
import { useRealtimeEvent } from "../realtime/RealtimeContext";
import CreateProjectTask from "../components/CreateProjectTask";
import { Badge, Button, Card, EmptyState, ErrorAlert, Select, Spinner, TextInput, stateLabel } from "../components/ui";
import AppIcon from "../components/AppIcon";

// Filter: state/project/assignee/tag/blocked/depth/overdue + sort allowlist + cursor.
const SORT_ALLOW = ["created_at", "updated_at", "due_at", "priority"] as const;
type SortKey = (typeof SORT_ALLOW)[number];

const STATE_OPTIONS = ["TODO", "IN_PROGRESS", "PENDING_APPROVAL", "COMPLETED", "CANCELLED"] as const;

interface TaskRow {
  id: string;
  title: string;
  state: string;
  project_id?: string;
  blocked?: boolean;
  blocked_flag?: boolean;
  deadline?: string | null;
  due_at?: string | null;
  version?: number;
  priority?: string;
  description?: string;
}

export default function Tasks() {
  const [params] = useSearchParams();
  const [state, setState] = useState("");
  const [priority, setPriority] = useState("");
  const [project, setProject] = useState(() => params.get("project") ?? params.get("project_id") ?? "");
  const [assignee, setAssignee] = useState("");
  const [assigneeDraft, setAssigneeDraft] = useState("");
  const [projects, setProjects] = useState<{ id: string; name: string }[]>([]);
  const [projectCursor, setProjectCursor] = useState<string | null>(null);
  const [projectError, setProjectError] = useState("");
  const [projectLoading, setProjectLoading] = useState(false);

  async function loadProjects(cursor?: string | null) {
    setProjectLoading(true);
    setProjectError("");
    try {
      const page = unwrapPage<{ id: string; name: string }>(
        await apiFetch("/projects", { query: { limit: 20, cursor: cursor ?? undefined } })
      );
      setProjects((old) => (cursor ? [...old, ...page.items] : page.items));
      setProjectCursor(page.nextCursor);
    } catch {
      setProjectError("Không tải được bộ chọn dự án.");
    } finally {
      setProjectLoading(false);
    }
  }

  useEffect(() => {
    void loadProjects();
  }, []);

  const [tag, setTag] = useState("");
  const [blocked, setBlocked] = useState("");
  const [depth, setDepth] = useState("");
  const [overdue, setOverdue] = useState("");
  const [sort, setSort] = useState<SortKey>("created_at");
  const [order, setOrder] = useState<"asc" | "desc">("desc");
  const [cursor, setCursor] = useState<string | null>(null);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [items, setItems] = useState<TaskRow[]>([]);
  const [err, setErr] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [view, setView] = useState<"list" | "kanban" | "calendar">("list");
  const [moving, setMoving] = useState(false);
  const [draggedTaskId, setDraggedTaskId] = useState<string | null>(null);

  // Quick detail drawer
  const [quickDetailTask, setQuickDetailTask] = useState<TaskRow | null>(null);

  async function moveTask(id: string, target: string) {
    const task = items.find((row) => row.id === id);
    if (!task || moving || task.state === target) return;
    const action =
      task.state === "TODO" && target === "IN_PROGRESS"
        ? "start"
        : task.state === "IN_PROGRESS" && (target === "PENDING_APPROVAL" || target === "COMPLETED")
        ? "submit"
        : task.state === "PENDING_APPROVAL" && target === "COMPLETED"
        ? "approve"
        : null;

    if (!action) {
      setErr("Chuyển trạng thái này cần thao tác và lý do tại trang chi tiết.");
      return;
    }

    const previous = items;
    setMoving(true);
    setErr(null);
    setItems((rows) => rows.map((row) => (row.id === id ? { ...row, state: target } : row)));

    try {
      const response = await apiFetch(`/tasks/${id}/workflow/${action}`, {
        ...mutationOpts({
          ifMatch: String(task.version),
          idempotencyKey: newIdempotencyKey(),
        }),
        method: "POST",
        body: {},
      });
      const result = unwrapData<{ to_state: string; version: number }>(response);
      if (!result) throw new Error("Missing workflow result");
      setItems((rows) =>
        rows.map((row) =>
          row.id === id ? { ...row, state: result.to_state, version: result.version } : row
        )
      );
    } catch (e: any) {
      setItems(previous);
      setErr(e.uiMessage ?? "Không thể chuyển trạng thái. Dữ liệu đã được khôi phục.");
    } finally {
      setMoving(false);
      setDraggedTaskId(null);
    }
  }

  const load = useCallback(
    async (c: string | null) => {
      setErr(null);
      setLoading(true);
      try {
        const r = await apiFetch("/tasks", {
          query: {
            state: state || undefined,
            priority: priority || undefined,
            project_id: project || undefined,
            assignee_id: assignee || undefined,
            tag: tag || undefined,
            blocked: blocked || undefined,
            depth: depth || undefined,
            overdue: overdue || undefined,
            sort: (SORT_ALLOW as readonly string[]).includes(sort) ? sort : "created_at",
            order,
            cursor: c || undefined,
          },
        });
        const page = unwrapPage<TaskRow>(r);
        setItems((prev) => (c ? [...prev, ...page.items] : page.items));
        setNextCursor(page.nextCursor);
      } catch (e: any) {
        setErr(e?.uiMessage ?? "Error");
      } finally {
        setLoading(false);
      }
    },
    [state, priority, project, assignee, tag, blocked, depth, overdue, sort, order]
  );

  useRealtimeEvent(["task", "workflow"], () => {
    void load(null);
  });

  useEffect(() => {
    setCursor(null);
    void load(null);
  }, [load]);

  const grouped = items.reduce<Record<string, TaskRow[]>>((acc, it) => {
    (acc[it.state] ??= []).push(it);
    return acc;
  }, Object.fromEntries(STATE_OPTIONS.map((s) => [s, [] as TaskRow[]])));

  return (
    <div className="mx-auto max-w-7xl space-y-4">
      {/* Top Header & View Modes */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="page-title">{t("nav.tasks")}</h1>
          <p className="page-subtitle">
            Theo dõi, lọc và kéo thả công việc trên bảng Kanban 5 trạng thái nghiệp vụ.
          </p>
        </div>
        <div className="segmented" role="group" aria-label="Chế độ hiển thị">
          <button
            className={view === "list" ? "segmented-active" : ""}
            onClick={() => setView("list")}
          >
            Danh sách
          </button>
          <button
            className={view === "kanban" ? "segmented-active" : ""}
            onClick={() => setView("kanban")}
          >
            Kanban
          </button>
          <button
            className={view === "calendar" ? "segmented-active" : ""}
            onClick={() => setView("calendar")}
          >
            Lịch
          </button>
        </div>
      </div>

      {/* Filter Toolbar */}
      <div className="toolbar-sticky filters rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-3.5 shadow-xs flex flex-wrap gap-2.5 items-center">
        <Select
          aria-label="Lọc ưu tiên"
          value={priority}
          onChange={(e) => setPriority(e.target.value)}
        >
          <option value="">Mọi độ ưu tiên</option>
          {["URGENT", "HIGH", "MEDIUM", "LOW"].map((p) => (
            <option key={p} value={p}>
              {
                (
                  {
                    URGENT: "Khẩn cấp",
                    HIGH: "Cao",
                    MEDIUM: "Trung bình",
                    LOW: "Thấp",
                  } as Record<string, string>
                )[p]
              }
            </option>
          ))}
        </Select>

        <Select
          aria-label={t("tasks.filter_state")}
          value={state}
          onChange={(e) => setState(e.target.value)}
        >
          <option value="">{`${t("tasks.filter_state")}: ${t("common.all")}`}</option>
          {STATE_OPTIONS.map((s) => (
            <option key={s} value={s}>
              {stateLabel(s)}
            </option>
          ))}
        </Select>

        <Select
          aria-label={t("tasks.filter_project")}
          value={project}
          disabled={projectLoading}
          onChange={(e) => setProject(e.target.value)}
        >
          <option value="">Tất cả dự án</option>
          {project && !projects.some((p) => p.id === project) && (
            <option value={project}>Dự án đang chọn</option>
          )}
          {projects.map((p) => (
            <option key={p.id} value={p.id}>
              {p.name}
            </option>
          ))}
        </Select>
        {projectCursor && (
          <Button
            variant="ghost"
            disabled={projectLoading}
            onClick={() => void loadProjects(projectCursor)}
          >
            Tải thêm dự án
          </Button>
        )}
        {projectError && (
          <button onClick={() => void loadProjects()} className="text-xs text-primary">
            {projectError} Thử lại
          </button>
        )}

        <form
          className="assignee-filter flex items-center gap-1.5"
          onSubmit={(e) => {
            e.preventDefault();
            setAssignee(assigneeDraft.trim());
          }}
        >
          <TextInput
            className="!w-44"
            placeholder="ID người phụ trách"
            title="Nhập UUID người phụ trách hoặc để trống để bỏ lọc"
            aria-label={t("tasks.filter_assignee")}
            pattern="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
            value={assigneeDraft}
            onChange={(e) => setAssigneeDraft(e.target.value)}
          />
          <Button variant="secondary" type="submit">
            Lọc người phụ trách
          </Button>
        </form>

        <TextInput
          className="!w-28"
          placeholder={t("tasks.filter_tag")}
          aria-label={t("tasks.filter_tag")}
          value={tag}
          onChange={(e) => setTag(e.target.value)}
        />

        <Select
          aria-label={t("tasks.filter_blocked")}
          value={blocked}
          onChange={(e) => setBlocked(e.target.value)}
        >
          <option value="">{`${t("tasks.filter_blocked")} ${t("common.all")}`}</option>
          <option value="true">{t("common.yes")}</option>
          <option value="false">{t("common.no")}</option>
        </Select>

        <Select
          aria-label={t("tasks.filter_depth")}
          value={depth}
          onChange={(e) => setDepth(e.target.value)}
        >
          <option value="">{`${t("tasks.filter_depth")}: ${t("common.all")}`}</option>
          <option value="0">{t("tasks.filter_depth_top")}</option>
          <option value="1">{t("tasks.filter_depth_sub")}</option>
        </Select>

        <Select
          aria-label={t("tasks.filter_overdue")}
          value={overdue}
          onChange={(e) => setOverdue(e.target.value)}
        >
          <option value="">{`${t("tasks.filter_overdue")} ${t("common.all")}`}</option>
          <option value="true">{t("common.yes")}</option>
          <option value="false">{t("common.no")}</option>
        </Select>

        <Select value={sort} onChange={(e) => setSort(e.target.value as SortKey)}>
          {SORT_ALLOW.map((s) => (
            <option key={s} value={s}>
              {
                (
                  {
                    created_at: "Ngày tạo",
                    updated_at: "Cập nhật gần đây",
                    due_at: "Hạn hoàn thành",
                    priority: "Độ ưu tiên",
                  } as Record<string, string>
                )[s]
              }
            </option>
          ))}
        </Select>

        <Select value={order} onChange={(e) => setOrder(e.target.value as "asc" | "desc")}>
          <option value="asc">Tăng dần</option>
          <option value="desc">Giảm dần</option>
        </Select>
      </div>

      <CreateProjectTask onCreated={() => void load(null)} />

      {err && items.length > 0 && <ErrorAlert message={err} />}

      {loading && items.length === 0 ? (
        <Spinner />
      ) : err && items.length === 0 ? (
        <Card className="text-center">
          <ErrorAlert message={err} />
          <div className="mt-3 flex justify-center">
            <Button variant="secondary" onClick={() => void load(null)}>
              {t("common.retry")}
            </Button>
          </div>
        </Card>
      ) : items.length === 0 ? (
        <EmptyState title={t("tasks.empty_title")} description={t("tasks.empty_desc")} />
      ) : view === "list" ? (
        <Card className="mt-4 overflow-x-auto !p-0 rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900">
          <table className="table min-w-[700px] w-full text-left">
            <thead>
              <tr className="border-b border-slate-200 dark:border-slate-800 text-xs font-semibold text-slate-500">
                <th className="p-3.5">Công việc</th>
                <th className="p-3.5">Trạng thái</th>
                <th className="p-3.5">Hạn hoàn thành</th>
                <th className="p-3.5">Trở ngại</th>
                <th className="p-3.5 text-right">Chi tiết</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-slate-800 text-sm">
              {items.map((it) => {
                const due = it.deadline ?? it.due_at;
                const blockedTask = it.blocked ?? it.blocked_flag ?? false;
                return (
                  <tr key={it.id} className="hover:bg-slate-50 dark:hover:bg-slate-850">
                    <td className="p-3.5">
                      <Link
                        to={`/tasks/${it.id}`}
                        className="font-medium text-slate-900 dark:text-white hover:text-primary"
                      >
                        {it.title}
                      </Link>
                    </td>
                    <td className="p-3.5">
                      <Badge state={it.state}>{stateLabel(it.state)}</Badge>
                    </td>
                    <td className="p-3.5 text-slate-600 dark:text-slate-400">
                      {due ? String(due).slice(0, 10) : "—"}
                    </td>
                    <td className="p-3.5">
                      {blockedTask ? (
                        <Badge state="BLOCKED">{t("task.blocked")}</Badge>
                      ) : (
                        "—"
                      )}
                    </td>
                    <td className="p-3.5 text-right">
                      <button
                        onClick={() => setQuickDetailTask(it)}
                        className="text-xs text-primary hover:underline font-semibold"
                      >
                        Xem nhanh
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </Card>
      ) : view === "calendar" ? (
        /* Calendar View Mode */
        <Card className="p-6 rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 text-center">
          <AppIcon name="calendar" size={36} className="text-primary mx-auto mb-2" />
          <h3 className="font-bold text-base text-slate-900 dark:text-white">
            Xem lịch trình công việc
          </h3>
          <p className="text-xs text-slate-500 mb-4">
            Đồng bộ với phân hệ Lịch theo ngày, tuần, tháng và múi giờ hệ thống.
          </p>
          <Link to="/calendar" className="btn btn-primary inline-flex text-xs">
            Mở trang Lịch toàn diện →
          </Link>
        </Card>
      ) : (
        /* Kanban Board View Mode */
        <div className="kanban-board" aria-label="Bảng Kanban">
          {Object.entries(grouped).map(([st, rows]) => (
            <Card
              key={st}
              className="kanban-column !p-3 rounded-2xl border border-slate-200 dark:border-slate-800 bg-slate-50/70 dark:bg-slate-900/70"
            >
              <div
                aria-label={`Thả vào ${st}`}
                onDragOver={(e) => {
                  e.preventDefault();
                  e.currentTarget.classList.add("bg-primary/5");
                }}
                onDragLeave={(e) => {
                  e.currentTarget.classList.remove("bg-primary/5");
                }}
                onDrop={(e) => {
                  e.preventDefault();
                  e.currentTarget.classList.remove("bg-primary/5");
                  const taskId = e.dataTransfer.getData("text/plain");
                  void moveTask(taskId, st);
                }}
                className="min-h-[450px] flex flex-col rounded-xl transition-colors"
              >
                <div className="mb-3 flex items-center justify-between pb-2 border-b border-slate-200 dark:border-slate-800">
                  <Badge state={st}>{stateLabel(st)}</Badge>
                  <span className="text-xs font-semibold text-slate-500">{rows.length}</span>
                </div>
                <ul className="flex flex-col gap-2 flex-1">
                  {rows.map((it) => {
                    const isBlocked = it.blocked ?? it.blocked_flag ?? false;
                    const due = it.deadline ?? it.due_at;
                    return (
                      <li
                        key={it.id}
                        draggable={!moving && it.version != null}
                        onDragStart={(e) => {
                          e.dataTransfer.setData("text/plain", it.id);
                          setDraggedTaskId(it.id);
                        }}
                        onDragEnd={() => setDraggedTaskId(null)}
                        className={`rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-850 p-3 shadow-xs hover:border-primary transition ${
                          draggedTaskId === it.id ? "opacity-40" : ""
                        }`}
                      >
                        <div className="flex items-start justify-between gap-2">
                          <Link
                            to={`/tasks/${it.id}`}
                            className="font-medium text-sm text-slate-900 dark:text-white hover:text-primary leading-snug"
                          >
                            {it.title}
                          </Link>
                          <button
                            onClick={() => setQuickDetailTask(it)}
                            title="Xem nhanh"
                            className="text-slate-400 hover:text-primary p-0.5"
                          >
                            <AppIcon name="overview" size={14} />
                          </button>
                        </div>

                        <div className="mt-2.5 flex flex-wrap items-center gap-1.5 text-xs text-slate-500">
                          <Badge state={it.state}>{stateLabel(it.state)}</Badge>
                          {it.priority && (
                            <span className={`badge-state text-[10px] ${it.priority === "URGENT" || it.priority === "HIGH" ? "badge-blocked" : "badge-todo"}`}>
                              {it.priority}
                            </span>
                          )}
                          {isBlocked && (
                            <Badge state="BLOCKED">{t("task.blocked")}</Badge>
                          )}
                          {due && (
                            <span className="text-[11px] text-slate-500 dark:text-slate-400">
                              📅 {String(due).slice(0, 10)}
                            </span>
                          )}
                        </div>

                        <div className="mt-3 pt-2 border-t border-slate-100 dark:border-slate-800 flex items-center justify-between text-xs">
                          <span className="text-[10px] text-slate-400">Chuyển cột:</span>
                          <select
                            aria-label={`Chuyển trạng thái ${it.title}`}
                            disabled={moving || it.version == null}
                            value={it.state}
                            onChange={(e) => void moveTask(it.id, e.target.value)}
                            className="text-xs bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded px-1.5 py-0.5"
                          >
                            {STATE_OPTIONS.map((s) => (
                              <option key={s} value={s}>
                                {stateLabel(s)}
                              </option>
                            ))}
                          </select>
                        </div>
                      </li>
                    );
                  })}
                </ul>
              </div>
            </Card>
          ))}
        </div>
      )}

      {nextCursor && (
        <div className="mt-4 flex justify-center">
          <Button
            variant="secondary"
            onClick={() => {
              setCursor(nextCursor);
              void load(nextCursor);
            }}
          >
            {t("common.more")}
          </Button>
        </div>
      )}
      {cursor && <p className="mt-2 hidden text-xs text-slate-400">{cursor}</p>}

      {/* Quick Detail Drawer / Modal */}
      {quickDetailTask && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-end bg-slate-900/40 backdrop-blur-xs"
          onClick={() => setQuickDetailTask(null)}
        >
          <div
            className="h-full w-full max-w-md bg-white dark:bg-slate-900 border-l border-slate-200 dark:border-slate-800 p-6 flex flex-col shadow-2xl animate-slide-left"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between border-b border-slate-200 dark:border-slate-800 pb-3">
              <span className="text-xs font-semibold uppercase text-slate-400">
                Chi tiết công việc nhanh
              </span>
              <button
                onClick={() => setQuickDetailTask(null)}
                className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 text-lg font-bold"
              >
                ×
              </button>
            </div>

            <div className="flex-1 overflow-y-auto py-4 space-y-4 text-sm">
              <div>
                <h3 className="text-xl font-bold text-slate-900 dark:text-white">
                  {quickDetailTask.title}
                </h3>
                <div className="mt-2 flex items-center gap-2">
                  <Badge state={quickDetailTask.state}>
                    {stateLabel(quickDetailTask.state)}
                  </Badge>
                  {quickDetailTask.priority && (
                    <Badge tone="badge-in-progress">
                      {quickDetailTask.priority}
                    </Badge>
                  )}
                </div>
              </div>

              {quickDetailTask.description && (
                <div>
                  <label className="text-xs font-bold text-slate-400">Mô tả</label>
                  <p className="mt-1 text-slate-700 dark:text-slate-300 whitespace-pre-wrap">
                    {quickDetailTask.description}
                  </p>
                </div>
              )}

              <div className="p-3 rounded-xl bg-slate-50 dark:bg-slate-850 border border-slate-100 dark:border-slate-800 text-xs space-y-1.5">
                <div>
                  Hạn chót:{" "}
                  <span className="font-semibold text-slate-800 dark:text-slate-200">
                    {quickDetailTask.due_at || quickDetailTask.deadline
                      ? String(quickDetailTask.due_at || quickDetailTask.deadline).slice(0, 10)
                      : "Chưa đặt"}
                  </span>
                </div>
                <div>Phiên bản: #{quickDetailTask.version ?? 1}</div>
              </div>
            </div>

            <div className="pt-4 border-t border-slate-200 dark:border-slate-800 flex items-center justify-between">
              <Link
                to={`/tasks/${quickDetailTask.id}`}
                className="btn btn-primary w-full text-center text-xs"
              >
                Mở toàn bộ trang chi tiết công việc →
              </Link>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
