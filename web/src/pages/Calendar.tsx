import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { useRealtimeEvent } from "../realtime/RealtimeContext";
import ScopeFilters from "../components/ScopeFilters";
import { apiFetch, mutationOpts, newIdempotencyKey, unwrapData, unwrapList, unwrapPage } from "../api/client";
import { t } from "../i18n";
import { Badge, Button, Card, EmptyState, ErrorAlert, Select, Spinner, TextInput } from "../components/ui";

function deadlineTone(deadline: string | null | undefined): "badge-todo" | "badge-pending" | "badge-blocked" {
  if (!deadline) return "badge-todo";
  const d = new Date(deadline);
  const now = new Date();
  if (d < now) return "badge-blocked";
  const diff = d.getTime() - now.getTime();
  if (diff < 3 * 24 * 3600 * 1000) return "badge-pending";
  return "badge-todo";
}

// Backend GET /calendar trả { data: [{ task_id, project_id, due_at }] } —
// không có title/tz riêng; web render đúng field thật, không bịa.
interface CalItem {
  scope?: string;
  due_is_date_only?:boolean;
  task_id: string;
  project_id?: string;
  due_at?: string | null;
  title?: string;
  deadline?: string | null;
  tz?: string;
}
interface TaskOption { id: string; title: string; }

