import { useEffect, useState, useRef } from "react";
import { apiFetch } from "../api/client";
import PersonalChecklist from "./PersonalChecklist";

type Task = { id: string; title: string; state: string; version: number; description?: string; notes?: string; priority?: string; startAt?: string | null; dueAt?: string | null; dueIsDateOnly?: boolean; dueTimezone?: string; dueDateInput?: string };
function deadlineDate(task: Task) {
  if (task.dueDateInput !== undefined) return task.dueDateInput;
  if (!task.dueAt) return "";
  const parts = new Intl.DateTimeFormat("en-CA", { timeZone: task.dueTimezone || "UTC", year: "numeric", month: "2-digit", day: "2-digit" }).formatToParts(new Date(task.dueAt));
  return ["year", "month", "day"].map(type => parts.find(p => p.type === type)?.value).join("-");
}
function localDateTime(value?: string | null) {
  if (!value) return "";
  const date = new Date(value);
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
}
export default function MyTasks() {
  const [items, setItems] = useState<Task[]>([]);
  const [title, setTitle] = useState("");
  const pendingCreate = useRef<{ title: string; key: string } | null>(null);
  const [page, setPage] = useState(0);
  const [last, setLast] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [query, setQuery] = useState("");
  const [state, setState] = useState("");
  const [deleted, setDeleted] = useState(false);
  const [sort, setSort] = useState("createdAt");
  const [priority, setPriority] = useState("");
  const [tag, setTag] = useState("");
  const [overdue, setOverdue] = useState(false);
  const [editing, setEditing] = useState<Task | null>(null);
  const [detailId, setDetailId] = useState<string | null>(null);
  const [summary, setSummary] = useState<Record<string, number> | null>(null);
  const requestSequence = useRef(0);
  const summarySequence = useRef(0);
  async function loadSummary() {
    const sequence = ++summarySequence.current;
    try { const result = await apiFetch<{ data: Record<string, number> }>("/me/tasks/summary"); if (sequence === summarySequence.current) setSummary(result.data); }
    catch { if (sequence === summarySequence.current) setError("Không tải được thống kê."); }
  }
  async function load() {
    const sequence = ++requestSequence.current;
    setBusy(true); setError("");
    try {
      const result = await apiFetch<{ data: { content: Task[]; last: boolean } }>("/me/tasks", { query: { page, size: 20, q: query, state, deleted, sort, priority, tag, overdue } });
      if (sequence !== requestSequence.current) return;
      setItems(result.data.content); setLast(result.data.last);
      void loadSummary();
    } catch { if (sequence === requestSequence.current) setError("Không tải được công việc. Vui lòng thử lại."); }
    finally { if (sequence === requestSequence.current) setBusy(false); }
  }
  useEffect(() => { void load(); return () => { requestSequence.current++; summarySequence.current++; }; }, [page, state, deleted, sort, priority, overdue]);
  async function start(task: Task) {
    setBusy(true); setError("");
    try { await apiFetch(`/me/tasks/${task.id}/start`, { method: "POST", ifMatch: String(task.version) }); await load(); }
    catch { setError("Không bắt đầu được công việc. Hãy tải lại dữ liệu."); }
    finally { setBusy(false); }
  }
  async function change(task: Task, action: "save" | "delete" | "restore") {
    setBusy(true); setError("");
    try {
      await apiFetch(`/me/tasks/${task.id}${action === "restore" ? "/restore" : ""}`, {
        method: action === "save" ? "PATCH" : action === "delete" ? "DELETE" : "POST",
        ifMatch: String(task.version),
        ...(action === "save" ? { body: { title: task.title, description: task.description ?? null, notes: task.notes ?? null, priority: task.priority ?? "MEDIUM", startAt: task.startAt || null, dueAt: task.dueIsDateOnly ? (deadlineDate(task) ? `${deadlineDate(task)}T00:00:00Z` : null) : task.dueAt || null, dueIsDateOnly: !!task.dueIsDateOnly, dueTimezone: task.dueTimezone || Intl.DateTimeFormat().resolvedOptions().timeZone } } : {}),
      });
      setEditing(null); await load();
    } catch { setError("Không lưu được thay đổi. Có thể công việc đã được sửa ở nơi khác; hãy tải lại."); }
    finally { setBusy(false); }
  }
  async function create(event: React.FormEvent) {
    event.preventDefault(); setBusy(true); setError("");
    if (!pendingCreate.current || pendingCreate.current.title !== title)
      pendingCreate.current = { title, key: crypto.randomUUID() };
    try { await apiFetch("/me/tasks", { body: { title }, idempotencyKey: pendingCreate.current.key }); pendingCreate.current = null; setTitle(""); await load(); }
    catch { setError("Không tạo được công việc."); }
    finally { setBusy(false); }
  }
  async function toggle(task: Task) {
    setBusy(true); setError("");
    try {
      await apiFetch(`/me/tasks/${task.id}/completion`, { method: "PATCH", ifMatch: String(task.version), body: { completed: task.state !== "COMPLETED" } });
      await load();
    } catch { setError("Không lưu được trạng thái. Hãy tải lại trước khi thử tiếp."); }
    finally { setBusy(false); }
  }
  return <section className="personal-tasks mx-auto max-w-3xl space-y-4">
    <h1 className="page-title">Việc của tôi</h1>
    <p>Công việc riêng tư, không cần tạo dự án.</p>
    <button disabled={busy} onClick={() => { setPage(0); setSort("priority"); }}>Ưu tiên cao trước</button>
    <div className="flex flex-wrap gap-3"><label>Ưu tiên<select value={priority} onChange={e => { setPage(0); setPriority(e.target.value); }}><option value="">Tất cả</option>{["LOW", "MEDIUM", "HIGH", "URGENT"].map(p => <option key={p}>{p}</option>)}</select></label><label>Nhãn<input value={tag} onChange={e => setTag(e.target.value)} /></label><label><input type="checkbox" checked={overdue} onChange={e => { setPage(0); setOverdue(e.target.checked); }} />Quá hạn</label></div>
    {!deleted && <div className="flex flex-wrap gap-2">{items.filter(task => task.state === "TODO").map(task => <button disabled={busy} key={task.id} onClick={() => void start(task)}>Bắt đầu: {task.title}</button>)}</div>}
    <button onClick={() => void loadSummary()}>Cập nhật thống kê cá nhân</button>
    {summary && <p>Tổng: {summary.total} · Cần làm: {summary.TODO} · Đang làm: {summary.IN_PROGRESS} · Hoàn thành: {summary.COMPLETED} · Quá hạn: {summary.overdue}</p>}
    <label>Checklist của công việc<select value={detailId ?? ""} onChange={e => setDetailId(e.target.value || null)}><option value="">Chọn công việc</option>{!deleted && items.map(task => <option key={task.id} value={task.id}>{task.title}</option>)}</select></label>
    {detailId && !deleted && <PersonalChecklist key={`${detailId}:${items.find(task => task.id === detailId)?.version ?? 0}`} id={detailId} onChanged={() => void load()} />}
    <form onSubmit={e => { e.preventDefault(); if (page !== 0) setPage(0); else void load(); }} className="flex flex-wrap gap-2">
      <input aria-label="Tìm công việc" value={query} onChange={e => setQuery(e.target.value)} placeholder="Tìm tiêu đề hoặc mô tả" /><button disabled={busy}>Tìm</button>
      <select aria-label="Trạng thái" value={state} onChange={e => { setPage(0); setState(e.target.value); }}><option value="">Tất cả trạng thái</option><option value="TODO">Cần làm</option><option value="IN_PROGRESS">Đang làm</option><option value="COMPLETED">Hoàn thành</option></select>
      <select aria-label="Sắp xếp" value={sort} onChange={e => { setPage(0); setSort(e.target.value); }}><option value="createdAt">Mới nhất</option><option value="dueAt">Hạn hoàn thành</option><option value="title">Tiêu đề</option></select>
      <label><input type="checkbox" checked={deleted} onChange={e => { setPage(0); setDeleted(e.target.checked); }} />Thùng rác</label>
    </form>
    <form onSubmit={create} className="flex gap-2"><input aria-label="Tiêu đề công việc" className="input flex-1" value={title} maxLength={200} required onChange={e => setTitle(e.target.value)} /><button className="btn btn-primary" disabled={busy || !title.trim()}>Thêm việc</button></form>
    {error && <p role="alert">{error} <button onClick={() => void load()}>Tải lại</button></p>}
    {busy && <p role="status">Đang xử lý…</p>}
    {!busy && !error && items.length === 0 && <p>Bạn chưa có công việc nào.</p>}
    <ul className="space-y-3">{items.map(task => <li key={task.id} className="card p-4"><label className="flex gap-3"><input type="checkbox" disabled={busy || deleted} checked={task.state === "COMPLETED"} onChange={() => void toggle(task)} /><span className={task.state === "COMPLETED" ? "line-through" : ""}>{task.title}</span></label><p>{task.priority} {task.dueAt && (task.dueIsDateOnly ? deadlineDate(task) : new Date(task.dueAt).toLocaleString())}</p>{deleted ? <button disabled={busy} onClick={() => void change(task, "restore")}>Khôi phục</button> : <div className="flex gap-3"><button disabled={busy} onClick={() => setEditing({ ...task })}>Sửa chi tiết</button><button disabled={busy} onClick={() => void change(task, "delete")}>Chuyển vào thùng rác</button></div>}</li>)}</ul>
    {editing && <form className="card space-y-3 p-4" onSubmit={e => { e.preventDefault(); void change(editing, "save"); }}>
      <h2>Sửa công việc</h2>
      <label className="block">Tiêu đề<input required maxLength={200} value={editing.title} onChange={e => setEditing({ ...editing, title: e.target.value })} /></label>
      <label className="block">Mô tả<textarea value={editing.description ?? ""} onChange={e => setEditing({ ...editing, description: e.target.value })} /></label>
      <label className="block">Ghi chú<textarea value={editing.notes ?? ""} onChange={e => setEditing({ ...editing, notes: e.target.value })} /></label>
      <label className="block">Ưu tiên<select value={editing.priority ?? "MEDIUM"} onChange={e => setEditing({ ...editing, priority: e.target.value })}>{["LOW", "MEDIUM", "HIGH", "URGENT"].map(p => <option key={p}>{p}</option>)}</select></label>
      <label className="block"><input type="checkbox" checked={!!editing.dueIsDateOnly} onChange={e => setEditing({ ...editing, dueIsDateOnly: e.target.checked, dueTimezone: editing.dueTimezone || Intl.DateTimeFormat().resolvedOptions().timeZone })} />Hạn chỉ có ngày (không chỉ định giờ)</label>
      {editing.dueIsDateOnly && <label>Ngày hết hạn<input type="date" value={deadlineDate(editing)} onChange={e => setEditing({ ...editing, dueDateInput: e.target.value })} /></label>}
      <label className="block">Thời điểm bắt đầu (giờ địa phương)<input type="datetime-local" value={localDateTime(editing.startAt)} onChange={e => setEditing({ ...editing, startAt: e.target.value ? new Date(e.target.value).toISOString() : null })} /></label>
      {!editing.dueIsDateOnly && <label className="block">Hạn hoàn thành (giờ địa phương)<input type="datetime-local" value={localDateTime(editing.dueAt)} onChange={e => setEditing({ ...editing, dueAt: e.target.value ? new Date(e.target.value).toISOString() : null })} /></label>}
      <button disabled={busy} className="btn btn-primary">Lưu</button><button type="button" disabled={busy} onClick={() => setEditing(null)}>Hủy</button>
    </form>}
    <div className="flex gap-3"><button disabled={busy || page === 0} onClick={() => setPage(page - 1)}>Trang trước</button><span>Trang {page + 1}</span><button disabled={busy || last} onClick={() => setPage(page + 1)}>Trang sau</button></div>
  </section>;
}
