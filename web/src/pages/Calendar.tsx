import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { useRealtimeEvent } from "../realtime/RealtimeContext";
import ScopeFilters from "../components/ScopeFilters";
import { apiFetch, mutationOpts, newIdempotencyKey, unwrapData, unwrapList, unwrapPage } from "../api/client";
import { t } from "../i18n";
import { Badge, Button, EmptyState, ErrorAlert, Select, Spinner, TextInput } from "../components/ui";
function localDay(d: Date) { return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`; }
function dateOf(day: string) { return new Date(`${day}T12:00:00`); }
const ruleLabels: Record<string, string> = { ACTIVE: "Đang chờ", PAUSED: "Tạm dừng", FIRED: "Đã xử lý", CANCELLED: "Đã hủy" };
// Backend GET /calendar trả { data: [{ task_id, project_id, due_at }] } —
// không có title/tz riêng; web render đúng field thật, không bịa.
interface CalItem {
    scope?: string;
    due_is_date_only?: boolean;
    due_date_only?: string;
    task_id: string;
    project_id?: string;
    due_at?: string | null;
    title?: string;
    deadline?: string | null;
    tz?: string;
}
interface TaskOption {
    id: string;
    title: string;
}
export default function Calendar() {
    const [mode, setMode] = useState<"day" | "week" | "month" | "unscheduled">("month");
    const [selectedDay, setSelectedDay] = useState(() => localDay(new Date()));
    const [personal, setPersonal] = useState(true), [title, setTitle] = useState(""), [description, setDescription] = useState(""), [dueAt, setDueAt] = useState("");
    const [busy, setBusy] = useState(false), [editing, setEditing] = useState(""), [editAt, setEditAt] = useState("");
    const sending = useRef(false), createKey = useRef({ body: "", key: "" });
    const [items, setItems] = useState<CalItem[]>([]);
    const [err, setErr] = useState<string | null>(null);
    const [loading, setLoading] = useState(true);
    const [anchor, setAnchor] = useState(() => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`; });
    const [refresh, setRefresh] = useState(0);
    const [scope, setScope] = useState("all"), [projectId, setProjectId] = useState(""), [teamId, setTeamId] = useState("");
    const [page, setPage] = useState(0);
    const requestSequence = useRef(0);
    useRealtimeEvent(["task", "workflow"], () => setRefresh(n => n + 1));
    const tz = Intl.DateTimeFormat().resolvedOptions().timeZone;
    useEffect(() => {
        const request = ++requestSequence.current;
        setLoading(true);
        setErr(null);
        apiFetch("/calendar", { query: { view: mode, date: anchor, timezone: tz, scope, projectId: projectId || undefined, teamId: teamId || undefined, page, size: 200 } })
            .then((r) => {
            if (request !== requestSequence.current)
                return;
            const d = unwrapData<unknown>(r);
            setItems(Array.isArray(d) ? (d as CalItem[]) : unwrapList<CalItem>(r));
        })
            .catch((e: any) => {
            if (request !== requestSequence.current)
                return;
            setErr(e?.uiMessage ?? t("error.generic"));
            setItems([]);
        })
            .finally(() => { if (request === requestSequence.current)
            setLoading(false); });
        return () => { requestSequence.current++; };
    }, [mode, tz, anchor, refresh, scope, projectId, teamId, page]);
    const dueOf = (i: CalItem): string | null => i.due_at ?? i.deadline ?? null;
    const dated = items.filter((i) => dueOf(i));
    const unscheduled = items.filter((i) => !dueOf(i));
    const [remTask, setRemTask] = useState("");
    const [remAt, setRemAt] = useState("");
    const [remMsg, setRemMsg] = useState<string | null>(null);
    const [tasks, setTasks] = useState<TaskOption[]>([]);
    const [projectTaskCursor, setProjectTaskCursor] = useState<string | null>(null), [personalTaskPage, setPersonalTaskPage] = useState(0), [personalTaskMore, setPersonalTaskMore] = useState(false), [optionsBusy, setOptionsBusy] = useState(false);
    const [reminders, setReminders] = useState<{
        id: string;
        task_id: string;
        remind_at: string;
        status: string;
        title?: string;
        scope?: string;
        available?: boolean;
    }[]>([]);
    const [reminderPage, setReminderPage] = useState(0), [reminderMore, setReminderMore] = useState(false);
    async function loadReminders(p = 0) { const r = await apiFetch<{
        data: typeof reminders;
    }>("/reminders", { query: { page: p, size: 100 } }); setReminders(old => p ? [...old, ...r.data] : r.data); setReminderPage(p); setReminderMore(r.data.length === 100); }
    useEffect(() => { void loadReminders().catch(() => setRemMsg("Không tải được danh sách nhắc việc. Hãy tải lại.")); }, [refresh]);
    async function moreTaskOptions() { setOptionsBusy(true); try {
        const rows: TaskOption[] = [];
        if (projectTaskCursor) {
            const p = unwrapPage<TaskOption>(await apiFetch("/tasks", { query: { limit: 100, cursor: projectTaskCursor } }));
            rows.push(...p.items);
            setProjectTaskCursor(p.nextCursor);
        }
        if (personalTaskMore) {
            const r = await apiFetch<{
                data: {
                    content: TaskOption[];
                    last: boolean;
                };
            }>("/me/tasks", { query: { size: 100, page: personalTaskPage + 1 } });
            rows.push(...r.data.content);
            setPersonalTaskPage(n => n + 1);
            setPersonalTaskMore(!r.data.last);
        }
        setTasks(old => Array.from(new Map([...old, ...rows].map(task => [task.id, task])).values()));
    }
    catch {
        setRemMsg("Không tải được thêm công việc");
    }
    finally {
        setOptionsBusy(false);
    } }
    useEffect(() => {
        Promise.all([apiFetch("/tasks", { query: { limit: 100, sort: "updated_at", order: "desc" } }), apiFetch<{
                data: {
                    content: TaskOption[];
                    last: boolean;
                };
            }>("/me/tasks", { query: { size: 100 } })])
            .then(([project, personal]) => { const p = unwrapPage<TaskOption>(project); setTasks([...p.items, ...personal.data.content]); setProjectTaskCursor(p.nextCursor); setPersonalTaskPage(0); setPersonalTaskMore(!personal.data.last); })
            .catch(() => {setTasks([]);setRemMsg("Không tải được danh sách công việc để đặt nhắc. Hãy tải lại trang.");});
    }, []);
    async function createReminder() {
        if (sending.current)
            return;
        setRemMsg(null);
        if ((personal && !title.trim()) || (!personal && !remTask)) {
            setRemMsg("Nhập tiêu đề hoặc chọn công việc.");
            return;
        }
        if (!remAt || Number.isNaN(Date.parse(remAt)) || Date.parse(remAt) <= Date.now()) {
            setRemMsg("Thời điểm nhắc phải ở tương lai.");
            return;
        }
        if (personal && (!dueAt || Number.isNaN(Date.parse(dueAt)) || Date.parse(dueAt) < Date.parse(remAt))) {
            setRemMsg("Thời điểm hẹn phải bằng hoặc sau thời điểm nhắc.");
            return;
        }
        const body = personal ? { title: title.trim(), description, due_at: new Date(dueAt).toISOString(), remind_at: new Date(remAt).toISOString(), timezone: tz } : { task_id: remTask, remind_at: new Date(remAt).toISOString() };
        const serialized = JSON.stringify(body);
        if (createKey.current.body !== serialized)
            createKey.current = { body: serialized, key: newIdempotencyKey() };
        sending.current = true;
        setBusy(true);
        try {
            await apiFetch(personal ? "/reminders/personal" : "/reminders", { ...mutationOpts({ idempotencyKey: createKey.current.key }), method: "POST", body });
            setTitle("");
            setDescription("");
            setDueAt("");
            setRemAt("");
            createKey.current = { body: "", key: "" };
            setRemMsg("Đã lưu lịch nhắc vào tài khoản.");
            setRefresh(n => n + 1);
        }
        catch (e: any) {
            setRemMsg(e.uiMessage ?? "Không thể lưu lịch nhắc. Nội dung nhập vẫn được giữ lại.");
        }
        finally {
            sending.current = false;
            setBusy(false);
        }
    }
    async function changeReminder(id: string, action: "pause" | "resume" | "cancel" | "edit") {
        if (sending.current)
            return;
        if (action === "cancel" && !window.confirm("Hủy nhắc việc này? Công việc và nội dung vẫn được giữ lại."))
            return;
        if (action === "edit" && (!editAt || Number.isNaN(Date.parse(editAt)) || Date.parse(editAt) <= Date.now())) {
            setRemMsg("Chọn thời điểm nhắc trong tương lai.");
            return;
        }
        sending.current = true;
        setBusy(true);
        setRemMsg(null);
        try {
            await apiFetch(`/reminders/${id}${action === "edit" ? "" : `/${action}`}`, { ...mutationOpts({ idempotencyKey: newIdempotencyKey() }), method: action === "edit" ? "PUT" : "POST", body: action === "edit" ? { remind_at: new Date(editAt).toISOString() } : {} });
            setEditing("");
            setRemMsg("Đã lưu thay đổi nhắc việc.");
            setRefresh(n => n + 1);
        }
        catch (e: any) {
            setRemMsg(e.uiMessage ?? "Không thể cập nhật nhắc việc.");
        }
        finally {
            sending.current = false;
            setBusy(false);
        }
    }
    function move(direction: number) { const d = dateOf(anchor); if (mode === "month") {
        d.setDate(1);
        d.setMonth(d.getMonth() + direction);
    }
    else
        d.setDate(d.getDate() + direction * (mode === "week" ? 7 : 1)); const next = localDay(d); setAnchor(next); setSelectedDay(next); setPage(0); }
    const month = dateOf(anchor), first = new Date(month.getFullYear(), month.getMonth(), 1, 12), offset = (first.getDay() + 6) % 7, days = new Date(month.getFullYear(), month.getMonth() + 1, 0).getDate();
    const weekStart = dateOf(anchor);
    weekStart.setDate(weekStart.getDate() - (weekStart.getDay() + 6) % 7);
    const cells = mode === "month" ? Array.from({ length: Math.ceil((offset + days) / 7) * 7 }, (_, i) => i < offset || i >= offset + days ? null : localDay(new Date(month.getFullYear(), month.getMonth(), i - offset + 1, 12))) : Array.from({ length: 7 }, (_, i) => { const d = new Date(weekStart); d.setDate(d.getDate() + i); return localDay(d); });
    const grouped = new Map<string, CalItem[]>();
    for (const item of dated) {
        const due=new Date(dueOf(item)!);
        const parts=item.due_is_date_only?new Intl.DateTimeFormat("en-CA",{timeZone:item.tz??tz,year:"numeric",month:"2-digit",day:"2-digit"}).formatToParts(due):[];
        const day=item.due_is_date_only?(item.due_date_only??["year","month","day"].map(type=>parts.find(part=>part.type===type)?.value).join("-")):localDay(due);
        grouped.set(day, [...(grouped.get(day) ?? []), item]);
    }
    const visible = mode === "unscheduled" ? unscheduled : mode === "day" ? dated : grouped.get(selectedDay) ?? [];
    return <div className="workspace-page calendar-workspace">
    <header className="workspace-heading"><div><p className="eyebrow">THỜI GIAN CỦA BẠN</p><h1>Lịch & nhắc hẹn</h1><p>Xem thời hạn, lưu lịch hẹn và chủ động nhắc việc.</p></div><a className="btn btn-primary" href="#new-reminder">+ Tạo lịch hẹn</a></header>
    <div className="calendar-toolbar"><div className="flex flex-wrap items-center gap-2"><Button variant="secondary" aria-label="Kỳ trước" onClick={() => move(-1)}>←</Button><Button variant="secondary" onClick={() => { const d = localDay(new Date()); setAnchor(d); setSelectedDay(d); setPage(0); }}>Hôm nay</Button><Button variant="secondary" aria-label="Kỳ sau" onClick={() => move(1)}>→</Button><input className="input !w-auto" aria-label="Ngày xem" type="date" value={anchor} onChange={e => { if (e.target.value) {
        setAnchor(e.target.value);
        setSelectedDay(e.target.value);
        setPage(0);
    } }}/></div><nav className="workspace-tabs !border-0 !m-0" aria-label="Kiểu lịch">{(["day", "week", "month", "unscheduled"] as const).map(value => <button key={value} aria-current={mode === value ? "page" : undefined} onClick={() => { setMode(value); setPage(0); }}>{{ day: "Ngày", week: "Tuần", month: "Tháng", unscheduled: "Chưa lên lịch" }[value]}</button>)}</nav></div>
    <ScopeFilters scope={scope} projectId={projectId} teamId={teamId} onChange={(s, p, t) => { setScope(s); setProjectId(p); setTeamId(t); setPage(0); }}/>
    <ErrorAlert message={err}/><div className="calendar-layout"><section className="work-panel !p-0 overflow-hidden"><div className="calendar-section-heading"><div><h2>{month.toLocaleDateString("vi-VN", { month: "long", year: "numeric" })}</h2><p className="text-xs text-slate-500">Múi giờ {tz} · {items.length} công việc trong trang này</p></div>{loading && <Spinner />}</div>
    {(mode === "month" || mode === "week") && <><div className="calendar-weekdays">{["T2", "T3", "T4", "T5", "T6", "T7", "CN"].map(d => <span key={d}>{d}</span>)}</div><div className="calendar-grid">{cells.map((day, index) => day ? <button className={`calendar-cell ${day === selectedDay ? 'is-selected' : ''} ${day === localDay(new Date()) ? 'is-today' : ''}`} key={day} aria-label={`Xem ngày ${day}`} aria-pressed={day === selectedDay} onClick={() => setSelectedDay(day)}><span className="calendar-date">{dateOf(day).getDate()}</span>{(grouped.get(day) ?? []).slice(0, 2).map(item => <span className="calendar-event" key={item.task_id}>{item.title ?? "Công việc"}</span>)}{(grouped.get(day)?.length ?? 0) > 2 && <span className="text-xs text-slate-500">+ thêm</span>}</button> : <div className="calendar-cell calendar-blank" key={index}/>)}</div></>}
    <div className="agenda"><h3>{mode === "unscheduled" ? "Chưa có thời hạn" : `Lịch ngày ${dateOf(mode === "day" ? anchor : selectedDay).toLocaleDateString("vi-VN")}`}</h3>{!loading && !visible.length && <EmptyState title="Chưa có công việc trong mục này"/>}{visible.map(item => <Link className="agenda-row" key={item.task_id} to={item.scope === "PERSONAL" ? "/my-tasks" : `/tasks/${item.task_id}`}><span className="agenda-time">{dueOf(item) ? item.due_is_date_only ? "Cả ngày" : new Date(dueOf(item)!).toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" }) : "—"}</span><span><strong>{item.title ?? "Công việc"}</strong><span className="block text-xs text-slate-500">{item.scope === "PERSONAL" ? "Cá nhân" : "Dự án / nhóm"}</span></span><span className="ml-auto" aria-hidden="true">↗</span></Link>)}</div><footer className="directory-toolbar"><Button variant="secondary" disabled={loading || !page} onClick={() => setPage(n => n - 1)}>Trang trước</Button><span className="text-xs text-slate-500">Trang {page + 1} · tối đa 200 việc</span><Button variant="secondary" disabled={loading || items.length < 200} onClick={() => setPage(n => n + 1)}>Trang sau</Button></footer></section>
    <aside className="work-panel self-start" id="new-reminder"><p className="eyebrow">THÊM VÀO LỊCH</p><h2>Lưu một lời nhắc</h2><div className="workspace-tabs"><button aria-current={personal ? "page" : undefined} onClick={() => setPersonal(true)}>Hẹn cá nhân</button><button aria-current={!personal ? "page" : undefined} onClick={() => setPersonal(false)}>Nhắc công việc</button></div><form className="space-y-4" onSubmit={e => { e.preventDefault(); void createReminder(); }}>{personal ? <><label>Tiêu đề<TextInput required maxLength={200} placeholder="Ví dụ: Hẹn trao đổi với khách hàng" value={title} onChange={e => setTitle(e.target.value)}/></label><label>Nội dung cần nhớ<textarea className="input min-h-24" maxLength={10000} placeholder="Địa điểm, nội dung chuẩn bị…" value={description} onChange={e => setDescription(e.target.value)}/></label><label>Thời điểm hẹn<TextInput required type="datetime-local" value={dueAt} onChange={e => setDueAt(e.target.value)}/></label></> : <label>Công việc cần nhắc<Select required value={remTask} onChange={e => setRemTask(e.target.value)}><option value="">Chọn công việc</option>{tasks.map(task => <option key={task.id} value={task.id}>{task.title}</option>)}</Select></label>}{!personal && (projectTaskCursor || personalTaskMore) && <Button type="button" variant="ghost" disabled={optionsBusy} onClick={() => void moreTaskOptions()}>Tải thêm công việc</Button>}<label>Thời điểm nhắc<TextInput required type="datetime-local" value={remAt} onChange={e => setRemAt(e.target.value)}/></label><p className="text-xs text-slate-500">Giờ {tz}. Lưu vào tài khoản và nhắc qua thông báo trong ứng dụng. Giờ yên lặng có thể trì hoãn thông báo.</p><Button className="w-full" disabled={busy}>{busy ? "Đang lưu…" : personal ? "Lưu lịch hẹn & nhắc" : "Lưu nhắc việc"}</Button></form><Link className="mt-4 block text-xs text-primary" to="/settings">Cài đặt thông báo và giờ yên lặng →</Link></aside></div>
    {remMsg && <p role="status" className="notice">{remMsg}</p>}
    <section className="work-panel"><div className="flex items-start justify-between gap-3"><div><p className="eyebrow">ĐÃ LƯU TRONG TÀI KHOẢN</p><h2>Nhắc việc của bạn</h2></div><Button variant="secondary" disabled={busy} onClick={() => setRefresh(n => n + 1)}>Tải lại</Button></div>{!reminders.length && <EmptyState title="Chưa có nhắc việc"/>}{reminders.map(rule => { const terminal = ["FIRED", "CANCELLED"].includes(rule.status); return <article className="reminder-row" key={rule.id}><div className="min-w-0 flex-1"><strong className="break-words">{rule.available===false?"Công việc không còn khả dụng":rule.title ?? tasks.find(task => task.id === rule.task_id)?.title ?? "Công việc"}</strong><p className="text-sm text-slate-500">{new Date(rule.remind_at).toLocaleString("vi-VN")} · {tz}</p></div><Badge tone={rule.status === "ACTIVE" ? "badge-in-progress" : "badge-todo"}>{ruleLabels[rule.status] ?? rule.status}</Badge>{!terminal && rule.available !== false && <div className="flex flex-wrap gap-2"><Button size="sm" variant="secondary" disabled={busy} onClick={() => { setEditing(rule.id); const d = new Date(rule.remind_at); setEditAt(`${localDay(d)}T${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`); }}>Đổi giờ</Button><Button size="sm" variant="ghost" disabled={busy} onClick={() => void changeReminder(rule.id, rule.status === "PAUSED" ? "resume" : "pause")}>{rule.status === "PAUSED" ? "Tiếp tục" : "Tạm dừng"}</Button><Button size="sm" variant="ghost" disabled={busy} onClick={() => void changeReminder(rule.id, "cancel")}>Hủy nhắc</Button></div>}{editing === rule.id && <form className="flex w-full flex-wrap gap-2" onSubmit={e => { e.preventDefault(); void changeReminder(rule.id, "edit"); }}><input className="input !w-auto" aria-label="Thời điểm nhắc mới" type="datetime-local" required value={editAt} onChange={e => setEditAt(e.target.value)}/><Button disabled={busy}>Lưu giờ nhắc</Button><Button variant="secondary" type="button" onClick={() => setEditing("")}>Đóng</Button></form>}</article>; })}{reminderMore && <Button variant="secondary" onClick={() => void loadReminders(reminderPage + 1).catch(() => setRemMsg("Không tải được nhắc việc"))}>Tải thêm nhắc việc</Button>}</section>
  </div>;
}