export default function Calendar() {
  const [mode, setMode] = useState<"day" | "week" | "month" | "unscheduled">("week");
  const [items, setItems] = useState<CalItem[]>([]);
  const [err, setErr] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [anchor,setAnchor]=useState(()=>{const d=new Date();return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,"0")}-${String(d.getDate()).padStart(2,"0")}`;});
  const [refresh,setRefresh]=useState(0);
  const [scope,setScope]=useState("all"),[projectId,setProjectId]=useState(""),[teamId,setTeamId]=useState("");
  const [page,setPage]=useState(0);
  const requestSequence=useRef(0);
  useRealtimeEvent(["task","workflow"],()=>setRefresh(n=>n+1));
  const tz = Intl.DateTimeFormat().resolvedOptions().timeZone;

  useEffect(() => {
    const request=++requestSequence.current;
    setLoading(true);
    setErr(null);
    apiFetch("/calendar", { query: { view: mode, date:anchor, timezone: tz,scope,projectId:projectId||undefined,teamId:teamId||undefined,page,size:200 } })
      .then((r) => {
        if(request!==requestSequence.current)return;
        const d = unwrapData<unknown>(r);
        setItems(Array.isArray(d) ? (d as CalItem[]) : unwrapList<CalItem>(r));
      })
      .catch((e: any) => {
        if(request!==requestSequence.current)return;
        setErr(e?.uiMessage ?? t("error.generic"));
        setItems([]);
      })
      .finally(() => {if(request===requestSequence.current)setLoading(false);});
    return()=>{requestSequence.current++;};
  }, [mode, tz, anchor, refresh,scope,projectId,teamId,page]);

  const dueOf = (i: CalItem): string | null =>
    i.due_at ?? i.deadline ?? null;
  const dated = items.filter((i) => dueOf(i));
  const unscheduled = items.filter((i) => !dueOf(i));

  const [remTask, setRemTask] = useState("");
  const [remAt, setRemAt] = useState("");
  const [remId, setRemId] = useState("");
  const [remMsg, setRemMsg] = useState<string | null>(null);
  const [tasks, setTasks] = useState<TaskOption[]>([]);
  const [projectTaskCursor,setProjectTaskCursor]=useState<string|null>(null),[personalTaskPage,setPersonalTaskPage]=useState(0),[personalTaskMore,setPersonalTaskMore]=useState(false),[optionsBusy,setOptionsBusy]=useState(false);
  const [reminders,setReminders]=useState<{id:string;task_id:string;remind_at:string;status:string}[]>([]);
  const [reminderPage,setReminderPage]=useState(0),[reminderMore,setReminderMore]=useState(false);
  async function loadReminders(p=0){const r=await apiFetch<{data:typeof reminders}>("/reminders",{query:{page:p,size:100}});setReminders(old=>p?[...old,...r.data]:r.data);setReminderPage(p);setReminderMore(r.data.length===100);}
  useEffect(()=>{void loadReminders().catch(()=>{});},[refresh]);
  async function moreTaskOptions(){setOptionsBusy(true);try{const rows:TaskOption[]=[];if(projectTaskCursor){const p=unwrapPage<TaskOption>(await apiFetch("/tasks",{query:{limit:100,cursor:projectTaskCursor}}));rows.push(...p.items);setProjectTaskCursor(p.nextCursor);}if(personalTaskMore){const r=await apiFetch<{data:{content:TaskOption[];last:boolean}}>("/me/tasks",{query:{size:100,page:personalTaskPage+1}});rows.push(...r.data.content);setPersonalTaskPage(n=>n+1);setPersonalTaskMore(!r.data.last);}setTasks(old=>Array.from(new Map([...old,...rows].map(task=>[task.id,task])).values()));}catch{setRemMsg("Không tải được thêm công việc");}finally{setOptionsBusy(false);}}

  useEffect(() => {
    Promise.all([apiFetch("/tasks", { query: { limit: 100, sort: "updated_at", order: "desc" } }),apiFetch<{data:{content:TaskOption[];last:boolean}}>("/me/tasks",{query:{size:100}})])
      .then(([project,personal]) => {const p=unwrapPage<TaskOption>(project);setTasks([...p.items,...personal.data.content]);setProjectTaskCursor(p.nextCursor);setPersonalTaskPage(0);setPersonalTaskMore(!personal.data.last);})
      .catch(() => setTasks([]));
  }, []);

  async function createReminder() {
    setRemMsg(null);
    // Validate: task_id bắt buộc; remind_at phải parse được ISO OffsetDateTime.
    if (!remTask.trim()) {
      setRemMsg(t("form.required"));
      return;
    }
    if (!remAt.trim() || Number.isNaN(Date.parse(remAt.trim()))) {
      setRemMsg(t("form.invalid_date"));
      return;
    }
    try {
      const r = await apiFetch("/reminders", {
        ...mutationOpts({ idempotencyKey: newIdempotencyKey() }),
        method: "POST",
        // datetime-local is entered in the user's local timezone; serialize an
        // explicit UTC instant for the OffsetDateTime backend contract.
        body: { task_id: remTask.trim(), remind_at: new Date(remAt).toISOString() },
      });
      const d = unwrapData<{ id: string }>(r);
      setRemId(d?.id ?? "");
      setRemMsg(t("calendar.reminder_created"));
      setRefresh(n=>n+1);
    } catch (e: any) {
      setRemMsg(e?.uiMessage ?? t("error.generic"));
    }
  }

  async function pauseResume(action: "pause" | "resume") {
    setRemMsg(null);
    if (!remId.trim()) {
      setRemMsg(t("form.required"));
      return;
    }
    try {
      const r = await apiFetch(`/reminders/${remId.trim()}/${action}`, {
        ...mutationOpts({ idempotencyKey: newIdempotencyKey() }),
        method: "POST",
        body: {},
      });
      setRefresh(n=>n+1);
      if (action === "resume") {
        const d = unwrapData<{ digests_sent?: number }>(r);
        setRemMsg(`${t("calendar.resumed")} (${d?.digests_sent ?? 0})`);
      } else {
        setRemMsg(t("calendar.paused"));
      }
    } catch (e: any) {
      setRemMsg(e?.uiMessage ?? t("error.generic"));
    }
  }

  const visible = mode === "unscheduled" ? unscheduled : mode === "month" ? items : dated;

  return (
    <div className="calendar-page mx-auto max-w-5xl space-y-4">
      <header><h1 className="page-title">Lịch công việc</h1><p className="page-subtitle">Sắp xếp thời hạn và nhắc việc theo múi giờ {tz}.</p></header>
      <ScopeFilters scope={scope} projectId={projectId} teamId={teamId} onChange={(s,p,t)=>{setScope(s);setProjectId(p);setTeamId(t);setPage(0);}}/>
      <label>Ngày xem <input className="input" aria-label="Ngày xem" type="date" value={anchor} onChange={e=>{setAnchor(e.target.value);setPage(0);}}/></label>
      <div className="mt-2 flex gap-1 rounded-xl bg-slate-100 dark:bg-slate-800 p-1 border border-slate-200 dark:border-slate-700">
        {(["day", "week", "month", "unscheduled"] as const).map((m) => (
          <button
            key={m}
            onClick={() => {setMode(m);setPage(0);}}
            disabled={mode === m}
            className={`flex-1 rounded-lg px-3 py-1.5 text-xs font-semibold transition ${mode === m ? "bg-white dark:bg-slate-900 text-blue-600 dark:text-blue-400 shadow-xs" : "text-slate-600 dark:text-slate-400 hover:text-slate-900"}`}
          >
            {{day:"Ngày",week:"Tuần",month:"Tháng",unscheduled:"Chưa lên lịch"}[m]}
          </button>
        ))}
      </div>
      <ErrorAlert message={err} />
      {loading ? (
        <div className="mt-3"><Spinner /></div>
      ) : (
        <div className="mt-3 grid gap-2">
          {visible.map((it) => {
            const due = dueOf(it);
            const tone = deadlineTone(due);
            const overdue = tone === "badge-blocked";
            const pending = tone === "badge-pending";
            return (
              <Card key={it.task_id} className={`!py-2.5 ${overdue ? "!border-red-200 !bg-red-50/50" : pending ? "!border-amber-200 !bg-amber-50/50" : ""}`}>
                <div className="flex flex-wrap items-center gap-2 text-sm">
                  <Link to={it.scope==="PERSONAL"?"/my-tasks":`/tasks/${it.task_id}`} className="font-medium text-slate-900 hover:text-primary">{it.title ?? it.task_id}</Link>
                  <Badge tone="badge-todo">{it.scope==="PERSONAL"?"Cá nhân":"Dự án / nhóm"}</Badge>
                  {due ? (
                    <span className={`badge-state ${tone}`}>
                      {new Date(due).toLocaleString(undefined,{timeZone:it.tz??tz})} ({it.tz??tz}){it.due_is_date_only?" · cả ngày":""}
                    </span>
                  ) : (
                    <Badge tone="badge-todo">{t("calendar.unscheduled")}</Badge>
                  )}
                </div>
              </Card>
            );
          })}
          {visible.length === 0 && !err && (
            <EmptyState title={t("calendar.empty")} />
          )}
        </div>
      )}
      <div className="mt-3 flex gap-3"><Button disabled={loading||page===0} onClick={()=>setPage(n=>n-1)}>Trang trước</Button><span>Trang {page+1} · tối đa 200 công việc</span><Button disabled={loading||items.length<200} onClick={()=>setPage(n=>n+1)}>Trang sau</Button></div>
      <Card className="mt-4">
        <h2 className="text-sm font-semibold text-slate-900">{t("calendar.reminders")}</h2>
        {(projectTaskCursor||personalTaskMore)&&<Button variant="secondary" disabled={optionsBusy} onClick={()=>void moreTaskOptions()}>Tải thêm công việc để đặt nhắc</Button>}
        {reminderMore&&<Button variant="secondary" onClick={()=>void loadReminders(reminderPage+1).catch(()=>setRemMsg("Không tải được nhắc việc"))}>Tải thêm nhắc việc</Button>}
        {remMsg && <p role="status" className="mt-1 text-sm text-slate-600">{remMsg}</p>}
        <div className="row">
          <Select className="min-w-56" aria-label="Công việc cần nhắc" value={remTask} onChange={(e) => setRemTask(e.target.value)}><option value="">Chọn công việc</option>{tasks.map((task) => <option key={task.id} value={task.id}>{task.title}</option>)}</Select>
          <TextInput className="!w-56" type="datetime-local" aria-label="Thời điểm nhắc" value={remAt} onChange={(e) => setRemAt(e.target.value)} />
          <Button variant="primary" size="sm" onClick={() => void createReminder()}>{t("common.create")}</Button>
        </div>
        <div className="row">
          <Select className="max-w-full" aria-label="Nhắc việc đã tạo" value={remId} onChange={e=>setRemId(e.target.value)}><option value="">Chọn nhắc việc đã tạo</option>{reminders.map(rule=><option key={rule.id} value={rule.id}>{tasks.find(task=>task.id===rule.task_id)?.title??"Công việc"} — {new Date(rule.remind_at).toLocaleString()} ({rule.status})</option>)}</Select>
          <Button disabled={!remId} variant="secondary" size="sm" onClick={() => void pauseResume("pause")}>{t("calendar.pause")}</Button>
          <Button disabled={!remId} variant="secondary" size="sm" onClick={() => void pauseResume("resume")}>{t("calendar.resume")}</Button>
        </div>
      </Card>
    </div>
  );
}
