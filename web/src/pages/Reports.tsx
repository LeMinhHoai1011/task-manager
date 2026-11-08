import { useCallback, useEffect, useState } from "react";
import {
  apiDownload,
  apiFetch,
  mutationOpts,
  newIdempotencyKey,
  saveBlobAsFile,
  unwrapData,
  unwrapPage,
} from "../api/client";
import { t } from "../i18n";
import { Button, Card, ErrorAlert, Select, Spinner } from "../components/ui";
import AppIcon from "../components/AppIcon";

type Dash = "personal" | "project";

function StatCard({ label, value, sub }: { label: string; value: string; sub?: string }) {
  const na = value === t("report.na") || value === "N/A";
  return (
    <Card className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-5 shadow-xs">
      <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">{label}</p>
      <p className={`mt-2 text-3xl font-extrabold ${na ? "text-slate-400" : "text-slate-900 dark:text-white"}`}>
        {value}
      </p>
      {sub && <p className="mt-1 text-xs text-slate-400">{sub}</p>}
    </Card>
  );
}

interface MetricValue {
  code: string;
  state: string;
  numeric?: number;
  intValue?: number;
  numerator?: number;
  denominator?: number;
}

interface DashboardData {
  scope: string;
  overall: string;
  metrics: Record<string, MetricValue>;
}

interface ExportJob {
  export_job_id: string;
  status: string;
}

interface ProjectOption {
  id: string;
  name: string;
}

export function rate(done: number, total: number): string {
  if (total === 0) return t("report.na");
  return `${Math.round((done / total) * 100)}%`;
}

