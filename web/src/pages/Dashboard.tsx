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

  const metrics = summary ? [{label:"Tổng công việc",value:summary.total},{label:"Chưa bắt đầu",value:summary.todo},{label:"Đang thực hiện",value:summary.in_progress},{label:"Chờ duyệt",value:summary.pending_approval},{label:"Hoàn thành",value:summary.completed},{label:"Quá hạn",value:summary.overdue},{label:"Đến hạn trong 7 ngày",value:summary.due_soon},{label:"Đến hạn hôm nay",value:summary.due_today},{label:"Tỷ lệ hoàn thành",value:`${summary.completion_rate.toFixed(1)}%`}].map(m=>({...m,tone:"text-slate-900"})) : [];
  const focus = tasks;

  return <div className="mx-auto max-w-7xl space-y-6">
    <section className="flex flex-wrap items-end justify-between gap-3">
      <div><p className="text-sm font-medium text-primary">TỔNG QUAN</p><h1 className="page-title">Công việc của bạn</h1><p className="page-subtitle">Theo dõi tiến độ từ dữ liệu công việc bạn có quyền truy cập.</p></div>
      <Link className="btn btn-primary" to="/tasks">Xem tất cả công việc</Link>
    </section>
    {error && <ErrorAlert message={error} onRetry={() => void load()} />}
    <ScopeFilters scope={scope} projectId={projectId} teamId={teamId} onChange={(s,p,t)=>{setScope(s);setProjectId(p);setTeamId(t);}}/>
    <div className="flex flex-wrap gap-3"><label>Hạn từ<input className="input" type="datetime-local" value={from} onChange={e=>setFrom(e.target.value)}/></label><label>Hạn đến (không bao gồm)<input className="input" type="datetime-local" value={to} onChange={e=>setTo(e.target.value)}/></label></div>
    <section className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
      {loading ? Array.from({ length: 5 }).map((_, i) => <Card key={i}><Skeleton className="h-4 w-24" /><Skeleton className="mt-3 h-8 w-12" /></Card>) : metrics.map((m) => <Card key={m.label} className="!p-4"><p className="text-sm text-slate-500">{m.label}</p><p className={`mt-2 text-3xl font-bold tabular-nums ${m.tone}`}>{m.value}</p></Card>)}
    </section>
    <section className="grid grid-cols-1 gap-5 lg:grid-cols-[minmax(0,1fr)_320px]">
      <Card><div className="flex flex-wrap items-center justify-between gap-2"><div className="min-w-0"><h2 className="section-title">Việc cần chú ý</h2><p className="mt-1 text-sm text-slate-500">Sắp xếp theo tình trạng quá hạn, sau đó là lần cập nhật gần đây.</p></div><Link className="text-sm font-medium text-primary hover:underline" to="/tasks">Mở danh sách</Link></div>
        {loading ? <div className="mt-5 space-y-3">{Array.from({length: 4}).map((_, i) => <Skeleton key={i} className="h-16 w-full" />)}</div> : focus.length === 0 ? <div className="mt-5"><EmptyState title="Bạn chưa có công việc cần xử lý" description="Công việc được giao cho bạn sẽ xuất hiện tại đây." /></div> : <ul className="mt-4 divide-y divide-slate-100">{focus.map((task) => <li key={task.id} className="flex items-center gap-3 py-3"><div className="min-w-0 flex-1"><Link to={task.scope==="PERSONAL"?"/my-tasks":`/tasks/${task.id}`} className="block truncate font-medium text-slate-900 hover:text-primary">{task.title}</Link><p className="mt-1 text-xs text-slate-500">{dateOnly(task) ? `Hạn: ${new Date(dateOnly(task)!).toLocaleString()}` : "Chưa đặt hạn"}</p></div><StatusBadge state={task.blocked || task.blocked_flag ? "BLOCKED" : task.state}>{task.blocked || task.blocked_flag ? "Bị chặn" : task.state ?? "Chưa rõ"}</StatusBadge></li>)}</ul>}
      </Card>
      <Card><h2 className="section-title">Khối lượng theo thành viên</h2><p className="text-sm text-slate-500">Mỗi người cộng tác được tính một lượt; bao gồm task con.</p><ul>{!loading&&summary?.workload.map(row=><li key={row.user_id} className="flex justify-between gap-2 py-2"><span>{row.display_name}</span><span>{row.active} mở / {row.total}</span></li>)}</ul><Link className="btn btn-secondary" to="/calendar">Xem lịch</Link></Card>
    </section>
  </div>;
}
