import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { apiFetch, mutationOpts, newIdempotencyKey, unwrapPage } from "../api/client";
import { t } from "../i18n";
import { useRealtimeEvent } from "../realtime/RealtimeContext";
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

  if (err && items.length === 0 && !loading) return <ErrorAlert message={err} />;
  return (
    <div className="mx-auto max-w-5xl">
      <div className="flex items-center justify-between gap-3">
        <h1 className="page-title">{t("nav.workspaces")}</h1>
        <Button variant="primary" size="sm" onClick={() => { setCreateOpen(true); setFormErr(null); }}>
          {t("workspaces.new")}
        </Button>
      </div>
      <div className="mt-3 flex gap-2">
        <TextInput aria-label="Search workspaces" placeholder="Search workspaces…" value={q} onChange={(e) => setQ(e.target.value)} />
      </div>
      {err && items.length > 0 && <ErrorAlert message={err} />}
      {loading ? (
        <div className="mt-4"><Spinner /></div>
      ) : filtered.length === 0 ? (
        <div className="mt-4"><EmptyState title="No workspaces" description="Create one to get started." /></div>
      ) : (
        <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {filtered.map((w) => (
            <Card key={w.id} className="card-hover">
              <p className="font-semibold text-slate-900">{w.name}</p>
              <p className="mt-1 truncate text-xs text-slate-500">{w.id}</p>
              <Link to={`/projects?workspace=${w.id}`} className="mt-1 inline-block text-xs text-primary hover:underline">
                {t("nav.projects")} →
              </Link>
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
