import { useEffect, useState } from "react";
import { apiFetch, mutationOpts, newIdempotencyKey, unwrapData, unwrapList } from "../api/client";
import { t } from "../i18n";
import { Button, Card, EmptyState, ErrorAlert, Select, TextInput } from "../components/ui";

// AI chỉ gợi ý, không bao giờ tự mutation: mọi thay đổi công việc đều qua nút
// "Xác nhận áp dụng" rõ ràng (POST /ai/jobs/{id}/accept) sau khi user xem bản nháp.
// Contract backend §AI: POST /ai/request {feature, projectId?, inputHint?, requestedFields?}.
const FEATURES = [
  "TASK_DRAFT",
  "SUBTASK_SUGGEST",
  "PRIORITY_SUGGEST",
  "DEADLINE_SUGGEST",
  "ASSIGNEE_SUGGEST",
  "DAILY_PLANNER",
  "WEEKLY_REVIEW",
] as const;

interface AiJob {
  id: string;
  feature: string;
  status: string;
  attempt?: number;
  maxAttempts?: number;
}

interface AiSuggestion {
  jobId: string;
  status: string;
  payload: string;
}

function parsePayload(payload: string): Array<{ k: string; v: string }> | null {
  try {
    const o = JSON.parse(payload);
    if (o && typeof o === "object" && !Array.isArray(o)) {
      return Object.entries(o as Record<string, unknown>).map(([k, v]) => ({
        k,
        v: typeof v === "string" ? v : JSON.stringify(v),
      }));
    }
    if (Array.isArray(o)) {
      return o.map((v, i) => ({ k: `#${i + 1}`, v: typeof v === "string" ? v : JSON.stringify(v) }));
    }
    return null;
  } catch {
    return null;
  }
}

