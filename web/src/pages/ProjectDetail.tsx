import React, { useEffect, useState, useCallback } from "react";
import { useParams, useSearchParams, useNavigate } from "react-router-dom";
import { apiFetch } from "../api/client";
import { useToast } from "../components/ToastContext";
import AppIcon from "../components/AppIcon";
import { Badge } from "../components/ui";

interface ProgressLogItem {
  id: string;
  projectId: string;
  updateDate: string;
  progressPercent: number;
  summary: string;
  completedWork?: string;
  blockers?: string;
  nextPlan?: string;
  createdBy: string;
  createdAt: string;
  version: number;
}

export default function ProjectDetail() {
  const { id } = useParams<{ id: string }>();
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const { showToast } = useToast();

  const [activeTab, setActiveTab] = useState<
    "overview" | "tasks" | "notes" | "progress" | "members" | "reports" | "settings"
  >(() => (searchParams.get("tab") as any) || "overview");

  const [project, setProject] = useState<any>(null);
  const [tasks, setTasks] = useState<any[]>([]);
  const [notes, setNotes] = useState<any[]>([]);
  const [progressLogs, setProgressLogs] = useState<ProgressLogItem[]>([]);
  const [members, setMembers] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  // New Progress Log modal / form state
  const [logModalOpen, setLogModalOpen] = useState(false);
  const [logPercent, setLogPercent] = useState<number>(50);
  const [logSummary, setLogSummary] = useState("");
  const [logCompleted, setLogCompleted] = useState("");
  const [logBlockers, setLogBlockers] = useState("");
  const [logNextPlan, setLogNextPlan] = useState("");
  const [submittingLog, setSubmittingLog] = useState(false);

  // Settings tab form state
  const [approvalEnabled, setApprovalEnabled] = useState(false);
  const [allowMemberCreate, setAllowMemberCreate] = useState(true);
  const [savingSettings, setSavingSettings] = useState(false);

  // Switch tab and sync search params
  const handleTabChange = (tab: typeof activeTab) => {
    setActiveTab(tab);
    setSearchParams({ tab });
  };

  const loadData = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    try {
      // 1. Load project details
      try {
        const pRes = await apiFetch<any>(`/projects/${id}`);
        setProject(pRes?.data || pRes);
      } catch {
        // Fallback or read from projects list
        const pList = await apiFetch<any>(`/projects`);
        const found = (pList?.items || pList?.data?.items || []).find((p: any) => p.id === id);
        if (found) setProject(found);
      }

      // 2. Load Tasks
      try {
        const tRes = await apiFetch<any>(`/tasks?project_id=${id}&limit=100`);
        const taskItems = (tRes?.items || tRes?.data?.items || []) as any[];
        setTasks(taskItems);
      } catch {}

      // 3. Load Progress Logs
      try {
        const plRes = await apiFetch<any>(`/projects/${id}/progress-logs`);
        const logItems = (plRes?.data || plRes || []) as ProgressLogItem[];
        setProgressLogs(logItems);
      } catch {}

      // 4. Load Project Notes
      try {
        const nRes = await apiFetch<any>(`/notes?project_id=${id}`);
        const noteItems = (nRes?.data || nRes || []) as any[];
        setNotes(noteItems);
      } catch {}

      // 5. Load Members
      try {
        const mRes = await apiFetch<any>(`/projects/${id}/members`);
        const mItems = (mRes?.data || mRes || []) as any[];
        setMembers(mItems);
      } catch {}

      // 6. Load Settings
      try {
        const sRes = await apiFetch<any>(`/projects/${id}/settings`);
        const sData = sRes?.data || sRes;
        if (sData) {
          setApprovalEnabled(!!sData.approval_enabled);
          setAllowMemberCreate(sData.allow_member_create_task !== false);
        }
      } catch {}
    } catch (e: any) {
      showToast({ type: "error", message: "Không thể tải thông tin dự án" });
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // Compute stats
  const totalTasks = tasks.length;
  const completedTasks = tasks.filter((t) => t.state === "COMPLETED").length;
  const inProgressTasks = tasks.filter((t) => t.state === "IN_PROGRESS").length;
  const overdueTasks = tasks.filter(
    (t) => t.due_at && new Date(t.due_at) < new Date() && t.state !== "COMPLETED"
  ).length;

  // Computed automated task progress %
  const activeTasksCount = tasks.filter((t) => t.state !== "CANCELLED").length;
  const computedProgressPercent =
    activeTasksCount > 0 ? Math.round((completedTasks * 100) / activeTasksCount) : 0;

  // Latest manual logged progress %
  const latestLogged = progressLogs[0];
  const loggedProgressPercent = latestLogged ? latestLogged.progressPercent : null;

  const handleCreateProgressLog = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!logSummary.trim()) {
      showToast({ type: "warning", message: "Vui lòng nhập tóm tắt tiến độ" });
      return;
    }
    setSubmittingLog(true);
    try {
      const payload = {
        updateDate: new Date().toISOString().substring(0, 10),
        progressPercent: Number(logPercent),
        summary: logSummary.trim(),
        completedWork: logCompleted.trim() || undefined,
        blockers: logBlockers.trim() || undefined,
        nextPlan: logNextPlan.trim() || undefined,
      };
      const res = await apiFetch<any>(`/projects/${id}/progress-logs`, {
        method: "POST",
        body: JSON.stringify(payload),
      });
      const newLog = (res?.data || res) as ProgressLogItem;
      setProgressLogs((prev) => [newLog, ...prev]);
      setLogModalOpen(false);
      setLogSummary("");
      setLogCompleted("");
      setLogBlockers("");
      setLogNextPlan("");
      showToast({ type: "success", message: "Đã ghi nhật ký tiến độ dự án" });
    } catch (e: any) {
      showToast({ type: "error", message: "Không thể lưu tiến độ dự án" });
    } finally {
      setSubmittingLog(false);
    }
  };

  const handleSaveSettings = async () => {
    setSavingSettings(true);
    try {
      await apiFetch(`/projects/${id}/approval`, {
        method: "PATCH",
        body: JSON.stringify({ enabled: approvalEnabled }),
      });
      showToast({ type: "success", message: "Đã cập nhật cài đặt dự án" });
    } catch {
      showToast({ type: "error", message: "Lỗi cập nhật cài đặt dự án" });
    } finally {
      setSavingSettings(false);
    }
  };

  if (loading && !project) {
    return (
      <div className="p-12 text-center text-sm text-slate-400">
        Đang tải thông tin chi tiết dự án...
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Project Banner Header */}
      <div className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-6 shadow-xs">
        <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2 text-xs text-slate-500 mb-1">
              <span
                onClick={() => navigate("/projects")}
                className="hover:underline cursor-pointer"
              >
                Dự án
              </span>
              <span>/</span>
              <span className="text-slate-800 dark:text-slate-200 font-medium">
                {project?.name || "Chi tiết"}
              </span>
            </div>
            <h1 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-white flex items-center gap-3">
              <span>{project?.name || "Dự án"}</span>
              <Badge tone="badge-in-progress">{project?.status ?? "PLANNING"}</Badge>
            </h1>
            <p className="text-sm text-slate-500 dark:text-slate-400 mt-1 max-w-2xl">
              {project?.description || "Không có mô tả dự án."}
            </p>
          </div>

          <div className="flex items-center gap-3 shrink-0">
            <button
              onClick={() => setLogModalOpen(true)}
              className="btn btn-primary text-xs flex items-center gap-1.5"
            >
              <AppIcon name="report" size={16} />
              <span>Cập nhật tiến độ</span>
            </button>
          </div>
        </div>

        {/* Dual Progress Display: Computed vs Logged */}
        <div className="mt-6 pt-6 border-t border-slate-100 dark:border-slate-800 grid grid-cols-1 md:grid-cols-2 gap-4">
          {/* Computed Task Progress */}
          <div className="p-4 rounded-xl bg-slate-50 dark:bg-slate-850/60 border border-slate-100 dark:border-slate-800">
            <div className="flex items-center justify-between text-xs font-semibold mb-2">
              <span className="text-slate-600 dark:text-slate-300">
                Tiến độ Task tự động (Đã hoàn thành / Tổng số)
              </span>
              <span className="text-primary font-bold">{computedProgressPercent}%</span>
            </div>
            <div className="h-2.5 w-full rounded-full bg-slate-200 dark:bg-slate-700 overflow-hidden">
              <div
                className="h-full bg-primary rounded-full transition-all duration-500"
                style={{ width: `${computedProgressPercent}%` }}
              />
            </div>
            <div className="mt-2 text-[11px] text-slate-400">
              {completedTasks}/{activeTasksCount} công việc đã xong
            </div>
          </div>

          {/* User Logged Progress */}
          <div className="p-4 rounded-xl bg-slate-50 dark:bg-slate-850/60 border border-slate-100 dark:border-slate-800">
            <div className="flex items-center justify-between text-xs font-semibold mb-2">
              <span className="text-slate-600 dark:text-slate-300">
                Tiến độ Quản lý báo cáo (Nhật ký gần nhất)
              </span>
              <span className="text-amber-600 dark:text-amber-400 font-bold">
                {loggedProgressPercent !== null ? `${loggedProgressPercent}%` : "Chưa có"}
              </span>
            </div>
            <div className="h-2.5 w-full rounded-full bg-slate-200 dark:bg-slate-700 overflow-hidden">
              <div
                className="h-full bg-amber-500 rounded-full transition-all duration-500"
                style={{ width: `${loggedProgressPercent || 0}%` }}
              />
            </div>
            <div className="mt-2 text-[11px] text-slate-400">
              {latestLogged
                ? `Cập nhật ngày ${new Date(latestLogged.updateDate).toLocaleDateString("vi-VN")}: "${latestLogged.summary}"`
                : "Nhấn 'Cập nhật tiến độ' để ghi nhận mốc mới"}
            </div>
          </div>
        </div>
      </div>

      {/* 7 Tabs Navigation */}
      <div className="border-b border-slate-200 dark:border-slate-800 overflow-x-auto">
        <nav className="flex gap-2 min-w-max pb-px">
          {[
            { id: "overview", label: "1. Tổng quan", icon: "overview" },
            { id: "tasks", label: `2. Công việc (${totalTasks})`, icon: "tasks" },
            { id: "notes", label: `3. Ghi chú (${notes.length})`, icon: "files" },
            { id: "progress", label: `4. Tiến độ (${progressLogs.length})`, icon: "report" },
            { id: "members", label: `5. Thành viên (${members.length})`, icon: "team" },
            { id: "reports", label: "6. Báo cáo", icon: "report" },
            { id: "settings", label: "7. Cài đặt", icon: "settings" },
          ].map((tab) => (
            <button
              key={tab.id}
              onClick={() => handleTabChange(tab.id as any)}
              className={`flex items-center gap-2 px-4 py-3 text-sm font-semibold border-b-2 transition-colors ${
                activeTab === tab.id
                  ? "border-primary text-primary"
                  : "border-transparent text-slate-500 hover:text-slate-800 dark:hover:text-slate-300"
              }`}
            >
              <AppIcon name={tab.icon} size={16} />
              <span>{tab.label}</span>
            </button>
          ))}
        </nav>
      </div>

      {/* TAB 1: TỔNG QUAN */}
      {activeTab === "overview" && (
        <div className="space-y-6">
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <div className="p-4 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900">
              <div className="text-xs text-slate-500">Tổng công việc</div>
              <div className="text-2xl font-bold text-slate-900 dark:text-white mt-1">
                {totalTasks}
              </div>
            </div>
            <div className="p-4 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900">
              <div className="text-xs text-slate-500">Đang thực hiện</div>
              <div className="text-2xl font-bold text-blue-600 mt-1">{inProgressTasks}</div>
            </div>
            <div className="p-4 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900">
              <div className="text-xs text-slate-500">Hoàn thành</div>
              <div className="text-2xl font-bold text-emerald-600 mt-1">{completedTasks}</div>
            </div>
            <div className="p-4 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900">
              <div className="text-xs text-slate-500">Quá hạn</div>
              <div className="text-2xl font-bold text-rose-600 mt-1">{overdueTasks}</div>
            </div>
          </div>

          {/* Quick tasks highlight */}
          <div className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-5">
            <h3 className="font-bold text-base text-slate-900 dark:text-white mb-3">
              Công việc cần chú ý gần nhất
            </h3>
            {tasks.length === 0 ? (
              <p className="text-sm text-slate-400 py-4">Chưa có công việc nào trong dự án.</p>
            ) : (
              <div className="divide-y divide-slate-100 dark:divide-slate-800">
                {tasks.slice(0, 5).map((t) => (
                  <div
                    key={t.id}
                    onClick={() => navigate(`/tasks/${t.id}`)}
                    className="py-3 flex items-center justify-between cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-850 px-2 rounded-lg"
                  >
                    <div>
                      <div className="font-medium text-sm text-slate-900 dark:text-white">
                        {t.title}
                      </div>
                      <div className="text-xs text-slate-400 mt-0.5">
                        Ưu tiên: {t.priority} • Hạn chót:{" "}
                        {t.due_at ? new Date(t.due_at).toLocaleDateString("vi-VN") : "Chưa đặt"}
                      </div>
                    </div>
                    <Badge state={t.state}>{t.state}</Badge>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      )}

      {/* TAB 2: CÔNG VIỆC */}
      {activeTab === "tasks" && (
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <h3 className="font-bold text-base text-slate-900 dark:text-white">
              Danh sách công việc dự án
            </h3>
            <button
              onClick={() => navigate(`/tasks?project_id=${id}`)}
              className="btn btn-secondary text-xs"
            >
              Mở trong bảng Kanban lớn →
            </button>
          </div>

          <div className="divide-y divide-slate-200 dark:divide-slate-800 rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900">
            {tasks.map((t) => (
              <div
                key={t.id}
                onClick={() => navigate(`/tasks/${t.id}`)}
                className="p-4 flex items-center justify-between hover:bg-slate-50 dark:hover:bg-slate-850 cursor-pointer"
              >
                <div>
                  <div className="font-semibold text-sm text-slate-900 dark:text-white">
                    {t.title}
                  </div>
                  <div className="text-xs text-slate-400 mt-1">
                    Trạng thái: {t.state} • Ưu tiên: {t.priority}
                  </div>
                </div>
                <Badge state={t.state}>{t.state}</Badge>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* TAB 3: GHI CHÚ */}
      {activeTab === "notes" && (
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <h3 className="font-bold text-base text-slate-900 dark:text-white">
              Ghi chú thuộc dự án
            </h3>
            <button
              onClick={() => navigate(`/notes`)}
              className="btn btn-primary text-xs"
            >
              + Tạo ghi chú dự án
            </button>
          </div>
          {notes.length === 0 ? (
            <div className="p-8 text-center text-sm text-slate-400 border rounded-2xl bg-white dark:bg-slate-900 border-slate-200 dark:border-slate-800">
              Chưa có ghi chú nào thuộc dự án này.
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {notes.map((n) => (
                <div
                  key={n.id}
                  onClick={() => navigate(`/notes?id=${n.id}`)}
                  className="p-4 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 cursor-pointer hover:border-primary transition"
                >
                  <div className="font-bold text-sm text-slate-900 dark:text-white truncate">
                    {n.title}
                  </div>
                  <p className="text-xs text-slate-500 mt-1 line-clamp-3">
                    {n.content?.replace(/[#*`\-_]/g, "") || "Trống..."}
                  </p>
                  <div className="text-[11px] text-slate-400 mt-3">
                    Cập nhật: {new Date(n.updatedAt).toLocaleDateString("vi-VN")}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* TAB 4: TIẾN ĐỘ (PROGRESS LOGS) */}
      {activeTab === "progress" && (
        <div className="space-y-6">
          <div className="flex items-center justify-between">
            <div>
              <h3 className="font-bold text-base text-slate-900 dark:text-white">
                Nhật ký tiến độ dự án
              </h3>
              <p className="text-xs text-slate-500">
                Lịch sử các mốc báo cáo tiến độ, công việc đã hoàn tất, khó khăn và kế hoạch tiếp theo
              </p>
            </div>
            <button
              onClick={() => setLogModalOpen(true)}
              className="btn btn-primary text-xs flex items-center gap-1.5"
            >
              <AppIcon name="plus" size={14} />
              <span>Ghi nhận tiến độ mới</span>
            </button>
          </div>

          {progressLogs.length === 0 ? (
            <div className="p-8 text-center text-sm text-slate-400 border rounded-2xl bg-white dark:bg-slate-900 border-slate-200 dark:border-slate-800">
              Chưa có bản ghi nhật ký tiến độ nào. Hãy cập nhật mốc tiến độ đầu tiên!
            </div>
          ) : (
            <div className="relative pl-6 space-y-6 before:absolute before:left-2.5 before:top-3 before:bottom-3 before:w-0.5 before:bg-slate-200 dark:before:bg-slate-800">
              {progressLogs.map((log) => (
                <div
                  key={log.id}
                  className="relative p-5 rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 shadow-xs space-y-3"
                >
                  {/* Timeline dot */}
                  <div className="absolute -left-[1.8rem] top-6 h-3.5 w-3.5 rounded-full bg-primary ring-4 ring-white dark:ring-slate-900" />

                  <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 dark:border-slate-800 pb-2">
                    <div className="flex items-center gap-3">
                      <span className="font-bold text-base text-slate-900 dark:text-white">
                        {log.summary}
                      </span>
                      <span className="text-xs px-2.5 py-0.5 rounded-full bg-primary/10 text-primary font-bold">
                        {log.progressPercent}% hoàn thành
                      </span>
                    </div>
                    <span className="text-xs text-slate-400">
                      Ngày cập nhật: {new Date(log.updateDate).toLocaleDateString("vi-VN")}
                    </span>
                  </div>

                  <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-xs">
                    {log.completedWork && (
                      <div className="p-3 rounded-xl bg-emerald-50/50 dark:bg-emerald-950/20 border border-emerald-100 dark:border-emerald-900/40">
                        <div className="font-bold text-emerald-800 dark:text-emerald-300 mb-1">
                          ✓ Đã hoàn thành
                        </div>
                        <p className="text-slate-700 dark:text-slate-300 whitespace-pre-wrap">
                          {log.completedWork}
                        </p>
                      </div>
                    )}
                    {log.blockers && (
                      <div className="p-3 rounded-xl bg-rose-50/50 dark:bg-rose-950/20 border border-rose-100 dark:border-rose-900/40">
                        <div className="font-bold text-rose-800 dark:text-rose-300 mb-1">
                          ⚠ Trở ngại / Blockers
                        </div>
                        <p className="text-slate-700 dark:text-slate-300 whitespace-pre-wrap">
                          {log.blockers}
                        </p>
                      </div>
                    )}
                    {log.nextPlan && (
                      <div className="p-3 rounded-xl bg-blue-50/50 dark:bg-blue-950/20 border border-blue-100 dark:border-blue-900/40">
                        <div className="font-bold text-blue-800 dark:text-blue-300 mb-1">
                          → Kế hoạch tiếp theo
                        </div>
                        <p className="text-slate-700 dark:text-slate-300 whitespace-pre-wrap">
                          {log.nextPlan}
                        </p>
                      </div>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* TAB 5: THÀNH VIÊN */}
      {activeTab === "members" && (
        <div className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-5 space-y-4">
          <h3 className="font-bold text-base text-slate-900 dark:text-white">
            Thành viên dự án ({members.length})
          </h3>
          <div className="divide-y divide-slate-100 dark:divide-slate-800">
            {members.map((m) => (
              <div key={m.id || m.user_id} className="py-3 flex items-center justify-between text-sm">
                <div>
                  <div className="font-medium text-slate-900 dark:text-white">
                    {m.user_email || m.email || m.user_id}
                  </div>
                  <div className="text-xs text-slate-400">Tham gia dự án</div>
                </div>
                <Badge tone="badge-in-progress">{m.role || "MEMBER"}</Badge>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* TAB 6: BÁO CÁO DỰ ÁN */}
      {activeTab === "reports" && (
        <div className="space-y-4">
          <div className="p-6 rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900">
            <h3 className="font-bold text-base text-slate-900 dark:text-white mb-2">
              Báo cáo tiến độ & Burndown dự án
            </h3>
            <p className="text-xs text-slate-500 mb-4">
              Theo dõi sự thay đổi giữa tiến độ công việc thực tế và tiến độ quản lý ghi nhận
            </p>
            <div className="h-48 flex items-end gap-3 pt-6 border-b border-slate-200 dark:border-slate-800 pb-2">
              {progressLogs.slice(0, 7).reverse().map((log) => (
                <div key={log.id} className="flex-1 flex flex-col items-center gap-1">
                  <span className="text-[11px] font-bold text-primary">{log.progressPercent}%</span>
                  <div
                    className="w-full bg-primary/80 rounded-t-lg transition-all duration-500 min-h-[8px]"
                    style={{ height: `${log.progressPercent * 1.5}px` }}
                  />
                  <span className="text-[10px] text-slate-400 truncate w-full text-center">
                    {new Date(log.updateDate).toLocaleDateString("vi-VN", { month: "numeric", day: "numeric" })}
                  </span>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* TAB 7: CÀI ĐẶT */}
      {activeTab === "settings" && (
        <div className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-6 space-y-6 max-w-2xl">
          <h3 className="font-bold text-lg text-slate-900 dark:text-white">
            Cài đặt dự án
          </h3>

          <div className="space-y-4 text-sm">
            <label className="flex items-center justify-between p-3 rounded-xl border border-slate-200 dark:border-slate-800 cursor-pointer">
              <div>
                <div className="font-medium text-slate-900 dark:text-white">
                  Yêu cầu phê duyệt công việc (Workflow Approval)
                </div>
                <div className="text-xs text-slate-500">
                  Khi bật, công việc phải qua bước PENDING_APPROVAL và được người duyệt chấp thuận
                </div>
              </div>
              <input
                type="checkbox"
                checked={approvalEnabled}
                onChange={(e) => setApprovalEnabled(e.target.checked)}
                className="h-4 w-4 rounded text-primary"
              />
            </label>

            <label className="flex items-center justify-between p-3 rounded-xl border border-slate-200 dark:border-slate-800 cursor-pointer">
              <div>
                <div className="font-medium text-slate-900 dark:text-white">
                  Cho phép thành viên tạo công việc
                </div>
                <div className="text-xs text-slate-500">
                  Thành viên có quyền tạo công việc mới trong dự án mà không cần vai trò quản lý
                </div>
              </div>
              <input
                type="checkbox"
                checked={allowMemberCreate}
                onChange={(e) => setAllowMemberCreate(e.target.checked)}
                className="h-4 w-4 rounded text-primary"
              />
            </label>
          </div>

          <button
            onClick={handleSaveSettings}
            disabled={savingSettings}
            className="btn btn-primary text-xs"
          >
            {savingSettings ? "Đang lưu..." : "Lưu cài đặt dự án"}
          </button>
        </div>
      )}

      {/* Progress Log Modal */}
      {logModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-xs">
          <div
            className="w-full max-w-lg rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-6 shadow-2xl space-y-4 animate-scale-in"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between">
              <h3 className="font-bold text-lg text-slate-900 dark:text-white">
                Ghi nhận nhật ký tiến độ dự án
              </h3>
              <button
                onClick={() => setLogModalOpen(false)}
                className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 text-xl font-bold"
              >
                ×
              </button>
            </div>

            <form onSubmit={handleCreateProgressLog} className="space-y-4 text-xs">
              <div>
                <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">
                  Tiến độ ước tính hiện tại: <span className="text-primary font-bold">{logPercent}%</span>
                </label>
                <input
                  type="range"
                  min="0"
                  max="100"
                  value={logPercent}
                  onChange={(e) => setLogPercent(Number(e.target.value))}
                  className="w-full accent-primary"
                />
              </div>

              <div>
                <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">
                  Tóm tắt tiến độ *
                </label>
                <input
                  type="text"
                  required
                  placeholder="Ví dụ: Hoàn tất giai đoạn thiết kế và API contract"
                  value={logSummary}
                  onChange={(e) => setLogSummary(e.target.value)}
                  className="input text-xs"
                />
              </div>

              <div>
                <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">
                  Công việc đã hoàn thành
                </label>
                <textarea
                  rows={2}
                  placeholder="Danh sách đầu việc đã chốt..."
                  value={logCompleted}
                  onChange={(e) => setLogCompleted(e.target.value)}
                  className="input text-xs resize-none"
                />
              </div>

              <div>
                <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">
                  Khó khăn, vướng mắc (Blockers)
                </label>
                <textarea
                  rows={2}
                  placeholder="Vấn đề phụ thuộc, chờ duyệt..."
                  value={logBlockers}
                  onChange={(e) => setLogBlockers(e.target.value)}
                  className="input text-xs resize-none"
                />
              </div>

              <div>
                <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">
                  Kế hoạch giai đoạn tiếp theo
                </label>
                <textarea
                  rows={2}
                  placeholder="Các công việc ưu tiên tiếp..."
                  value={logNextPlan}
                  onChange={(e) => setLogNextPlan(e.target.value)}
                  className="input text-xs resize-none"
                />
              </div>

              <div className="flex items-center justify-end gap-3 pt-3 border-t border-slate-100 dark:border-slate-800">
                <button
                  type="button"
                  onClick={() => setLogModalOpen(false)}
                  className="btn btn-ghost text-xs"
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  disabled={submittingLog}
                  className="btn btn-primary text-xs"
                >
                  {submittingLog ? "Đang lưu..." : "Lưu nhật ký"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
