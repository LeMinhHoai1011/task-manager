import { useEffect, useState } from "react";
import { apiFetch, newIdempotencyKey } from "../api/client";
import AppIcon from "./AppIcon";

type Props = {
  id: string;
  title: string;
  description?: string | null;
  version: string;
  dueAt?: string | null;
  onChanged: () => void;
};

export default function ProjectTaskEditor({
  id,
  title,
  description,
  version,
  dueAt,
  onChanged,
}: Props) {
  const [open, setOpen] = useState(false);
  const [name, setName] = useState(title);
  const [notes, setNotes] = useState(description ?? "");
  const [due, setDue] = useState("");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");

  useEffect(() => {
    setName(title);
    setNotes(description ?? "");
  }, [id, title, description]);

  useEffect(() => {
    if (!dueAt) {
      setDue("");
      return;
    }
    const d = new Date(dueAt);
    setDue(new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16));
  }, [id, dueAt]);

  async function save(deadline: boolean) {
    setBusy(true);
    setMessage("");
    try {
      await apiFetch(`/tasks/${id}${deadline ? "/deadline" : ""}`, {
        method: deadline ? "POST" : "PATCH",
        ifMatch: version,
        idempotencyKey: newIdempotencyKey(),
        body: deadline
          ? {
              due_at: due ? new Date(due).toISOString() : null,
              due_timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
            }
          : { title: name.trim(), description: notes },
      });
      setMessage(deadline ? "Đã cập nhật hạn" : "Đã cập nhật nội dung");
      onChanged();
    } catch (e: any) {
      setMessage(e.uiMessage ?? "Không cập nhật được. Kiểm tra quyền hoặc tải lại dữ liệu.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="card rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-4 shadow-xs">
      <div className="flex items-center justify-between">
        <button
          disabled={busy}
          onClick={() => setOpen((v) => !v)}
          className="btn btn-secondary text-xs flex items-center gap-1.5"
        >
          <AppIcon name="edit" size={14} />
          <span>{open ? "Đóng sửa công việc" : "Sửa thông tin / deadline"}</span>
        </button>
      </div>

      {open && (
        <div className="mt-4 pt-4 border-t border-slate-100 dark:border-slate-800 space-y-4">
          <form
            onSubmit={(e) => {
              e.preventDefault();
              void save(false);
            }}
            className="space-y-3"
          >
            <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300">
              Tên công việc
              <input
                disabled={busy}
                required
                maxLength={200}
                className="input text-xs mt-1"
                value={name}
                onChange={(e) => setName(e.target.value)}
              />
            </label>
            <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300">
              Mô tả
              <textarea
                disabled={busy}
                className="textarea text-xs mt-1"
                rows={3}
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
              />
            </label>
            <button
              disabled={busy || !name.trim()}
              className="btn btn-primary text-xs"
            >
              Lưu nội dung
            </button>
          </form>

          <form
            onSubmit={(e) => {
              e.preventDefault();
              void save(true);
            }}
            className="pt-3 border-t border-slate-100 dark:border-slate-800 space-y-3"
          >
            <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300">
              Hạn công việc
              <input
                disabled={busy}
                type="datetime-local"
                className="input text-xs mt-1"
                value={due}
                onChange={(e) => setDue(e.target.value)}
              />
            </label>
            <button disabled={busy} className="btn btn-secondary text-xs">
              Lưu hạn
            </button>
          </form>
        </div>
      )}

      {message && (
        <p
          role="status"
          className="mt-3 text-xs rounded-lg bg-blue-50 dark:bg-blue-950/40 border border-blue-200 dark:border-blue-800 p-2 text-blue-800 dark:text-blue-300"
        >
          {message}
        </p>
      )}
    </section>
  );
}
