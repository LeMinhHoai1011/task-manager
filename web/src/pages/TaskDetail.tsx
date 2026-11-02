import { useCallback, useEffect, useRef, useState } from "react";
import { useParams } from "react-router-dom";
import { apiFetch, mutationOpts, newIdempotencyKey, unwrapData, unwrapList } from "../api/client";
import WorkflowPanel from "./WorkflowPanel";
import TaskResponsibility from "./TaskResponsibility";
import ProjectTaskEditor from "../components/ProjectTaskEditor";
import { t } from "../i18n";
import { useRealtimeEvent } from "../realtime/RealtimeContext";
import { Badge, Button, Card, EmptyState, ErrorAlert, Spinner, TextInput } from "../components/ui";

interface Task {
  id: string;
  title: string;
  state?: string;
  priority?: string;
  assignee_id?: string | null;
  project_id?: string;
  version?: number | string;
  etag?: string;
  role?: string;
  description?: string | null;
  due_at?: string | null;
  deadline?: string | null;
}

interface Subtask {
  id: string;
  title: string;
  state: string;
}

interface ChecklistItem {
  id: string;
  title?: string;
  label?: string;
  done: boolean;
}

interface Timelog {
  id: string;
  work_date: string;
  minutes: number;
  note?: string | null;
  user_id?: string;
}

interface CommentItem {
  id: string;
  body: string;
  author_user_id?: string;
}

interface TimelineItem {
  id: string;
  kind: string;
  payload?: string | null;
  redacted?: boolean;
}

function fieldErr(cond: boolean, key: string): string | null {
  return cond ? null : t(key);
}

