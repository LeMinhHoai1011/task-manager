import { useEffect, useState } from "react";
import { apiDownload, apiFetch, mutationOpts, newIdempotencyKey, saveBlobAsFile, unwrapData, unwrapPage } from "../api/client";
import { t } from "../i18n";
import { Button, Card, EmptyState, ErrorAlert, Select, Spinner } from "../components/ui";

type Dash = "personal" | "project";

// Phân biệt unique task vs assignment count; N/A khi mẫu số 0.
export function rate(done: number, total: number): string {
  if (total === 0) return t("report.na");
  return `${Math.round((done / total) * 100)}%`;
}

function StatCard({ label, value }: { label: string; value: string }) {
  const na = value === t("report.na") || value === "N/A";
  return (
    <Card className="text-center">
      <p className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</p>
      <p className={`mt-1 text-2xl font-bold ${na ? "text-slate-400" : "text-slate-900"}`}>{value}</p>
    </Card>
  );
}

interface DashboardData {
  total?: number;
  done?: number;
  overdue?: number;
  [k: string]: unknown;
}

interface ExportJob {
  export_job_id: string;
  status: string;
}
interface ProjectOption { id: string; name: string; }

// PH-6: dashboard personal/project + export durable (TTL 24h, chỉ requester tải).
// Download tải bytes thật qua blob/objectURL (GET /export-jobs/{id}/download trả
// binary, không phải JSON); 409 EXPORT_NOT_READY nghĩa là job chưa xong.
export default function Reports() {
  const [projectId, setProjectId] = useState("");
  const [data, setData] = useState<DashboardData | null>(null);
  const [job, setJob] = useState<ExportJob | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const [projects, setProjects] = useState<ProjectOption[]>([]);
  useEffect(() => { apiFetch("/projects", { query: { limit: 100 } }).then((r) => setProjects(unwrapPage<ProjectOption>(r).items)).catch(() => setProjects([])); }, []);

  async function load(d: Dash) {
    setErr(null);
    setLoading(true);
    try {
      // Backend nhận `projectId` (camelCase) cho dashboard project.
      const r = await apiFetch(`/reports/dashboard`, {
        query: d === "project" && projectId.trim() ? { projectId: projectId.trim() } : {},
      });
      setData(unwrapData<DashboardData>(r) ?? null);
    } catch (e: any) {
      setErr(e?.uiMessage ?? t("error.generic"));
      setData(null);
    } finally {
      setLoading(false);
    }
  }

  async function exportReport(fmt: "PDF" | "XLSX" | "CSV") {
    setErr(null);
    try {
      const j = await apiFetch(
        `/reports/export-jobs`,
        {
          ...mutationOpts({ idempotencyKey: newIdempotencyKey() }),
          method: "POST",
          body: {
            report_code: projectId.trim() ? "PROJECT_PROGRESS" : "PERSONAL_SUMMARY",
            scope_type: projectId.trim() ? "PROJECT" : "PERSONAL",
            scope_id: projectId.trim() || undefined,
            format: fmt,
          },
        }
      );
      const d = unwrapData<ExportJob>(j);
      if (d && d.export_job_id) setJob(d);
      else setErr(t("error.generic"));
    } catch (e: any) {
      setErr(e?.uiMessage ?? t("error.generic"));
    }
  }

  async function downloadExport(id: string) {
    setErr(null);
    setDownloading(true);
    try {
      const { blob, filename } = await apiDownload(`/export-jobs/${id}/download`);
      saveBlobAsFile(blob, filename);
    } catch (e: any) {
      // 409 EXPORT_NOT_READY: job chưa build xong — báo retry trung tính.
      setErr(e?.status === 409 ? t("reports.not_ready") : (e?.uiMessage ?? t("error.generic")));
    } finally {
      setDownloading(false);
    }
  }

  const stats: Array<{ label: string; value: string }> = data
    ? [
        { label: t("common.total"), value: data.total != null ? String(data.total) : t("report.na") },
        { label: t("common.done"), value: data.done != null ? String(data.done) : t("report.na") },
        { label: t("common.completion"), value: data.done != null && data.total != null ? rate(Number(data.done), Number(data.total)) : t("report.na") },
        { label: t("common.overdue"), value: data.overdue != null ? String(data.overdue) : t("report.na") },
      ]
    : [];

  return (
    <div className="mx-auto max-w-5xl">
      <h1 className="page-title">{t("nav.reports")}</h1>
      <div className="mt-3 flex max-w-md gap-2">
        <Select aria-label="Dự án báo cáo" value={projectId} onChange={(e) => setProjectId(e.target.value)}><option value="">Báo cáo cá nhân</option>{projects.map((project) => <option key={project.id} value={project.id}>{project.name}</option>)}</Select>
      </div>
      <div className="row">
        <Button variant="secondary" size="sm" onClick={() => void load("personal")}>{t("reports.personal")}</Button>
        <Button variant="secondary" size="sm" onClick={() => void load("project")}>{t("reports.project")}</Button>
      </div>
      <ErrorAlert message={err} />
      {loading ? (
        <div className="mt-3"><Spinner /></div>
      ) : data ? (
        <div className="mt-3 grid grid-cols-2 gap-3 lg:grid-cols-4">
          {stats.map((s) => (
            <StatCard key={s.label} label={s.label} value={s.value} />
          ))}
        </div>
      ) : (
        <div className="mt-3 grid grid-cols-2 gap-3 lg:grid-cols-4">
          {[t("common.total"), t("common.done"), t("common.completion"), t("common.overdue")].map((l) => (
            <StatCard key={l} label={l} value={t("report.na")} />
          ))}
        </div>
      )}
      <div className="row">
        <Button variant="primary" size="sm" onClick={() => void exportReport("PDF")}>{t("reports.export_pdf")}</Button>
        <Button variant="primary" size="sm" onClick={() => void exportReport("XLSX")}>{t("reports.export_xlsx")}</Button>
        <Button variant="primary" size="sm" onClick={() => void exportReport("CSV")}>{t("reports.export_csv")}</Button>
      </div>
      {job ? (
        <Card className="mt-3">
          <table className="table">
            <thead><tr><th>{t("reports.export_job")}</th><th>{t("common.status")}</th><th>{t("common.actions")}</th></tr></thead>
            <tbody>
              <tr>
                <td className="font-mono text-xs">{job.export_job_id}</td>
                <td>{job.status}</td>
                <td>
                  <Button variant="secondary" size="sm" disabled={downloading} onClick={() => void downloadExport(job.export_job_id)}>
                    {downloading ? t("reports.downloading") : `${t("common.download")} (TTL 24h)`}
                  </Button>
                </td>
              </tr>
            </tbody>
          </table>
        </Card>
      ) : (
        <div className="mt-3"><EmptyState title={t("common.empty")} /></div>
      )}
    </div>
  );
}