export default function Reports() {
  const [projectId, setProjectId] = useState("");
  const [data, setData] = useState<DashboardData | null>(null);
  const [job, setJob] = useState<ExportJob | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const [projects, setProjects] = useState<ProjectOption[]>([]);
  const [timeRange, setTimeRange] = useState<"week" | "month" | "quarter">("month");

  useEffect(() => {
    apiFetch("/projects", { query: { limit: 100 } })
      .then((r) => setProjects(unwrapPage<ProjectOption>(r).items))
      .catch(() => setProjects([]));
  }, []);

  const load = useCallback(async (d: Dash) => {
    setErr(null);
    setLoading(true);
    try {
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
  }, [projectId]);

  useEffect(() => {
    void load(projectId ? "project" : "personal");
  }, [load]);

  async function exportReport(fmt: "PDF" | "XLSX" | "CSV") {
    setErr(null);
    try {
      const j = await apiFetch(`/reports/export-jobs`, {
        ...mutationOpts({ idempotencyKey: newIdempotencyKey() }),
        method: "POST",
        body: {
          report_code: projectId.trim() ? "PROJECT_PROGRESS" : "PERSONAL_SUMMARY",
          scope_type: projectId.trim() ? "PROJECT" : "PERSONAL",
          scope_id: projectId.trim() || undefined,
          format: fmt,
        },
      });
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
      setErr(e?.status === 409 ? t("reports.not_ready") : e?.uiMessage ?? t("error.generic"));
    } finally {
      setDownloading(false);
    }
  }

  // Client-side export to Markdown from real API data
  const exportMarkdown = () => {
    const title = projectId
      ? `Báo cáo dự án: ${projects.find((p) => p.id === projectId)?.name || projectId}`
      : "Báo cáo tổng hợp cá nhân";
    const date = new Date().toLocaleDateString("vi-VN");
    const m = data?.metrics ?? {};
    const total = m["TASK_UNIQUE_TOTAL"]?.intValue ?? 0;
    const completed = m["TASK_BY_STATE_COMPLETED"]?.intValue ?? 0;
    const overdue = m["TASK_OVERDUE_COUNT"]?.intValue ?? 0;
    const progress = m["PROGRESS_PERCENT"]?.numeric;
    const lines = [
      `# SMARTTASK REPORT - ${title}`,
      `Ngày xuất: ${date}`,
      `Phạm vi: ${data?.scope === "PROJECT" ? "Dự án" : "Cá nhân"}`,
      `Trạng thái tổng thể: ${data?.overall ?? "N/A"}`,
      "",
      "## 1. Số liệu tổng quan",
      `- Tổng số công việc: ${total}`,
      `- Hoàn thành: ${completed}`,
      `- Tỷ lệ hoàn thành: ${progress != null ? progress.toFixed(1) + "%" : t("report.na")}`,
      `- Công việc quá hạn: ${overdue}`,
      `- Công việc chờ duyệt: ${m["TASK_BY_STATE_PENDING_APPROVAL"]?.intValue ?? 0}`,
      `- Công việc bị chặn: ${m["TASK_BLOCKED_COUNT"]?.intValue ?? 0}`,
      "",
      "## 2. Công việc theo trạng thái",
      `- Cần làm: ${m["TASK_BY_STATE_TODO"]?.intValue ?? 0}`,
      `- Đang thực hiện: ${m["TASK_BY_STATE_IN_PROGRESS"]?.intValue ?? 0}`,
      `- Chờ duyệt: ${m["TASK_BY_STATE_PENDING_APPROVAL"]?.intValue ?? 0}`,
      `- Hoàn thành: ${completed}`,
      `- Đã hủy: ${m["TASK_BY_STATE_CANCELLED"]?.intValue ?? 0}`,
      "",
      "## 3. Phê duyệt",
      `- Chờ phê duyệt: ${m["APPROVAL_PENDING_COUNT"]?.intValue ?? 0}`,
      `- Đã duyệt: ${m["APPROVAL_APPROVED_COUNT"]?.intValue ?? 0}`,
      `- Đã từ chối: ${m["APPROVAL_REJECTED_COUNT"]?.intValue ?? 0}`,
      "",
      "---",
      `Báo cáo được trích xuất từ hệ thống SmartTask V3.0.`,
    ];
    const blob = new Blob([lines.join("\n")], { type: "text/markdown;charset=utf-8" });
    saveBlobAsFile(blob, `smarttask-report-${Date.now()}.md`);
  };

  const handlePrintPDF = () => {
    window.print();
  };

  // Helper to get metric value
  const getMetric = (code: string) => {
    const mv = data?.metrics?.[code];
    if (!mv) return 0;
    return mv.intValue ?? mv.numeric ?? 0;
  };

  const stats: Array<{ label: string; value: string; sub?: string }> = data
    ? [
        {
          label: t("common.total"),
          value: getMetric("TASK_UNIQUE_TOTAL") > 0 || data.metrics["TASK_UNIQUE_TOTAL"]?.intValue === 0
            ? String(getMetric("TASK_UNIQUE_TOTAL"))
            : t("report.na"),
          sub: "Tổng số công việc",
        },
        {
          label: t("common.done"),
          value: String(getMetric("TASK_BY_STATE_COMPLETED")),
          sub: "Đã hoàn thành",
        },
        {
          label: t("common.completion"),
          value: data.metrics["PROGRESS_PERCENT"]?.numeric != null
            ? `${data.metrics["PROGRESS_PERCENT"].numeric.toFixed(1)}%`
            : t("report.na"),
          sub: "Tỷ lệ đạt được",
        },
        {
          label: t("common.overdue"),
          value: String(getMetric("TASK_OVERDUE_COUNT")),
          sub: "Cần chú ý xử lý",
        },
      ]
    : [];

  // Burndown data from real metrics: completed vs total over time range
  // We approximate from current state: remaining = total - completed
  const burndownBars = data ? [
    { label: "Tổng", remaining: getMetric("TASK_UNIQUE_TOTAL"), ideal: getMetric("TASK_UNIQUE_TOTAL") },
    { label: "Hoàn thành", remaining: Math.max(0, getMetric("TASK_UNIQUE_TOTAL") - getMetric("TASK_BY_STATE_COMPLETED")), ideal: 0 },
    { label: "Còn lại", remaining: Math.max(0, getMetric("TASK_UNIQUE_TOTAL") - getMetric("TASK_BY_STATE_COMPLETED") - getMetric("TASK_BY_STATE_CANCELLED")), ideal: 0 },
  ] : [];

  // Weekly completion: by-state counts as pseudo-weekly bars
  const weeklyBars = data ? [
    { label: "Cần làm", count: getMetric("TASK_BY_STATE_TODO") },
    { label: "Đang làm", count: getMetric("TASK_BY_STATE_IN_PROGRESS") },
    { label: "Chờ duyệt", count: getMetric("TASK_BY_STATE_PENDING_APPROVAL") },
    { label: "Hoàn thành", count: getMetric("TASK_BY_STATE_COMPLETED") },
    { label: "Đã hủy", count: getMetric("TASK_BY_STATE_CANCELLED") },
  ] : [];

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      {/* Header and Print controls */}
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-200 dark:border-slate-800 pb-4">
        <div>
          <h1 className="page-title text-2xl font-bold tracking-tight text-slate-900 dark:text-white flex items-center gap-2">
            <AppIcon name="report" size={24} className="text-primary" />
            <span>{t("nav.reports")}</span>
          </h1>
          <p className="page-subtitle text-xs text-slate-500 mt-1">
            Theo dõi tiến độ, burndown chart, công việc quá hạn và trích xuất dữ liệu đa định dạng.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <Button variant="secondary" size="sm" onClick={exportMarkdown}>
            📄 Xuất Markdown
          </Button>
          <Button variant="secondary" size="sm" onClick={handlePrintPDF}>
            🖨️ In / Xuất PDF
          </Button>
        </div>
      </div>

      {/* Filter toolbar */}
      <div className="flex flex-wrap items-center justify-between gap-3 p-4 rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 shadow-xs">
        <div className="flex flex-wrap items-center gap-3">
          <Select
            aria-label="Dự án báo cáo"
            value={projectId}
            onChange={(e) => setProjectId(e.target.value)}
            className="!w-64 text-xs"
          >
            <option value="">Báo cáo cá nhân</option>
            {projects.map((project) => (
              <option key={project.id} value={project.id}>
                {project.name}
              </option>
            ))}
          </Select>

          <div className="flex items-center gap-1.5 text-xs">
            <button
              onClick={() => setTimeRange("week")}
              className={`px-3 py-1.5 rounded-lg font-medium transition ${
                timeRange === "week"
                  ? "bg-primary text-white"
                  : "bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300"
              }`}
            >
              Theo tuần
            </button>
            <button
              onClick={() => setTimeRange("month")}
              className={`px-3 py-1.5 rounded-lg font-medium transition ${
                timeRange === "month"
                  ? "bg-primary text-white"
                  : "bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300"
              }`}
            >
              Theo tháng
            </button>
            <button
              onClick={() => setTimeRange("quarter")}
              className={`px-3 py-1.5 rounded-lg font-medium transition ${
                timeRange === "quarter"
                  ? "bg-primary text-white"
                  : "bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300"
              }`}
            >
              Quý
            </button>
          </div>
        </div>

        <div className="row !my-0 flex items-center gap-2">
          <Button variant="secondary" size="sm" onClick={() => void load("personal")}>
            {t("reports.personal")}
          </Button>
          <Button variant="secondary" size="sm" onClick={() => void load("project")}>
            {t("reports.project")}
          </Button>
        </div>
      </div>

      <ErrorAlert message={err} />

      {/* KPI Stats Grid */}
      {loading ? (
        <div className="py-8 flex justify-center">
          <Spinner />
        </div>
      ) : data ? (
        <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          {stats.map((s) => (
            <StatCard key={s.label} label={s.label} value={s.value} sub={s.sub} />
          ))}
        </div>
      ) : (
        <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          {[t("common.total"), t("common.done"), t("common.completion"), t("common.overdue")].map(
            (l) => (
              <StatCard key={l} label={l} value={t("report.na")} />
            )
          )}
        </div>
      )}

      {/* Visual Analytics Sections: Burndown & Weekly Completion */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Burndown Chart Visualization */}
        <Card className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-6 shadow-xs">
          <div className="flex items-center justify-between mb-4 border-b border-slate-100 dark:border-slate-800 pb-2">
            <div>
              <h3 className="font-bold text-sm text-slate-900 dark:text-white">
                Biểu đồ Burndown (Khối lượng việc còn lại)
              </h3>
              <p className="text-xs text-slate-400">
                Dữ liệu từ báo cáo thực tế (công việc còn lại vs tổng số)
              </p>
            </div>
            <span className="text-xs font-semibold px-2 py-0.5 rounded bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300">
              {data?.overall === "OK" ? "Dữ liệu thật" : "Dữ liệu một phần"}
            </span>
          </div>

          <div className="h-44 flex items-end justify-between gap-3 pt-6 px-2">
            {burndownBars.map((item) => (
              <div key={item.label} className="flex-1 flex flex-col items-center gap-2">
                <div className="text-[11px] font-bold text-primary">{item.remaining}</div>
                <div className="w-full max-w-[28px] bg-slate-100 dark:bg-slate-800 rounded-t-lg h-32 relative overflow-hidden flex items-end">
                  <div
                    className="w-full bg-primary rounded-t-lg transition-all duration-500"
                    style={{ height: burndownBars[0]?.remaining > 0 ? `${(item.remaining / burndownBars[0].remaining) * 100}%` : "0%" }}
                  />
                </div>
                <span className="text-[10px] text-slate-400 truncate">{item.label}</span>
              </div>
            ))}
          </div>
        </Card>

        {/* Task by State */}
        <Card className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-6 shadow-xs">
          <div className="flex items-center justify-between mb-4 border-b border-slate-100 dark:border-slate-800 pb-2">
            <div>
              <h3 className="font-bold text-sm text-slate-900 dark:text-white">
                Công việc theo trạng thái
              </h3>
              <p className="text-xs text-slate-400">Số lượng task theo từng trạng thái quy trình</p>
            </div>
          </div>

          <div className="h-44 flex items-end justify-between gap-3 pt-6 px-2">
            {weeklyBars.map((bar) => {
              const maxCount = Math.max(...weeklyBars.map((b) => b.count), 1);
              const color = bar.label === "Hoàn thành" ? "bg-emerald-500" :
                bar.label === "Đang làm" ? "bg-blue-500" :
                bar.label === "Chờ duyệt" ? "bg-amber-500" :
                bar.label === "Đã hủy" ? "bg-slate-500" : "bg-indigo-500";
              return (
                <div key={bar.label} className="flex-1 flex flex-col items-center gap-2">
                  <div className="text-[11px] font-bold text-primary">{bar.count}</div>
                  <div className="w-full max-w-[28px] bg-slate-100 dark:bg-slate-800 rounded-t-lg h-32 relative overflow-hidden flex items-end">
                    <div
                      className={`w-full ${color} rounded-t-lg transition-all duration-500`}
                      style={{ height: `${(bar.count / maxCount) * 100}%` }}
                    />
                  </div>
                  <span className="text-[10px] text-slate-400 truncate">{bar.label}</span>
                </div>
              );
            })}
          </div>
        </Card>
      </div>

      {/* Backend Export Jobs Section */}
      <Card className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-6 shadow-xs space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h3 className="font-bold text-sm text-slate-900 dark:text-white">
              Xuất tệp báo cáo Durable (Hỗ trợ tải tệp 24h)
            </h3>
            <p className="text-xs text-slate-500">
              Tạo job xuất báo cáo trên server và tải file an toàn
            </p>
          </div>
          <div className="row !my-0 flex items-center gap-2">
            <Button variant="primary" size="sm" onClick={() => void exportReport("PDF")}>
              {t("reports.export_pdf")}
            </Button>
            <Button variant="primary" size="sm" onClick={() => void exportReport("XLSX")}>
              {t("reports.export_xlsx")}
            </Button>
            <Button variant="primary" size="sm" onClick={() => void exportReport("CSV")}>
              {t("reports.export_csv")}
            </Button>
          </div>
        </div>

        {job ? (
          <div className="rounded-xl border border-slate-200 dark:border-slate-800 overflow-hidden">
            <table className="table w-full text-left text-sm">
              <thead className="bg-slate-50 dark:bg-slate-850 text-xs font-semibold text-slate-500">
                <tr>
                  <th className="p-3">{t("reports.export_job")}</th>
                  <th className="p-3">{t("common.status")}</th>
                  <th className="p-3 text-right">{t("common.actions")}</th>
                </tr>
              </thead>
              <tbody>
                <tr className="border-t border-slate-100 dark:border-slate-800">
                  <td className="p-3 font-mono text-xs">{job.export_job_id}</td>
                  <td className="p-3">
                    <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-300">
                      {job.status}
                    </span>
                  </td>
                  <td className="p-3 text-right">
                    <Button
                      variant="secondary"
                      size="sm"
                      disabled={downloading}
                      onClick={() => void downloadExport(job.export_job_id)}
                    >
                      {downloading ? t("reports.downloading") : `${t("common.download")} (TTL 24h)`}
                    </Button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        ) : (
          <div className="p-6 text-center text-xs text-slate-400">
            Chưa có job xuất báo cáo nào đang chạy.
          </div>
        )}
      </Card>
    </div>
  );
}
