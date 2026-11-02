import { useCallback, useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { apiFetch, unwrapData } from "../api/client";
import { t } from "../i18n";
import { Card, EmptyState, ErrorAlert, Skeleton, StatusBadge } from "../components/ui";
import { useRealtimeEvent } from "../realtime/RealtimeContext";
import ScopeFilters from "../components/ScopeFilters";

type TaskSummary = { id: string; title: string; scope?:string; state?: string; priority?: string; due_at?: string | null; deadline?: string | null; blocked?: boolean; blocked_flag?: boolean };
type Overview = {total:number;todo:number;in_progress:number;pending_approval:number;completed:number;overdue:number;due_soon:number;due_today:number;completion_rate:number;focus:TaskSummary[];workload:{user_id:string;display_name:string;active:number;total:number}[]};

function dateOnly(task: TaskSummary) { return task.due_at ?? task.deadline ?? null; }

export default function Dashboard() {
  const [tasks, setTasks] = useState<TaskSummary[]>([]);
  const [summary,setSummary]=useState<Overview|null>(null);
  const [scope,setScope]=useState("all");
  const [projectId,setProjectId]=useState(""),[teamId,setTeamId]=useState(""),[from,setFrom]=useState(""),[to,setTo]=useState("");
  const requestSequence=useRef(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const load = useCallback(async () => {
    const request=++requestSequence.current;
    setError(null); setLoading(true);
    try {
      const response = await apiFetch("/dashboard", { query: { scope,projectId:projectId||undefined,teamId:teamId||undefined,from:from?new Date(from).toISOString():undefined,to:to?new Date(to).toISOString():undefined,timezone:Intl.DateTimeFormat().resolvedOptions().timeZone } });
      if(request!==requestSequence.current)return;
      const data=unwrapData<Overview>(response);if(!data)throw new Error("Missing overview");setSummary(data);setTasks(data.focus);
    } catch (e: any) { if(request===requestSequence.current)setError(e?.uiMessage ?? t("error.generic")); }
    finally { if(request===requestSequence.current)setLoading(false); }
  }, [scope,projectId,teamId,from,to]);
  useEffect(() => { void load(); }, [load]);
  useRealtimeEvent(["task", "workflow"], () => { void load(); });

  const metrics = summary ? [{label:"Tổng công việc",value:summary.total,detail:`${summary.todo} chưa bắt đầu`,kind:"total"},{label:"Đang thực hiện",value:summary.in_progress,detail:`${summary.pending_approval} chờ duyệt`,kind:"active"},{label:"Hoàn thành",value:summary.completed,detail:`${summary.completion_rate.toFixed(1)}% tổng công việc`,kind:"done"},{label:"Quá hạn",value:summary.overdue,detail:"Cần kiểm tra và sắp xếp lại",kind:"overdue"}] : [];
  const focus = tasks;

  return <div className="dashboard-page mx-auto max-w-7xl space-y-6">
    <section className="flex flex-wrap items-end justify-between gap-3">
      <div><h1 className="page-title">Nhìn rõ việc cần làm.</h1><p className="page-subtitle">Tiến độ, thời hạn và những công việc cần bạn chú ý.</p></div>
      <div className="flex flex-wrap gap-2"><Link className="btn btn-secondary" to="/calendar">Xem lịch</Link><Link className="btn btn-primary" to="/my-tasks">+ Thêm việc cá nhân</Link></div>
    </section>
    {error && <ErrorAlert message={error} onRetry={() => void load()} />}
    <details className="filter-panel"><summary>Lọc phạm vi công việc <span>{scope==="personal"?"Cá nhân":scope==="project"?"Dự án":"Tất cả"}</span></summary><div className="filter-panel-body"><ScopeFilters scope={scope} projectId={projectId} teamId={teamId} onChange={(s,p,t)=>{setScope(s);setProjectId(p);setTeamId(t);}}/>
    <div className="flex flex-wrap gap-3"><label>Hạn từ<input className="input" type="datetime-local" value={from} onChange={e=>setFrom(e.target.value)}/></label><label>Hạn đến (không bao gồm)<input className="input" type="datetime-local" value={to} onChange={e=>setTo(e.target.value)}/></label></div>
    </div></details>
    <section className="metric-grid" aria-label="Thống kê công việc" aria-busy={loading}>
      {loading ? Array.from({ length: 4 }).map((_, i) => <Card key={i}><Skeleton className="h-4 w-24" /><Skeleton className="mt-3 h-8 w-12" /></Card>) : metrics.map((m) => <Card key={m.label} className={`metric-card metric-${m.kind}`}><p className="metric-label">{m.label}</p><p className="metric-value">{m.value}</p><p className="metric-detail">{m.detail}</p></Card>)}
    </section>
    {summary&&!loading&&<div className="deadline-brief"><span><strong>{summary.due_today}</strong> đến hạn hôm nay</span><span><strong>{summary.due_soon}</strong> đến hạn trong 7 ngày</span><span><strong>{summary.pending_approval}</strong> đang chờ duyệt</span><Link to="/calendar">Sắp xếp lịch làm việc</Link></div>}
    <section className="grid grid-cols-1 gap-5 lg:grid-cols-[minmax(0,1fr)_320px]">
      <Card><div className="flex flex-wrap items-center justify-between gap-2"><div className="min-w-0"><h2 className="section-title">Việc cần chú ý</h2><p className="mt-1 text-sm text-slate-500">Sắp xếp theo tình trạng quá hạn, sau đó là lần cập nhật gần đây.</p></div><Link className="text-sm font-medium text-primary hover:underline" to="/tasks">Mở danh sách</Link></div>
        {loading ? <div className="mt-5 space-y-3">{Array.from({length: 4}).map((_, i) => <Skeleton key={i} className="h-16 w-full" />)}</div> : focus.length === 0 ? <div className="mt-5"><EmptyState title="Bạn chưa có công việc cần xử lý" description="Công việc được giao cho bạn sẽ xuất hiện tại đây." /></div> : <ul className="mt-4 divide-y divide-slate-100">{focus.map((task) => <li key={task.id} className="flex items-center gap-3 py-3"><div className="min-w-0 flex-1"><Link to={task.scope==="PERSONAL"?"/my-tasks":`/tasks/${task.id}`} className="block truncate font-medium text-slate-900 hover:text-primary">{task.title}</Link><p className="mt-1 text-xs text-slate-500">{dateOnly(task) ? `Hạn: ${new Date(dateOnly(task)!).toLocaleString("vi-VN")}` : "Chưa đặt hạn"}</p></div><StatusBadge state={task.blocked || task.blocked_flag ? "BLOCKED" : task.state}>{task.blocked || task.blocked_flag ? "Bị chặn" : undefined}</StatusBadge></li>)}</ul>}
      </Card>
      <Card><h2 className="section-title">Nhịp làm việc của nhóm</h2><p className="mt-1 text-sm text-slate-500">Công việc đang mở trên tổng số được giao.</p>{loading?<Skeleton className="mt-5 h-28 w-full"/>:<ul className="workload-list">{summary?.workload.map(row=><li key={row.user_id}><div className="flex justify-between gap-2"><span className="truncate font-medium">{row.display_name}</span><span className="shrink-0 text-xs text-slate-500">{row.active} / {row.total}</span></div><meter min={0} max={Math.max(row.total,1)} value={row.active} aria-label={`Công việc đang mở của ${row.display_name}`}/></li>)}</ul>}{!loading&&!summary?.workload.length&&<p className="my-5 text-sm text-slate-500">Chưa có công việc được giao trong phạm vi này.</p>}<p className="mt-4 border-t border-slate-100 pt-4 text-xs text-slate-500">Bao gồm công việc con. Một công việc có thể có nhiều người cộng tác.</p></Card>
    </section>
  </div>;
}
