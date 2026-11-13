import { useCallback, useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { apiFetch, mutationOpts, newIdempotencyKey, unwrapPage } from "../api/client";
import { t } from "../i18n";
import { useRealtimeEvent } from "../realtime/RealtimeContext";
import { Button, Card, EmptyState, ErrorAlert, Select, Spinner, TextInput } from "../components/ui";
import AppIcon from "../components/AppIcon";

interface Project {
  id: string;
  name: string;
  workspace_id: string;
  status?: string;
}
interface WorkspaceOption {
  id: string;
  name: string;
}

export default function Projects() {
  const [params] = useSearchParams();
  const [items, setItems] = useState<Project[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [q, setQ] = useState("");
  const [wsFilter, setWsFilter] = useState(() => params.get("workspace") ?? "");
  const [viewMode, setViewMode] = useState<"card" | "table">("card");
  const [createOpen, setCreateOpen] = useState(false);
  const [newName, setNewName] = useState("");
  const [newWs, setNewWs] = useState("");
  const [formErr, setFormErr] = useState<string | null>(null);
  const [workspaces, setWorkspaces] = useState<WorkspaceOption[]>([]);
  const [actionBusy, setActionBusy] = useState(false);

  async function projectAction(id: string, activate: boolean) {
    setActionBusy(true);
    setErr(null);
    try {
      const r = await apiFetch<{ data: { version: number; approval_enabled: boolean } }>(
        `/projects/${id}/settings`
      );
      await apiFetch(`/projects/${id}/${activate ? "activate" : "approval"}`, {
        method: activate ? "POST" : "PATCH",
        ifMatch: String(r.data.version),
        body: activate ? {} : { enabled: !r.data.approval_enabled, version: r.data.version },
      });
      await load(null, wsFilter || undefined);
    } catch (e: any) {
      setErr(e.uiMessage ?? "Không thay đổi được dự án");
    } finally {
      setActionBusy(false);
    }
  }

  const load = useCallback(async (cursor?: string | null, ws?: string) => {
    if (!cursor) setLoading(true);
    setErr(null);
    try {
      const r = await apiFetch("/projects", {
        query: { workspace_id: ws || undefined, cursor: cursor || undefined, limit: 20 },
      });
      const page = unwrapPage<Project>(r);
      setItems((prev) => (cursor ? [...prev, ...page.items] : page.items));
      setNextCursor(page.nextCursor);
    } catch (e: any) {
      setErr(e?.uiMessage ?? t("error.generic"));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load(null, wsFilter.trim() || undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [load]);

  useEffect(() => {
    apiFetch("/workspaces", { query: { limit: 100 } })
      .then((r) => setWorkspaces(unwrapPage<WorkspaceOption>(r).items))
      .catch(() => setWorkspaces([]));
  }, []);

  useRealtimeEvent(["project"], () => {
    void load(null, wsFilter.trim() || undefined);
  });

  async function createProject() {
    if (!newName.trim()) {
      setFormErr(t("form.required"));
      return;
    }
    if (!newWs.trim()) {
      setFormErr(t("form.required"));
      return;
    }
    setFormErr(null);
    try {
      await apiFetch("/projects", {
        ...mutationOpts({ idempotencyKey: newIdempotencyKey() }),
        method: "POST",
        body: { workspace_id: newWs.trim(), name: newName.trim() },
      });
      setCreateOpen(false);
      setNewName("");
      void load(null, wsFilter.trim() || undefined);
    } catch (e: any) {
      setFormErr(e?.uiMessage ?? t("error.generic"));
    }
  }

  const filtered = items.filter((p) => p.name.toLowerCase().includes(q.toLowerCase()));

  if (err && items.length === 0 && !loading) return <ErrorAlert message={err} />;

  return (
    <div className="mx-auto max-w-6xl space-y-5">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-200 dark:border-slate-800 pb-4">
        <div>
          <h1 className="page-title text-2xl font-bold tracking-tight text-slate-900 dark:text-white flex items-center gap-2">
            <AppIcon name="project" size={24} className="text-blue-600" />
            <span>{t("nav.projects")}</span>
          </h1>
          <p className="page-subtitle text-xs text-slate-500 dark:text-slate-400 mt-0.5">
            Quản lý tất cả dự án, phân công thành viên, theo dõi tiến độ và kiểm soát phê duyệt.
          </p>
        </div>

        <div className="flex items-center gap-2.5">
          {/* Card / Table Switcher */}
          <div className="segmented">
            <button
              type="button"
              onClick={() => setViewMode("card")}
              className={`flex items-center gap-1.5 ${viewMode === "card" ? "segmented-active" : ""}`}
              title="Dạng thẻ"
            >
              <AppIcon name="grid" size={15} />
              <span className="hidden sm:inline">Thẻ</span>
            </button>
            <button
              type="button"
              onClick={() => setViewMode("table")}
              className={`flex items-center gap-1.5 ${viewMode === "table" ? "segmented-active" : ""}`}
              title="Dạng bảng"
            >
              <AppIcon name="list" size={15} />
              <span className="hidden sm:inline">Bảng</span>
            </button>
          </div>

          <Button
            variant="primary"
            size="sm"
            onClick={() => {
              setCreateOpen(true);
              setFormErr(null);
            }}
            className="flex items-center gap-1.5"
          >
            <AppIcon name="plus" size={15} />
            <span>{t("projects.new")}</span>
          </Button>
        </div>
      </div>

      {/* Filters bar */}
      <div className="flex flex-wrap items-center gap-3 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-3.5 shadow-xs">
        <TextInput
          className="max-w-sm text-xs"
          aria-label="Tìm kiếm dự án"
          placeholder="Tìm kiếm dự án theo tên…"
          value={q}
          onChange={(e) => setQ(e.target.value)}
        />
        <Select
          aria-label="Lọc theo không gian làm việc"
          className="text-xs"
          value={wsFilter}
          onChange={(e) => {
            setWsFilter(e.target.value);
            void load(null, e.target.value || undefined);
          }}
        >
          <option value="">Tất cả không gian làm việc</option>
          {workspaces.map((w) => (
            <option key={w.id} value={w.id}>
              {w.name}
            </option>
          ))}
        </Select>
      </div>

      {err && items.length > 0 && <ErrorAlert message={err} />}

      {loading ? (
        <div className="mt-8 flex justify-center">
          <Spinner />
        </div>
      ) : filtered.length === 0 ? (
        <div className="mt-4">
          <EmptyState title="Không tìm thấy dự án" description="Tạo dự án mới để bắt đầu quản lý công việc." />
        </div>
      ) : viewMode === "card" ? (
        /* CARD VIEW */
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {filtered.map((p) => {
            const wsName = workspaces.find((w) => w.id === p.workspace_id)?.name ?? "Không gian làm việc";
            return (
              <Card
                key={p.id}
                className="card-hover rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-5 shadow-xs flex flex-col justify-between"
              >
                <div>
                  <div className="flex items-start justify-between gap-2">
                    <Link
                      to={`/projects/${p.id}`}
                      className="font-bold text-base text-slate-900 dark:text-white hover:text-blue-600 transition"
                    >
                      {p.name}
                    </Link>
                    {p.status && (
                      <span className="badge-state badge-todo text-[11px] font-semibold">
                        {p.status}
                      </span>
                    )}
                  </div>
                  <p className="mt-1 text-xs text-slate-400 flex items-center gap-1.5">
                    <AppIcon name="workspace" size={13} />
                    <span>{wsName}</span>
                  </p>
                </div>

                <div className="mt-4 pt-3 border-t border-slate-100 dark:border-slate-800 flex flex-wrap items-center justify-between gap-2">
                  <div className="flex flex-wrap gap-1.5">
                    {p.status === "PLANNING" && (
                      <Button
                        size="sm"
                        disabled={actionBusy}
                        onClick={() => void projectAction(p.id, true)}
                        className="!text-[11px] !h-7 !px-2"
                      >
                        Kích hoạt
                      </Button>
                    )}
                    <Button
                      size="sm"
                      variant="secondary"
                      disabled={actionBusy}
                      onClick={() => void projectAction(p.id, false)}
                      className="!text-[11px] !h-7 !px-2"
                    >
                      Bật/tắt duyệt
                    </Button>
                  </div>
                  <div className="flex items-center gap-3 text-xs">
                    <Link
                      to={`/projects/${p.id}`}
                      className="text-blue-600 dark:text-blue-400 font-semibold hover:underline"
                    >
                      Chi tiết →
                    </Link>
                    <Link
                      to={`/tasks?project=${p.id}`}
                      className="text-slate-500 hover:text-blue-600 font-medium hover:underline"
                    >
                      Việc làm
                    </Link>
                  </div>
                </div>
              </Card>
            );
          })}
        </div>
      ) : (
        /* TABLE VIEW */
        <Card className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 overflow-x-auto !p-0 shadow-xs">
          <table className="table min-w-[700px] w-full text-left">
            <thead>
              <tr className="border-b border-slate-200 dark:border-slate-800 text-xs font-semibold text-slate-500 bg-slate-50 dark:bg-slate-850">
                <th className="p-3.5">Tên dự án</th>
                <th className="p-3.5">Không gian làm việc</th>
                <th className="p-3.5">Trạng thái</th>
                <th className="p-3.5">Thao tác nhanh</th>
                <th className="p-3.5 text-right">Điều hướng</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-slate-800 text-xs">
              {filtered.map((p) => {
                const wsName = workspaces.find((w) => w.id === p.workspace_id)?.name ?? "Không gian làm việc";
                return (
                  <tr key={p.id} className="hover:bg-slate-50 dark:hover:bg-slate-800/50">
                    <td className="p-3.5 font-semibold text-slate-900 dark:text-white">
                      <Link to={`/projects/${p.id}`} className="hover:text-blue-600">
                        {p.name}
                      </Link>
                    </td>
                    <td className="p-3.5 text-slate-500">{wsName}</td>
                    <td className="p-3.5">
                      <span className="badge-state badge-todo">{p.status || "ACTIVE"}</span>
                    </td>
                    <td className="p-3.5">
                      <div className="flex gap-1.5">
                        {p.status === "PLANNING" && (
                          <Button
                            size="sm"
                            disabled={actionBusy}
                            onClick={() => void projectAction(p.id, true)}
                            className="!text-[10px] !h-6 !px-2"
                          >
                            Kích hoạt
                          </Button>
                        )}
                        <Button
                          size="sm"
                          variant="secondary"
                          disabled={actionBusy}
                          onClick={() => void projectAction(p.id, false)}
                          className="!text-[10px] !h-6 !px-2"
                        >
                          Bật/tắt duyệt
                        </Button>
                      </div>
                    </td>
                    <td className="p-3.5 text-right">
                      <div className="flex items-center justify-end gap-3 font-semibold">
                        <Link to={`/projects/${p.id}`} className="text-blue-600 hover:underline">
                          Chi tiết
                        </Link>
                        <Link to={`/tasks?project=${p.id}`} className="text-slate-400 hover:text-blue-600">
                          Công việc
                        </Link>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </Card>
      )}

      {nextCursor && (
        <div className="mt-4 flex justify-center">
          <Button variant="secondary" onClick={() => void load(nextCursor, wsFilter.trim() || undefined)}>
            {t("common.more")}
          </Button>
        </div>
      )}

      {/* Create Project Modal */}
      {createOpen && (
        <div
          role="dialog"
          aria-modal="true"
          aria-label={t("projects.create_title")}
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-xs p-4"
        >
          <div className="w-full max-w-sm rounded-2xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 p-6 shadow-xl">
            <h2 className="text-base font-bold text-slate-900 dark:text-white">{t("projects.create_title")}</h2>
            <div className="mt-3 flex flex-col gap-2.5">
              <TextInput
                aria-label={t("projects.name")}
                placeholder={t("projects.name")}
                value={newName}
                onChange={(e) => setNewName(e.target.value)}
                className="text-xs"
              />
              <Select
                aria-label="Không gian làm việc"
                value={newWs}
                onChange={(e) => setNewWs(e.target.value)}
                className="text-xs"
              >
                <option value="">Chọn không gian làm việc</option>
                {workspaces.map((w) => (
                  <option key={w.id} value={w.id}>
                    {w.name}
                  </option>
                ))}
              </Select>
            </div>
            <ErrorAlert message={formErr} />
            <div className="mt-4 flex justify-end gap-2">
              <Button variant="secondary" size="sm" onClick={() => setCreateOpen(false)}>
                {t("common.cancel")}
              </Button>
              <Button variant="primary" size="sm" onClick={() => void createProject()}>
                {t("common.create")}
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