export default function TaskDetail() {
  const { id } = useParams();
  const [task, setTask] = useState<Task | null>(null);
  const [subtasks, setSubtasks] = useState<Subtask[]>([]);
  const [checklist, setChecklist] = useState<ChecklistItem[]>([]);
  const [timelogs, setTimelogs] = useState<Timelog[]>([]);
  const [comments, setComments] = useState<CommentItem[]>([]);
  const [timeline, setTimeline] = useState<TimelineItem[]>([]);
  const [err, setErr] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const loadSequence=useRef(0);
  const [tickBusy,setTickBusy]=useState(false);
  const [subtaskPage,setSubtaskPage]=useState(0),[moreSubtasks,setMoreSubtasks]=useState(false);

  // Dependency modal (thay prompt()).
  const [depOpen, setDepOpen] = useState(false);
  const [depId, setDepId] = useState("");
  const [depErr, setDepErr] = useState<string | null>(null);

  // Create forms.
  const [subTitle, setSubTitle] = useState("");
  const [clTitle, setClTitle] = useState("");
  const [tlDate, setTlDate] = useState("");
  const [tlMinutes, setTlMinutes] = useState("");
  const [tlNote, setTlNote] = useState("");
  const [commentBody, setCommentBody] = useState("");
  const [formErr, setFormErr] = useState<string | null>(null);
  const [formOk, setFormOk] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!id) return;
    const request=++loadSequence.current;
    setErr(null);
    setLoading(true);
    try {
      // GET /tasks/{id} trả envelope { data: TaskDto } — bóc canonical.
      const r = await apiFetch(`/tasks/${id}`);
      const d = unwrapData<Task>(r);
      if(request!==loadSequence.current)return;
      if (!d || typeof d !== "object") throw new Error("empty");
      setTask(d);
    } catch (e: any) {
      if(request===loadSequence.current)setErr(e?.uiMessage ?? t("task.not_found"));
    } finally {
      if(request===loadSequence.current)setLoading(false);
    }
    if(request!==loadSequence.current)return;
    apiFetch(`/tasks/${id}/subtasks`,{query:{page:0,size:20}}).then(r=>{if(request===loadSequence.current){const rows=unwrapList<Subtask>(r);setSubtasks(rows);setMoreSubtasks(rows.length===20);setSubtaskPage(0);}}).catch(()=>{});
    // Checklist/timelogs trả { data: [...] } thuần; comments/activity trả PageResponse.
    apiFetch(`/tasks/${id}/checklist`)
      .then((r) => {if(request===loadSequence.current)setChecklist(unwrapList<ChecklistItem>(r));})
      .catch(() => {if(request===loadSequence.current)setChecklist([]);});
    apiFetch(`/tasks/${id}/timelogs`)
      .then((r) => setTimelogs(unwrapList<Timelog>(r)))
      .catch(() => setTimelogs([]));
    apiFetch(`/tasks/${id}/comments`)
      .then((r) => setComments(unwrapList<CommentItem>(r)))
      .catch(() => setComments([]));
    apiFetch(`/tasks/${id}/activity`)
      .then((r) => setTimeline(unwrapList<TimelineItem>(r)))
      .catch(() => setTimeline([]));
  }, [id]);
  async function tick(item:ChecklistItem,done:boolean){if(!task||task.version==null||tickBusy)return;setTickBusy(true);setFormErr(null);loadSequence.current++;setChecklist(rows=>rows.map(row=>row.id===item.id?{...row,done}:row));try{await apiFetch(`/tasks/${id}/checklist/${item.id}`,{method:"PATCH",ifMatch:String(task.version),body:{done}});await load();}catch(e:any){setChecklist(rows=>rows.map(row=>row.id===item.id?{...row,done:item.done}:row));setFormErr(e.uiMessage??"Không cập nhật được checklist");}finally{setTickBusy(false);}}

  useEffect(() => {
    void load();
  }, [load]);

  // Realtime: task/activity/comment đổi -> refetch đúng scope qua API.
  useRealtimeEvent(["task", "comment", "activity", "workflow"], () => {
    void load();
  });

  // Subtasks persist through the parent-scoped API and survive reload.
  async function createSubtask() {
    const v = fieldErr(subTitle.trim().length > 0, "form.required");
    setFormErr(v);
    if (v) return;
    setFormErr(null);
    setFormOk(null);
    try {
      const r = await apiFetch(`/tasks/${id}/subtasks`, {
        ...mutationOpts({ idempotencyKey: newIdempotencyKey() }),
        method: "POST",
        body: { title: subTitle.trim() },
      });
      const d = unwrapData<Subtask>(r);
      if (d && typeof d === "object" && (d as Subtask).id) {
        await load();
      }
      setSubTitle("");
      setFormOk(t("task.created_ok"));
    } catch (e: any) {
      setFormErr(e?.uiMessage ?? t("error.generic"));
    }
  }

  async function createChecklist() {
    const v = fieldErr(clTitle.trim().length > 0, "form.required");
    setFormErr(v);
    if (v) return;
    setFormErr(null);
    setFormOk(null);
    try {
      await apiFetch(`/tasks/${id}/checklist`, {
        ...mutationOpts({ idempotencyKey: newIdempotencyKey() }),
        method: "POST",
        body: { title: clTitle.trim() },
      });
      setClTitle("");
      setFormOk(t("task.created_ok"));
      const r = await apiFetch(`/tasks/${id}/checklist`).catch(() => null);
      if (r) setChecklist(unwrapList<ChecklistItem>(r));
    } catch (e: any) {
      setFormErr(e?.uiMessage ?? t("error.generic"));
    }
  }

  async function createTimelog() {
    const v =
      fieldErr(tlDate.trim().length > 0, "form.required") ??
      (/^\d{4}-\d{2}-\d{2}$/.test(tlDate.trim()) && !Number.isNaN(Date.parse(tlDate.trim()))
        ? null
        : t("form.invalid_date")) ??
      (tlMinutes.trim().length > 0 && Number.isInteger(Number(tlMinutes)) && Number(tlMinutes) > 0
        ? null
        : t("form.invalid_number"));
    setFormErr(v);
    if (v) return;
    setFormErr(null);
    setFormOk(null);
    try {
      await apiFetch(`/tasks/${id}/timelogs`, {
        ...mutationOpts({ idempotencyKey: newIdempotencyKey() }),
        method: "POST",
        body: { work_date: tlDate.trim(), minutes: Number(tlMinutes), note: tlNote.trim() || undefined },
      });
      setTlDate("");
      setTlMinutes("");
      setTlNote("");
      setFormOk(t("task.created_ok"));
      const r = await apiFetch(`/tasks/${id}/timelogs`).catch(() => null);
      if (r) setTimelogs(unwrapList<Timelog>(r));
    } catch (e: any) {
      setFormErr(e?.uiMessage ?? t("error.generic"));
    }
  }

  async function createComment() {
    const v = fieldErr(commentBody.trim().length > 0, "form.required");
    setFormErr(v);
    if (v) return;
    setFormErr(null);
    setFormOk(null);
    try {
      await apiFetch(`/tasks/${id}/comments`, {
        ...mutationOpts({ idempotencyKey: newIdempotencyKey() }),
        method: "POST",
        body: { body: commentBody.trim() },
      });
      setCommentBody("");
      setFormOk(t("task.created_ok"));
      const r = await apiFetch(`/tasks/${id}/comments`).catch(() => null);
      if (r) setComments(unwrapList<CommentItem>(r));
    } catch (e: any) {
      setFormErr(e?.uiMessage ?? t("error.generic"));
    }
  }

  // Dependency: validate client (non-empty + chống chu trình hiển nhiên) rồi gọi API.
  async function addDependency() {
    const v = fieldErr(depId.trim().length > 0, "form.required");
    setDepErr(v);
    if (v) return;
    if (depId.trim() === id) {
      setDepErr(t("task.cycle_detected"));
      return;
    }
    setDepErr(null);
    try {
      await apiFetch(`/tasks/${id}/dependencies`, {
        ...mutationOpts({ idempotencyKey: newIdempotencyKey() }),
        method: "POST",
        body: { depends_on_task_id: depId.trim() },
      });
      setDepOpen(false);
      setDepId("");
      setFormOk(t("task.created_ok"));
    } catch (e: any) {
      setDepErr(e?.uiMessage ?? t("error.generic"));
    }
  }

  if (loading) return <Spinner label={t("task.loading")} />;
  if (err)
    return (
      <Card className="text-center">
        <ErrorAlert message={err} />
        <div className="mt-3 flex justify-center">
          <Button variant="secondary" onClick={() => void load()}>
            {t("common.retry")}
          </Button>
        </div>
      </Card>
    );
  if (!task) return <ErrorAlert message={t("task.not_found")} />;

  const due = task.due_at ?? task.deadline;
  const version = task.version ?? task.etag ?? "";

  return (
    <div className="mx-auto max-w-6xl">
      <div className="flex flex-wrap items-center gap-2">
        <h1 className="page-title">{task.title}</h1>
        {task.state && <Badge state={task.state}>{task.state}</Badge>}
        {task.priority && <Badge priority={task.priority}>{task.priority}</Badge>}
      </div>
      <p className="page-subtitle">
        {t("task.assignee")}: {task.assignee_id ?? "-"}
        {due ? ` · ${String(due).slice(0, 10)}` : ""}
        {` · ${t("task.version")}: ${String(version)}`}
      </p>
      {task.description && <p className="mt-2 text-sm text-slate-600">{task.description}</p>}
      <ProjectTaskEditor id={String(id)} title={task.title} description={task.description} version={String(version)} dueAt={task.due_at} onChanged={()=>void load()}/>
      <ErrorAlert message={formErr} />
      {formOk && (
        <p role="status" className="mt-2 rounded-lg border border-green-200 bg-green-50 px-3 py-2 text-sm text-green-800">
          {formOk}
        </p>
      )}
      <div className="mt-4 grid gap-4 lg:grid-cols-[1fr_320px]">
        <div className="flex flex-col gap-4">
          <Card>
            <h2 className="text-sm font-semibold text-slate-900">{t("task.subtasks")}</h2>
            <p className="mt-1 text-xs text-slate-500">Công việc con trong phạm vi quyền truy cập của bạn.</p>
            <ul className="mt-2 flex flex-col gap-1.5">
              {subtasks.map((s) => (
                <li key={s.id} className="flex items-center justify-between rounded-lg bg-slate-50 px-2.5 py-1.5 text-sm">
                  <span>{s.title}</span>
                  <Badge state={s.state}>{s.state}</Badge>
                </li>
              ))}
              {subtasks.length === 0 && <li className="text-sm text-slate-400">—</li>}
            </ul>
            {moreSubtasks&&<Button variant="secondary" onClick={()=>void apiFetch(`/tasks/${id}/subtasks`,{query:{page:subtaskPage+1,size:20}}).then(r=>{const rows=unwrapList<Subtask>(r);setSubtasks(old=>[...old,...rows]);setSubtaskPage(n=>n+1);setMoreSubtasks(rows.length===20);}).catch(()=>setFormErr("Không tải được công việc con"))}>Tải thêm công việc con</Button>}
            <div className="mt-2 flex gap-2">
              <TextInput aria-label={t("task.subtask_title")} placeholder={t("task.subtask_title")} value={subTitle} onChange={(e) => setSubTitle(e.target.value)} />
              <Button variant="secondary" size="sm" onClick={() => void createSubtask()}>
                {t("task.add_subtask")}
              </Button>
            </div>
          </Card>
          <Card>
            <h2 className="text-sm font-semibold text-slate-900">{t("task.checklist")}</h2>
            <ul className="mt-2 flex flex-col gap-1.5 text-sm">
              {checklist.map((c) => (
                <li key={c.id} className="flex items-center gap-2">
                  <input type="checkbox" checked={!!c.done} disabled={tickBusy} onChange={e=>void tick(c,e.target.checked)} className="checkbox" aria-label={c.title ?? c.label ?? c.id} />
                  <span className={c.done ? "text-slate-400 line-through" : ""}>{c.title ?? c.label ?? c.id}</span>
                </li>
              ))}
              {checklist.length === 0 && <li className="text-sm text-slate-400">—</li>}
            </ul>
            <div className="mt-2 flex gap-2">
              <TextInput aria-label={t("task.checklist_title")} placeholder={t("task.checklist_title")} value={clTitle} onChange={(e) => setClTitle(e.target.value)} />
              <Button variant="secondary" size="sm" onClick={() => void createChecklist()}>
                {t("task.add_checklist")}
              </Button>
            </div>
          </Card>
          <Card>
            <h2 className="text-sm font-semibold text-slate-900">{t("task.dependencies")}</h2>
            <Button variant="secondary" size="sm" onClick={() => { setDepOpen(true); setDepErr(null); }}>
              {t("task.add_dependency")}
            </Button>
            {depOpen && (
              <div role="dialog" aria-modal="true" aria-label={t("task.add_dependency")} className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
                <div className="w-full max-w-sm rounded-xl bg-white p-6 shadow-lg">
                  <h3 className="text-base font-bold text-slate-900">{t("task.add_dependency")}</h3>
                  <div className="mt-3">
                    <TextInput aria-label={t("task.depends_on_id")} placeholder={t("task.depends_on_id")} value={depId} onChange={(e) => setDepId(e.target.value)} />
                  </div>
                  <ErrorAlert message={depErr} />
                  <div className="mt-4 flex justify-end gap-2">
                    <Button variant="primary" size="sm" onClick={() => void addDependency()}>
                      {t("common.confirm")}
                    </Button>
                    <Button variant="secondary" size="sm" onClick={() => setDepOpen(false)}>
                      {t("common.cancel")}
                    </Button>
                  </div>
                </div>
              </div>
            )}
          </Card>
          <Card>
            <h2 className="text-sm font-semibold text-slate-900">{t("task.timelogs")}</h2>
            {timelogs.length === 0 ? (
              <p className="mt-2 text-sm text-slate-400">—</p>
            ) : (
              <table className="table mt-2">
                <thead>
                  <tr>
                    <th>{t("common.date")}</th>
                    <th>{t("task.minutes")}</th>
                    <th>{t("task.note")}</th>
                  </tr>
                </thead>
                <tbody>
                  {timelogs.map((x) => (
                    <tr key={x.id}>
                      <td>{x.work_date}</td>
                      <td>{x.minutes}</td>
                      <td>{x.note ?? "—"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
            <div className="mt-2 flex flex-wrap gap-2">
              <TextInput className="!w-36" aria-label={t("task.work_date")} placeholder="YYYY-MM-DD" value={tlDate} onChange={(e) => setTlDate(e.target.value)} />
              <TextInput className="!w-24" aria-label={t("task.minutes")} placeholder="60" inputMode="numeric" value={tlMinutes} onChange={(e) => setTlMinutes(e.target.value)} />
              <TextInput className="!w-44" aria-label={t("task.note")} placeholder={t("task.note")} value={tlNote} onChange={(e) => setTlNote(e.target.value)} />
              <Button variant="secondary" size="sm" onClick={() => void createTimelog()}>
                {t("task.add_timelog")}
              </Button>
            </div>
          </Card>
          <Card>
            <h2 className="text-sm font-semibold text-slate-900">{t("task.comments")}</h2>
            {comments.length === 0 ? (
              <div className="mt-2"><EmptyState title={t("common.empty")} /></div>
            ) : (
              <ul className="mt-2 flex flex-col gap-2">
                {comments.map((c) => (
                  <li key={c.id} className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm">
                    <p className="text-slate-800">{c.body}</p>
                    {c.author_user_id && (
                      <p className="mt-1 text-xs text-slate-500">{t("common.author")}: {c.author_user_id}</p>
                    )}
                  </li>
                ))}
              </ul>
            )}
            <div className="mt-2 flex gap-2">
              <TextInput aria-label={t("task.comment_body")} placeholder={t("task.comment_body")} value={commentBody} onChange={(e) => setCommentBody(e.target.value)} />
              <Button variant="secondary" size="sm" onClick={() => void createComment()}>
                {t("task.add_comment")}
              </Button>
            </div>
          </Card>
        </div>
        <div className="flex flex-col gap-4">
          <WorkflowPanel disabled={tickBusy} taskId={String(id)} version={String(version)} role={task.role} state={task.state} onDone={() => void load()} />
          <TaskResponsibility id={String(id)} version={String(version)} onChanged={() => void load()} />
          <Card>
            <h2 className="text-sm font-semibold text-slate-900">{t("task.timeline")}</h2>
            {timeline.length === 0 ? (
              <p className="mt-2 text-sm text-slate-400">—</p>
            ) : (
              <ul className="mt-2 flex flex-col gap-2">
                {timeline.map((ev) => (
                  <li key={ev.id} className="flex gap-2 text-xs text-slate-600">
                    <span className="timeline-dot" />
                    <div className="rounded bg-slate-50 px-2 py-1">
                      <span className="font-semibold text-slate-800">{ev.kind}</span>
                      {ev.redacted && (
                        <span className="ml-1.5 rounded bg-slate-200 px-1 text-slate-500">{t("task.redacted")}</span>
                      )}
                      {ev.payload && !ev.redacted && (
                        <span className="mt-0.5 block break-all text-slate-500">{ev.payload}</span>
                      )}
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </div>
      </div>
    </div>
  );
}
