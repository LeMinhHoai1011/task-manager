import { useCallback, useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { apiFetch, mutationOpts, newIdempotencyKey, unwrapPage } from "../api/client";
import { t } from "../i18n";
import { useRealtimeEvent } from "../realtime/RealtimeContext";
import { Button, Card, EmptyState, ErrorAlert, Spinner, TextInput } from "../components/ui";

interface Project {
  id: string;
  name: string;
  workspace_id: string;
  status?: string;
}

/**
 * Nested GET /workspaces/{id}/projects da co (F4) nhung DTO rut gon; trang nay
 * dung query /projects?workspace_id={id} (day du truong) lam primary.
 */
export default function Projects() {
  const [params] = useSearchParams();
  const [items, setItems] = useState<Project[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [q, setQ] = useState("");
  const [wsFilter, setWsFilter] = useState(() => params.get("workspace") ?? "");
  const [createOpen, setCreateOpen] = useState(false);
  const [newName, setNewName] = useState("");
  const [newWs, setNewWs] = useState("");
  const [formErr, setFormErr] = useState<string | null>(null);

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
    <div className="mx-auto max-w-5xl">
      <div className="flex items-center justify-between gap-3">
        <h1 className="page-title">{t("nav.projects")}</h1>
        <Button variant="primary" size="sm" onClick={() => { setCreateOpen(true); setFormErr(null); }}>
          {t("projects.new")}
        </Button>
      </div>
      <div className="mt-3 flex gap-2">
        <TextInput aria-label="Search projects" placeholder="Search projects…" value={q} onChange={(e) => setQ(e.target.value)} />
        <TextInput
          aria-label={t("projects.workspace_filter")}
          placeholder={t("projects.workspace_filter")}
          value={wsFilter}
          onChange={(e) => setWsFilter(e.target.value)}
          onBlur={() => void load(null, wsFilter.trim() || undefined)}
        />
      </div>
      {err && items.length > 0 && <ErrorAlert message={err} />}
      {loading ? (
        <div className="mt-4"><Spinner /></div>
      ) : filtered.length === 0 ? (
        <div className="mt-4"><EmptyState title="No projects" description="Create one to get started." /></div>
      ) : (
        <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {filtered.map((p) => (
            <Card key={p.id} className="card-hover">
              <p className="font-semibold text-slate-900">{p.name}</p>
              <p className="mt-1 truncate text-xs text-slate-500">workspace: {p.workspace_id}</p>
              <Link to={`/tasks?project=${p.id}`} className="mt-1 inline-block text-xs text-primary hover:underline">
                {t("nav.tasks")} →
              </Link>
            </Card>
          ))}
        </div>
      )}
      {nextCursor && (
        <div className="mt-4 flex justify-center">
          <Button variant="secondary" onClick={() => void load(nextCursor, wsFilter.trim() || undefined)}>
            {t("common.more")}
          </Button>
        </div>
      )}
      {createOpen && (
        <div role="dialog" aria-modal="true" aria-label={t("projects.create_title")} className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
          <div className="w-full max-w-sm rounded-xl bg-white p-6 shadow-lg">
            <h2 className="text-base font-bold text-slate-900">{t("projects.create_title")}</h2>
            <div className="mt-3 flex flex-col gap-2">
              <TextInput aria-label={t("projects.name")} placeholder={t("projects.name")} value={newName} onChange={(e) => setNewName(e.target.value)} />
              <TextInput aria-label={t("projects.workspace_id")} placeholder={t("projects.workspace_id")} value={newWs} onChange={(e) => setNewWs(e.target.value)} />
            </div>
            <ErrorAlert message={formErr} />
            <div className="mt-4 flex justify-end gap-2">
              <Button variant="primary" size="sm" onClick={() => void createProject()}>{t("common.create")}</Button>
              <Button variant="secondary" size="sm" onClick={() => setCreateOpen(false)}>{t("common.cancel")}</Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
