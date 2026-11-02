import { useCallback, useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { apiFetch, unwrapPage, unwrapData, mutationOpts, newIdempotencyKey } from "../api/client";
import { t } from "../i18n";
import { useRealtimeEvent } from "../realtime/RealtimeContext";
import CreateProjectTask from "../components/CreateProjectTask";
import { Badge, Button, Card, EmptyState, ErrorAlert, Select, Spinner, TextInput, stateLabel } from "../components/ui";

// Filter: state/project/assignee/tag/blocked/depth/overdue + sort allowlist + cursor.
const SORT_ALLOW = ["created_at", "updated_at", "due_at", "priority"] as const;
type SortKey = (typeof SORT_ALLOW)[number];

const STATE_OPTIONS = ["TODO", "IN_PROGRESS", "PENDING_APPROVAL", "COMPLETED", "CANCELLED"] as const;

interface TaskRow {
  id: string;
  title: string;
  state: string;
  project_id?: string;
  blocked?: boolean;
  blocked_flag?: boolean;
  deadline?: string | null;
  due_at?: string | null;
  version?: number;
}

export default function Tasks() {
  const [params] = useSearchParams();
  const [state, setState] = useState("");
  const [priority,setPriority]=useState("");
  const [project, setProject] = useState(() => params.get("project") ?? "");
  const [assignee, setAssignee] = useState("");
  const [assigneeDraft,setAssigneeDraft]=useState("");
  const [projects,setProjects]=useState<{id:string;name:string}[]>([]);
  const [projectCursor,setProjectCursor]=useState<string|null>(null);
  const [projectError,setProjectError]=useState("");
  const [projectLoading,setProjectLoading]=useState(false);
  async function loadProjects(cursor?:string|null){setProjectLoading(true);setProjectError("");try{const page=unwrapPage<{id:string;name:string}>(await apiFetch("/projects",{query:{limit:20,cursor:cursor??undefined}}));setProjects(old=>cursor?[...old,...page.items]:page.items);setProjectCursor(page.nextCursor);}catch{setProjectError("Không tải được bộ chọn dự án.");}finally{setProjectLoading(false);}}
  useEffect(()=>{void loadProjects();},[]);
  const [tag, setTag] = useState("");
  const [blocked, setBlocked] = useState("");
  const [depth, setDepth] = useState("");
  const [overdue, setOverdue] = useState("");
  const [sort, setSort] = useState<SortKey>("created_at");
  const [order, setOrder] = useState<"asc" | "desc">("desc");
  const [cursor, setCursor] = useState<string | null>(null);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [items, setItems] = useState<TaskRow[]>([]);
  const [err, setErr] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [view, setView] = useState<"list" | "kanban">("list");
  const [moving,setMoving]=useState(false);
  async function moveTask(id:string,target:string) {
    const task=items.find(row=>row.id===id);if(!task||moving||task.state===target)return;
    const action=task.state==="TODO"&&target==="IN_PROGRESS"?"start":task.state==="IN_PROGRESS"&&(target==="PENDING_APPROVAL"||target==="COMPLETED")?"submit":task.state==="PENDING_APPROVAL"&&target==="COMPLETED"?"approve":null;
    if(!action){setErr("Chuyển trạng thái này cần thao tác và lý do tại trang chi tiết.");return;}
    const previous=items;setMoving(true);setErr(null);
    setItems(rows=>rows.map(row=>row.id===id?{...row,state:target}:row));
    try {
      const response=await apiFetch(`/tasks/${id}/workflow/${action}`,{...mutationOpts({ifMatch:String(task.version),idempotencyKey:newIdempotencyKey()}),method:"POST",body:{}});
      const result=unwrapData<{to_state:string;version:number}>(response);
      if(!result)throw new Error("Missing workflow result");
      setItems(rows=>rows.map(row=>row.id===id?{...row,state:result.to_state,version:result.version}:row));
    }catch(e:any){setItems(previous);setErr(e.uiMessage??"Không thể chuyển trạng thái. Dữ liệu đã được khôi phục.");}
    finally{setMoving(false);}
  }

  const load = useCallback(
    async (c: string | null) => {
      setErr(null);
      setLoading(true);
      try {
        // Giữ nguyên sort+order hiện tại: backend hỗ trợ legacy sort+order
        // (§7 unified sort) nên không đổi contract.
        const r = await apiFetch("/tasks", {
          query: {
            state: state || undefined,
            priority:priority||undefined,
            project_id: project || undefined,
            assignee_id: assignee || undefined,
            tag: tag || undefined,
            blocked: blocked || undefined,
            depth: depth || undefined,
            overdue: overdue || undefined,
            // sort allowlist: chỉ gửi key hợp lệ.
            sort: (SORT_ALLOW as readonly string[]).includes(sort) ? sort : "created_at",
            order,
            cursor: c || undefined,
          },
        });
        // Envelope canonical: data.items (+fallback legacy data.data).
        const page = unwrapPage<TaskRow>(r);
        setItems((prev) => (c ? [...prev, ...page.items] : page.items));
        setNextCursor(page.nextCursor);
      } catch (e: any) {
        setErr(e?.uiMessage ?? "Error");
      } finally {
        setLoading(false);
      }
    },
    [state, priority, project, assignee, tag, blocked, depth, overdue, sort, order]
  );

  // Realtime: signal task/workflow -> refetch đúng scope qua API (không tin payload).
  useRealtimeEvent(["task", "workflow"], () => {
    void load(null);
  });

  useEffect(() => {
    setCursor(null);
    void load(null);
  }, [load]);

  const grouped = items.reduce<Record<string, TaskRow[]>>((acc, it) => {
    (acc[it.state] ??= []).push(it);
    return acc;
  }, Object.fromEntries(STATE_OPTIONS.map(s=>[s,[] as TaskRow[]])));

  return (
    <div className="mx-auto max-w-6xl">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3"><div><h1 className="page-title">{t("nav.tasks")}</h1><p className="page-subtitle">Theo dõi và sắp xếp công việc trong phạm vi bạn được cấp quyền.</p></div><div className="segmented" role="group" aria-label="Chế độ hiển thị"><button className={view === "list" ? "segmented-active" : ""} onClick={() => setView("list")}>Danh sách</button><button className={view === "kanban" ? "segmented-active" : ""} onClick={() => setView("kanban")}>Kanban</button></div></div>
      <div className="toolbar-sticky filters rounded-xl border border-slate-200 bg-white p-3 shadow-sm">
        <Select aria-label="Lọc ưu tiên" value={priority} onChange={e=>setPriority(e.target.value)}><option value="">Mọi độ ưu tiên</option>{["URGENT","HIGH","MEDIUM","LOW"].map(p=><option key={p} value={p}>{({URGENT:"Khẩn cấp",HIGH:"Cao",MEDIUM:"Trung bình",LOW:"Thấp"} as Record<string,string>)[p]}</option>)}</Select>
        <Select aria-label={t("tasks.filter_state")} value={state} onChange={(e) => setState(e.target.value)}>
          <option value="">{`${t("tasks.filter_state")}: ${t("common.all")}`}</option>
          {STATE_OPTIONS.map((s) => (
            <option key={s} value={s}>
              {stateLabel(s)}
            </option>
          ))}
        </Select>
        <Select aria-label={t("tasks.filter_project")} value={project} disabled={projectLoading} onChange={e=>setProject(e.target.value)}><option value="">Tất cả dự án</option>{project&&!projects.some(p=>p.id===project)&&<option value={project}>Dự án đang chọn</option>}{projects.map(p=><option key={p.id} value={p.id}>{p.name}</option>)}</Select>
        {projectCursor&&<Button variant="ghost" disabled={projectLoading} onClick={()=>void loadProjects(projectCursor)}>Tải thêm dự án</Button>}
        {projectError&&<button onClick={()=>void loadProjects()} className="text-xs text-primary">{projectError} Thử lại</button>}
        <form className="assignee-filter" onSubmit={e=>{e.preventDefault();setAssignee(assigneeDraft.trim());}}><TextInput className="!w-44" placeholder="ID người phụ trách" title="Nhập UUID người phụ trách hoặc để trống để bỏ lọc" aria-label={t("tasks.filter_assignee")} pattern="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}" value={assigneeDraft} onChange={e=>setAssigneeDraft(e.target.value)}/><Button variant="secondary" type="submit">Lọc người phụ trách</Button></form>
        <TextInput className="!w-28" placeholder={t("tasks.filter_tag")} aria-label={t("tasks.filter_tag")} value={tag} onChange={(e) => setTag(e.target.value)} />
        <Select aria-label={t("tasks.filter_blocked")} value={blocked} onChange={(e) => setBlocked(e.target.value)}>
          <option value="">{`${t("tasks.filter_blocked")} ${t("common.all")}`}</option>
          <option value="true">{t("common.yes")}</option>
          <option value="false">{t("common.no")}</option>
        </Select>
        <Select aria-label={t("tasks.filter_depth")} value={depth} onChange={(e) => setDepth(e.target.value)}>
          <option value="">{`${t("tasks.filter_depth")}: ${t("common.all")}`}</option>
          <option value="0">{t("tasks.filter_depth_top")}</option>
          <option value="1">{t("tasks.filter_depth_sub")}</option>
        </Select>
        <Select aria-label={t("tasks.filter_overdue")} value={overdue} onChange={(e) => setOverdue(e.target.value)}>
          <option value="">{`${t("tasks.filter_overdue")} ${t("common.all")}`}</option>
          <option value="true">{t("common.yes")}</option>
          <option value="false">{t("common.no")}</option>
        </Select>
        <Select value={sort} onChange={(e) => setSort(e.target.value as SortKey)}>
          {SORT_ALLOW.map((s) => (
            <option key={s} value={s}>
              {{created_at:"Ngày tạo",updated_at:"Cập nhật gần đây",due_at:"Hạn hoàn thành",priority:"Độ ưu tiên"}[s]}
            </option>
          ))}
        </Select>
        <Select value={order} onChange={(e) => setOrder(e.target.value as "asc" | "desc")}>
          <option value="asc">Tăng dần</option>
          <option value="desc">Giảm dần</option>
        </Select>
      </div>
      <CreateProjectTask onCreated={()=>void load(null)}/>
      {err && items.length > 0 && <ErrorAlert message={err} />}
      {loading && items.length === 0 ? (
        <Spinner />
      ) : err && items.length === 0 ? (
        <Card className="text-center">
          <ErrorAlert message={err} />
          <div className="mt-3 flex justify-center">
            <Button variant="secondary" onClick={() => void load(null)}>
              {t("common.retry")}
            </Button>
          </div>
        </Card>
      ) : items.length === 0 ? (
        <EmptyState title={t("tasks.empty_title")} description={t("tasks.empty_desc")} />
      ) : view === "list" ? (
        <Card className="mt-4 overflow-x-auto !p-0"><table className="table min-w-[700px]"><thead><tr><th>Công việc</th><th>Trạng thái</th><th>Hạn hoàn thành</th><th>Trở ngại</th></tr></thead><tbody>{items.map((it) => { const due = it.deadline ?? it.due_at; const blockedTask = it.blocked ?? it.blocked_flag ?? false; return <tr key={it.id}><td><Link to={`/tasks/${it.id}`} className="font-medium text-slate-900 hover:text-primary">{it.title}</Link></td><td><Badge state={it.state}>{stateLabel(it.state)}</Badge></td><td>{due ? String(due).slice(0, 10) : "—"}</td><td>{blockedTask ? <Badge state="BLOCKED">{t("task.blocked")}</Badge> : "—"}</td></tr>; })}</tbody></table></Card>
      ) : (
        <div className="kanban-board" aria-label="Bảng Kanban">
          {Object.entries(grouped).map(([st, rows]) => (
            <Card key={st} className="kanban-column !p-3"><div aria-label={`Thả vào ${st}`} onDragOver={e=>e.preventDefault()} onDrop={e=>{e.preventDefault();void moveTask(e.dataTransfer.getData("text/plain"),st);}}>
              <div className="mb-2 flex items-center justify-between">
                <Badge state={st}>{stateLabel(st)}</Badge>
                <span className="text-xs text-slate-500">{rows.length}</span>
              </div>
              <ul className="flex flex-col gap-1.5">
                {rows.map((it) => {
                  const isBlocked = it.blocked ?? it.blocked_flag ?? false;
                  const due = it.deadline ?? it.due_at;
                  return (
                  <li key={it.id} draggable={!moving&&it.version!=null} onDragStart={e=>e.dataTransfer.setData("text/plain",it.id)} className="rounded-lg border border-slate-100 bg-slate-50 px-2.5 py-2 hover:border-blue-200 hover:bg-blue-50/50">
                    <Link to={`/tasks/${it.id}`} className="font-medium text-slate-900 hover:text-primary">
                      {it.title}
                    </Link>
                    <div className="mt-1 flex items-center gap-1.5 text-xs text-slate-500">
                      <Badge state={it.state}>{stateLabel(it.state)}</Badge>
                      {isBlocked && <Badge state="BLOCKED">{t("task.blocked")}</Badge>}
                      {due && <span>{String(due).slice(0, 10)}</span>}
                    </div>
                    <select aria-label={`Chuyển trạng thái ${it.title}`} disabled={moving||it.version==null} value={it.state} onChange={e=>void moveTask(it.id,e.target.value)}>{STATE_OPTIONS.map(s=><option key={s} value={s}>{stateLabel(s)}</option>)}</select>
                  </li>
                  );
                })}
              </ul>
            </div></Card>
          ))}
        </div>
      )}
      {nextCursor && (
        <div className="mt-4 flex justify-center">
          <Button
            variant="secondary"
            onClick={() => {
              setCursor(nextCursor);
              void load(nextCursor);
            }}
          >
            {t("common.more")}
          </Button>
        </div>
      )}
      {cursor && <p className="mt-2 hidden text-xs text-slate-400">{cursor}</p>}
    </div>
  );
}