export default function Ai() {
  const [feature, setFeature] = useState<string>("TASK_DRAFT");
  const [inputHint, setInputHint] = useState("");
  const [projectId, setProjectId] = useState("");
  const [job, setJob] = useState<AiJob | null>(null);
  const [suggestion, setSuggestion] = useState<AiSuggestion | null>(null);
  const [history, setHistory] = useState<AiJob[]>([]);
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [confirmAccept, setConfirmAccept] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  async function loadHistory() {
    try {
      const r = await apiFetch("/ai/history");
      const d = unwrapData<unknown>(r);
      setHistory(Array.isArray(d) ? (d as AiJob[]) : unwrapList<AiJob>(r));
    } catch {
      setHistory([]);
    }
  }

  useEffect(() => {
    void loadHistory();
  }, []);

  async function refreshJob(id: string) {
    try {
      const r = await apiFetch(`/ai/jobs/${id}`);
      const d = unwrapData<AiJob>(r);
      if (d) setJob(d);
    } catch (e: any) {
      setErr(e?.uiMessage ?? t("error.generic"));
    }
    try {
      const s = await apiFetch(`/ai/jobs/${id}/suggestion`);
      const sd = unwrapData<AiSuggestion>(s);
      if (sd) setSuggestion(sd);
    } catch {
      /* gợi ý chưa sẵn sàng — giữ trạng thái job, không báo lỗi chặn */
    }
  }

  // Auto-poll trạng thái job đang chạy (tối đa ~30s), dừng khi có gợi ý.
  useEffect(() => {
    if (!job || suggestion) return;
    const terminal = ["SUCCEEDED", "COMPLETED", "DONE", "FAILED", "REJECTED", "ACCEPTED", "APPLIED"];
    if (terminal.includes(String(job.status).toUpperCase())) return;
    const timer = window.setInterval(() => void refreshJob(job.id), 3000);
    return () => window.clearInterval(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [job, suggestion]);

  async function request() {
    setErr(null);
    setNotice(null);
    setSuggestion(null);
    if (!feature) {
      setErr(t("form.required"));
      return;
    }
    setBusy(true);
    try {
      const r = await apiFetch("/ai/request", {
        ...mutationOpts({ idempotencyKey: newIdempotencyKey() }),
        method: "POST",
        body: {
          feature,
          projectId: projectId.trim() || undefined,
          inputHint: inputHint.trim() || undefined,
          idempotencyKey: newIdempotencyKey(),
        },
      });
      const d = unwrapData<AiJob>(r);
      if (!d || !d.id) throw new Error("empty job");
      setJob(d);
      void refreshJob(d.id);
      void loadHistory();
    } catch (e: any) {
      setErr(e?.uiMessage ?? t("error.generic"));
    } finally {
      setBusy(false);
    }
  }

  async function accept() {
    if (!job) return;
    setErr(null);
    try {
      await apiFetch(`/ai/jobs/${job.id}/accept`, {
        ...mutationOpts({ idempotencyKey: newIdempotencyKey() }),
        method: "POST",
        body: {},
      });
      setConfirmAccept(false);
      setNotice(t("ai.accepted"));
      void refreshJob(job.id);
      void loadHistory();
    } catch (e: any) {
      setErr(e?.uiMessage ?? t("error.generic"));
      setConfirmAccept(false);
    }
  }

  async function reject() {
    if (!job) return;
    setErr(null);
    try {
      await apiFetch(`/ai/jobs/${job.id}/reject`, {
        ...mutationOpts({ idempotencyKey: newIdempotencyKey() }),
        method: "POST",
        body: {},
      });
      setNotice(t("admin.done"));
      void refreshJob(job.id);
      void loadHistory();
    } catch (e: any) {
      setErr(e?.uiMessage ?? t("error.generic"));
    }
  }

  async function retry() {
    if (!job) return;
    setErr(null);
    try {
      const r = await apiFetch(`/ai/jobs/${job.id}/retry`, {
        ...mutationOpts({ idempotencyKey: newIdempotencyKey() }),
        method: "POST",
        body: {},
      });
      const d = unwrapData<AiJob>(r);
      if (d) setJob(d);
      setSuggestion(null);
      void loadHistory();
    } catch (e: any) {
      setErr(e?.uiMessage ?? t("error.generic"));
    }
  }

  const parsed = suggestion ? parsePayload(suggestion.payload) : null;

  return (
    <div className="mx-auto max-w-4xl">
      <h1 className="page-title">{t("ai.title")}</h1>
      <p className="page-subtitle">{t("ai.desc")}</p>
      <ErrorAlert message={err} />
      {notice && <p role="status" className="mt-2 rounded-lg border border-green-200 bg-green-50 px-3 py-2 text-sm text-green-800">{notice}</p>}
      <Card className="mt-3">
        <div className="flex flex-wrap items-end gap-2">
          <label className="block">
            <span className="label">{t("ai.feature")}</span>
            <Select aria-label={t("ai.feature")} value={feature} onChange={(e) => setFeature(e.target.value)}>
              {FEATURES.map((f) => (
                <option key={f} value={f}>{f}</option>
              ))}
            </Select>
          </label>
          <label className="block">
            <span className="label">{t("ai.project_id")}</span>
            <TextInput className="!w-56" value={projectId} onChange={(e) => setProjectId(e.target.value)} />
          </label>
        </div>
        <label className="mt-2 block">
          <span className="label">{t("ai.input_hint")}</span>
          <textarea
            aria-label={t("ai.input_hint")}
            className="input min-h-20"
            value={inputHint}
            onChange={(e) => setInputHint(e.target.value)}
          />
        </label>
        <div className="row">
          <Button variant="primary" size="sm" disabled={busy} onClick={() => void request()}>
            {busy ? t("ai.loading") : t("ai.request")}
          </Button>
        </div>
      </Card>
      {job && (
        <Card className="mt-3">
          <h2 className="text-sm font-semibold text-slate-900">{t("ai.job_status")}</h2>
          <table className="table mt-2">
            <thead><tr><th>{t("common.id")}</th><th>{t("ai.feature")}</th><th>{t("common.status")}</th><th>{t("common.actions")}</th></tr></thead>
            <tbody>
              <tr>
                <td className="font-mono text-xs">{job.id}</td>
                <td>{job.feature}</td>
                <td>{job.status}</td>
                <td>
                  <div className="flex flex-wrap gap-2">
                    <Button variant="secondary" size="sm" onClick={() => void refreshJob(job.id)}>{t("common.refresh")}</Button>
                    <Button variant="secondary" size="sm" onClick={() => void retry()}>{t("ai.retry")}</Button>
                    <Button variant="danger" size="sm" onClick={() => void reject()}>{t("ai.reject")}</Button>
                    <Button variant="success" size="sm" onClick={() => setConfirmAccept(true)}>{t("ai.accept")}</Button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </Card>
      )}
      <Card className="mt-3">
        <h2 className="text-sm font-semibold text-slate-900">{t("ai.suggestion")}</h2>
        {!suggestion ? (
          <div className="mt-2"><EmptyState title={t("ai.no_suggestion")} /></div>
        ) : parsed ? (
          <table className="table mt-2">
            <tbody>
              {parsed.map((row) => (
                <tr key={row.k}>
                  <td className="w-40 font-medium text-slate-700">{row.k}</td>
                  <td className="whitespace-pre-wrap">{row.v}</td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <p className="mt-2 whitespace-pre-wrap text-sm text-slate-700">{suggestion.payload}</p>
        )}
      </Card>
      {confirmAccept && (
        <div role="dialog" aria-modal="true" aria-label={t("ai.accept")} className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
          <div className="w-full max-w-sm rounded-xl bg-white p-6 shadow-lg">
            <p className="text-sm text-slate-800">{t("ai.accept_confirm")}</p>
            <div className="mt-4 flex justify-end gap-2">
              <Button variant="success" size="sm" onClick={() => void accept()}>{t("common.confirm")}</Button>
              <Button variant="secondary" size="sm" onClick={() => setConfirmAccept(false)}>{t("common.cancel")}</Button>
            </div>
          </div>
        </div>
      )}
      <Card className="mt-3">
        <h2 className="text-sm font-semibold text-slate-900">{t("ai.history")}</h2>
        {history.length === 0 ? (
          <p className="mt-2 text-sm text-slate-400">—</p>
        ) : (
          <table className="table mt-2">
            <thead><tr><th>{t("common.id")}</th><th>{t("ai.feature")}</th><th>{t("common.status")}</th></tr></thead>
            <tbody>
              {history.map((h) => (
                <tr key={h.id}>
                  <td>
                    <button className="font-mono text-xs text-primary hover:underline" onClick={() => { setJob(h); setSuggestion(null); void refreshJob(h.id); }}>
                      {h.id}
                    </button>
                  </td>
                  <td>{h.feature}</td>
                  <td>{h.status}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>
    </div>
  );
}
