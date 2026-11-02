import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { apiFetch, mutationOpts, newIdempotencyKey, unwrapPage } from "../api/client";
import { t } from "../i18n";
import { useRealtimeEvent } from "../realtime/RealtimeContext";
import WorkspaceInvitations from "../components/WorkspaceInvitations";
import { Button, Card, EmptyState, ErrorAlert, Spinner, TextInput } from "../components/ui";

interface Workspace {
  id: string;
  name: string;
}

export default function Workspaces() {
  const [items, setItems] = useState<Workspace[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [q, setQ] = useState("");
  const [createOpen, setCreateOpen] = useState(false);
  const [newName, setNewName] = useState("");
  const [formErr, setFormErr] = useState<string | null>(null);

  const load = useCallback(async (cursor?: string | null) => {
    if (!cursor) setLoading(true);
    setErr(null);
    try {
      const r = await apiFetch("/workspaces", {
        query: { cursor: cursor || undefined, limit: 20 },
      });
      const page = unwrapPage<Workspace>(r);
      setItems((prev) => (cursor ? [...prev, ...page.items] : page.items));
      setNextCursor(page.nextCursor);
    } catch (e: any) {
      setErr(e?.uiMessage ?? t("error.generic"));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load(null);
  }, [load]);

  useRealtimeEvent(["workspace"], () => {
    void load(null);
  });

  async function createWorkspace() {
    if (!newName.trim()) {
      setFormErr(t("form.required"));
      return;
    }
    setFormErr(null);
    try {
      await apiFetch("/workspaces", {
        ...mutationOpts({ idempotencyKey: newIdempotencyKey() }),
        method: "POST",
        body: { name: newName.trim() },
      });
      setCreateOpen(false);
      setNewName("");
      void load(null);
    } catch (e: any) {
      setFormErr(e?.uiMessage ?? t("error.generic"));
    }
  }

  const filtered = items.filter((w) => w.name.toLowerCase().includes(q.toLowerCase()));

  if (err && items.length === 0 && !loading) return <ErrorAlert message={err} onRetry={() => void load(null)} />;
  return (
    <div className="mx-auto max-w-5xl">
      <div className="flex items-center justify-between gap-3"><div>
        <h1 className="page-title">{t("nav.workspaces")}</h1><p className="page-subtitle">Chọn không gian làm việc để xem dự án và công việc liên quan.</p></div>
        <Button variant="primary" size="sm" onClick={() => { setCreateOpen(true); setFormErr(null); }}>
          {t("workspaces.new")}
        </Button>
      </div>
      <WorkspaceInvitations workspaces={items} onAccepted={()=>void load(null)}/>
      <div className="mt-3 flex gap-2 rounded-xl border border-slate-200 bg-white p-3">
        <TextInput className="max-w-sm" aria-label="Tìm kiếm không gian làm việc" placeholder="Tìm kiếm không gian làm việc…" value={q} onChange={(e) => setQ(e.target.value)} />
      </div>
      {err && items.length > 0 && <ErrorAlert message={err} />}
      {loading ? (
        <div className="mt-4"><Spinner /></div>
      ) : filtered.length === 0 ? (
        <div className="mt-4"><EmptyState title="Chưa có không gian làm việc" description="Tạo không gian đầu tiên để tổ chức dự án và công việc." action={<Button variant="primary" onClick={() => setCreateOpen(true)}>Tạo không gian</Button>} /></div>
      ) : (
        <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {filtered.map((w) => (
            <Card key={w.id} className="card-hover">
              <div className="flex items-start justify-between gap-2"><p className="font-semibold text-slate-900">{w.name}</p><span className="badge-state badge-todo">Không gian</span></div>
              <p className="mt-2 text-sm text-slate-500">Dự án và công việc trong phạm vi bạn được cấp quyền.</p>
              <Link to={`/projects?workspace=${w.id}`} className="mt-4 inline-flex text-sm font-medium text-primary hover:underline">Mở dự án →</Link>
            </Card>
          ))}
        </div>
      )}
      {nextCursor && (
        <div className="mt-4 flex justify-center">
          <Button variant="secondary" onClick={() => void load(nextCursor)}>
            {t("common.more")}
          </Button>
        </div>
      )}
      {createOpen && (
        <div role="dialog" aria-modal="true" aria-label={t("workspaces.create_title")} className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
          <div className="w-full max-w-sm rounded-xl bg-white p-6 shadow-lg">
            <h2 className="text-base font-bold text-slate-900">{t("workspaces.create_title")}</h2>
            <div className="mt-3">
              <TextInput aria-label={t("workspaces.name")} placeholder={t("workspaces.name")} value={newName} onChange={(e) => setNewName(e.target.value)} />
            </div>
            <ErrorAlert message={formErr} />
            <div className="mt-4 flex justify-end gap-2">
              <Button variant="primary" size="sm" onClick={() => void createWorkspace()}>{t("common.create")}</Button>
              <Button variant="secondary" size="sm" onClick={() => setCreateOpen(false)}>{t("common.cancel")}</Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
