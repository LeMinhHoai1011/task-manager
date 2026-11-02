import { useEffect, useState } from "react";
import { apiFetch } from "../api/client";
type Detail = { task: { version: number }; checklist: { id: string; title: string; done: boolean }[]; tags: { tag: string }[] };
export default function PersonalChecklist({ id, onChanged }: { id: string; onChanged: () => void }) {
  const [data, setData] = useState<Detail | null>(null);
  const [title, setTitle] = useState("");
  const [labels, setLabels] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  async function load() {
    const result = await apiFetch<{ data: Detail }>(`/me/tasks/${id}`);
    setData(result.data); setLabels(result.data.tags.map(t => t.tag).join(", "));
  }
  useEffect(() => { setData(null); setError(""); void load().catch(() => setError("Không tải được checklist.")); }, [id]);
  async function mutate(path: string, method: string, body: unknown) {
    if (!data) return;
    setBusy(true); setError("");
    try {
      await apiFetch(`/me/tasks/${id}${path}`, { method, body, ifMatch: String(data.task.version) });
      setTitle(""); await load(); onChanged();
    } catch { setError("Không lưu được. Hãy đóng và mở lại để tải version mới."); }
    finally { setBusy(false); }
  }
  return <section className="card space-y-3 p-4"><h2>Checklist và nhãn</h2>
    {error && <p role="alert">{error} <button disabled={busy} onClick={() => { setError(""); void load().catch(() => setError("Không tải được checklist.")); }}>Tải lại checklist</button></p>}
    {!data && !error && <p>Đang tải…</p>}
    {data && <><p>Tiến độ: {data.checklist.filter(i => i.done).length}/{data.checklist.length}</p>
      {data.checklist.map(item => <label className="block" key={item.id}><input type="checkbox" disabled={busy} checked={item.done} onChange={() => void mutate(`/checklist/${item.id}`, "PATCH", { done: !item.done })} /> {item.title}</label>)}
      <form onSubmit={e => { e.preventDefault(); void mutate("/checklist", "POST", { title }); }}><label>Mục checklist<input required maxLength={200} value={title} onChange={e => setTitle(e.target.value)} /></label><button disabled={busy || !title.trim()}>Thêm mục</button></form>
      <form onSubmit={e => { e.preventDefault(); void mutate("/labels", "PUT", { labels: labels.split(",").map(s => s.trim()).filter(Boolean) }); }}><label>Nhãn, cách nhau bằng dấu phẩy<input value={labels} onChange={e => setLabels(e.target.value)} /></label><button disabled={busy}>Lưu nhãn</button></form>
    </>}
  </section>;
}
