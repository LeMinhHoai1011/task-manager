import { useEffect, useRef, useState } from "react";
import type {TaskAccess} from "../auth/AccessContext";
import { useSearchParams } from "react-router-dom";
import { API_BASE, apiFetch, mutationOpts, newIdempotencyKey, unwrapData, unwrapList, unwrapPage } from "../api/client";
import { t } from "../i18n";
import { Button, Card, EmptyState, ErrorAlert, Select, Spinner, TextInput } from "../components/ui";

// PH-3: upload_session -> staging -> commit (version tăng, không ghi đè),
// preview/download qua gateway + StreamTicket single-use TTL 30s.
// Route /files không truyền prop: tự lấy taskId từ query ?taskId= (prop giữ để tương thích).
interface Attachment {
  id: string;
  file_name?: string;
  status?: string;
  version?: number;
}
interface TaskOption { id: string; title: string; }

export default function Files({ taskId: taskIdProp }: { taskId?: string }) {
  const [access,setAccess]=useState<TaskAccess|null>(null);
  const [params, setParams] = useSearchParams();
  const taskId = taskIdProp ?? params.get("taskId") ?? "";
  const [err, setErr] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [restoreSrc, setRestoreSrc] = useState("");
  const [attId, setAttId] = useState("");
  const [progress, setProgress] = useState<number | null>(null);
  const [dragOver, setDragOver] = useState(false);
  const [versions, setVersions] = useState<Array<{ version: number; note: string }>>([]);
  const [attachments, setAttachments] = useState<Attachment[]>([]);
  const [listLoading, setListLoading] = useState(false);
  const [taskInput, setTaskInput] = useState(taskId);
  const [tasks, setTasks] = useState<TaskOption[]>([]);
  const fileRef = useRef<HTMLInputElement>(null);

  async function loadAttachments(tid: string) {
    if (!tid) {
      setAttachments([]);
      return;
    }
    setListLoading(true);
    try {
      const r = await apiFetch(`/files/tasks/${tid}/attachments`);
      // Backend trả { data: { items: [...] } } — bóc canonical.
      const d = unwrapData<{ items: Attachment[] }>(r);
      const items = d && typeof d === "object" && Array.isArray((d as { items?: unknown }).items)
        ? (d as { items: Attachment[] }).items
        : unwrapList<Attachment>(r);
      setAttachments(items);
    } catch (e: any) {
      setErr(e?.uiMessage ?? t("error.generic"));
      setAttachments([]);
    } finally {
      setListLoading(false);
    }
  }

  useEffect(() => {
    let active=true;setAccess(null);
    if(taskId)void apiFetch(`/tasks/${taskId}/access`).then(r=>{if(active)setAccess(unwrapData<TaskAccess>(r)??null);}).catch(()=>{});
    void loadAttachments(taskId);
    return()=>{active=false;};
  }, [taskId]);

  useEffect(() => {
    apiFetch("/tasks", { query: { limit: 100, sort: "updated_at", order: "desc" } })
      .then((r) => setTasks(unwrapPage<TaskOption>(r).items))
      .catch(() => setTasks([]));
  }, []);

  async function upload(f: File) {
    if(!access?.files)return;
    setErr(null);
    setNotice(null);
    if (!taskId) {
      setErr(t("files.task_required"));
      return;
    }
    setProgress(10);
    try {
      const init = await apiFetch("/files/upload", {
        ...mutationOpts({ idempotencyKey: newIdempotencyKey() }),
        method: "POST",
        body: {
          task_id: taskId,
          file_name: f.name,
          mime_type: f.type || "application/octet-stream",
          declared_size: f.size,
        },
      });
      setProgress(40);
      const d0 = unwrapData<{ upload_session_id: string }>(init);
      const sid = d0?.upload_session_id;
      if (!sid) throw new Error("no session");

      // Server-side staging: bytes go browser -> API -> private MinIO.
      // (MinIO is never exposed to the browser.)
      const fd = new FormData();
      fd.append("file", f);
      await apiFetch(`/files/upload/${sid}/content`, {
        ...mutationOpts({ idempotencyKey: newIdempotencyKey() }),
        method: "POST",
        body: fd,
      });
      setProgress(60);

      await apiFetch(`/files/upload/${sid}/complete`, {
        ...mutationOpts({ idempotencyKey: newIdempotencyKey() }),
        method: "POST",
        body: { actual_bytes: f.size },
      });
      setProgress(80);
      const done = await apiFetch(
        `/files/upload/${sid}/commit`,
        {
          ...mutationOpts({ idempotencyKey: newIdempotencyKey() }),
          method: "POST",
          body: { change_note: "web upload" },
        }
      );
      const d = unwrapData<{ attachment_id: string; version: number }>(done) ?? (done as { attachment_id: string; version: number });
      setNotice(`${t("files.upload_ok")} ${d.attachment_id} v${d.version}.`);
      setAttId(d.attachment_id);
      setVersions((v) => [...v, { version: d.version, note: "web upload" }]);
      setProgress(100);
      void loadAttachments(taskId);
    } catch (e: any) {
      setErr(e?.uiMessage ?? t("error.generic"));
    } finally {
      setTimeout(() => setProgress(null), 800);
    }
  }

  async function ticket(kind: "preview" | "download", id: string) {
    setErr(null);
    if (!id.trim()) {
      setErr(t("form.required"));
      return;
    }
    try {
      const r = await apiFetch(`/files/${id.trim()}/${kind}`);
      const d = unwrapData<{ ticket: string }>(r);
      const ticketVal = d?.ticket;
      if (!ticketVal) throw new Error("no ticket");
      // Stream single-use: mở 1 lần, ticket hết hiệu lực sau 30s hoặc sau 1 lượt dùng.
      window.open(`${API_BASE}/files/stream/${ticketVal}`, "_blank");
    } catch (e: any) {
      setErr(e?.uiMessage ?? t("error.generic"));
    }
  }

  async function restoreVersion(id: string) {
    if(!access?.files)return;
    setErr(null);
    if (!id.trim() || !restoreSrc.trim() || !Number.isInteger(Number(restoreSrc))) {
      setErr(t("form.invalid_number"));
      return;
    }
    try {
      await apiFetch(`/files/${id.trim()}/restore`, {
        ...mutationOpts({ idempotencyKey: newIdempotencyKey() }),
        method: "POST",
        body: { source_version: Number(restoreSrc), change_note: "web restore" },
      });
      setNotice(`${t("admin.done")} ${id.trim()}.`);
      setVersions((v) => [...v, { version: Number(restoreSrc), note: "restored as new version" }]);
    } catch (e: any) {
      setErr(e?.uiMessage ?? t("error.generic"));
    }
  }

  return (
    <div className="mx-auto max-w-4xl">
      <h1 className="page-title">{t("nav.files")}</h1>
      <div className="mt-3 flex flex-wrap gap-2 rounded-xl border border-slate-200 bg-white p-3">
        <Select
          aria-label="Công việc"
          value={taskInput}
          onChange={(e) => setTaskInput(e.target.value)}
        ><option value="">Chọn công việc để quản lý tệp</option>{tasks.map((task) => <option key={task.id} value={task.id}>{task.title}</option>)}</Select>
        <Button
          variant="secondary"
          size="sm"
          onClick={() => {
            if (!taskInput.trim()) {
              setErr(t("files.task_required"));
              return;
            }
            setParams(taskInput.trim() ? { taskId: taskInput.trim() } : {});
          }}
        >
          {t("common.refresh")}
        </Button>
      </div>
      {!taskId && <p role="note" className="mt-2 text-sm text-slate-500">{t("files.task_required")}</p>}
      {taskId&&!access?.files&&<p role="note" className="card mt-3">Bạn chỉ xem/tải tệp trong phạm vi công việc được cấp quyền. Quản lý dự án được tải lên và khôi phục phiên bản khi công việc cho phép.</p>}
      <ErrorAlert message={err} />
      {notice && <p role="status" className="mt-2 rounded-lg border border-green-200 bg-green-50 px-3 py-2 text-sm text-green-800">{notice}</p>}
      {access?.files&&<div
        className={`dropzone mt-3 ${dragOver ? "dropzone-active" : ""}`}
        onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
        onDragLeave={() => setDragOver(false)}
        onDrop={(e) => {
          e.preventDefault();
          setDragOver(false);
          const f = e.dataTransfer.files?.[0];
          if (f) void upload(f);
        }}
        onClick={() => fileRef.current?.click()}
        role="button"
        tabIndex={0}
      >
        <p className="font-medium">Kéo & thả file vào đây, hoặc bấm để chọn</p>
        <input
          ref={fileRef}
          type="file"
          className="hidden"
          onChange={(e) => {
            const f = e.target.files?.[0];
            if (f) void upload(f);
          }}
        />
        <Button variant="secondary" size="sm">Chọn tệp</Button>
      </div>}
      {progress !== null && (
        <div className="mt-2">
          <div className="progress-track"><div className="progress-fill" style={{ width: `${progress}%` }} /></div>
        </div>
      )}
      <Card className="mt-4">
        <h2 className="text-sm font-semibold text-slate-900">{t("files.attachments")}</h2>
        {listLoading ? (
          <div className="mt-2"><Spinner /></div>
        ) : !taskId ? (
          <p className="mt-2 text-sm text-slate-400">{t("files.task_required")}</p>
        ) : attachments.length === 0 ? (
          <div className="mt-2"><EmptyState title={t("files.no_attachments")} /></div>
        ) : (
          <table className="table mt-2">
            <thead><tr><th>{t("common.id")}</th><th>{t("common.name")}</th><th>{t("common.status")}</th><th>{t("files.version")}</th><th className="text-right">{t("common.actions")}</th></tr></thead>
            <tbody>
              {attachments.map((a) => (
                <tr key={a.id}>
                  <td className="font-mono text-xs">{a.id}</td>
                  <td>{a.file_name ?? "—"}</td>
                  <td>{a.status ?? "—"}</td>
                  <td>v{a.version ?? "?"}</td>
                  <td>
                    <div className="flex justify-end gap-2">
                      <Button variant="secondary" size="sm" onClick={() => void ticket("preview", a.id)}>{t("files.preview")}</Button>
                      <Button variant="secondary" size="sm" onClick={() => void ticket("download", a.id)}>{t("common.download")}</Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>
      <Card className="mt-4">
        {access?.files&&<div className="flex flex-wrap items-center gap-2">
          <TextInput className="!w-56" placeholder="attachment id" aria-label="attachment id" value={attId} onChange={(e) => setAttId(e.target.value)} />
          <TextInput className="!w-36" placeholder="source version" aria-label="source version" value={restoreSrc} onChange={(e) => setRestoreSrc(e.target.value)} />
          <Button variant="secondary" size="sm" onClick={() => { if (attId) void restoreVersion(attId); }}>
            Restore as new version
          </Button>
        </div>}
        <p className="mt-2 text-xs text-slate-500">
          Preview/download mở ticket 1 lần duy nhất (TTL 30s) qua gateway, không dùng URL
          object-storage trực tiếp. {t("files.restore_note")}
        </p>
        <div className="row">
          <Button variant="secondary" size="sm" onClick={() => void ticket("preview", attId)}>{t("files.preview")}</Button>
          <Button variant="secondary" size="sm" onClick={() => void ticket("download", attId)}>{t("common.download")}</Button>
        </div>
      </Card>
      <Card className="mt-4">
        <h2 className="text-sm font-semibold text-slate-900">Phiên bản phát sinh trong phiên hiện tại</h2>
        <table className="table mt-2">
          <thead><tr><th>Version</th><th>Note</th></tr></thead>
          <tbody>
            {versions.map((v, i) => (
              <tr key={i}><td>v{v.version}</td><td>{v.note}</td></tr>
            ))}
            {versions.length === 0 && <tr><td colSpan={2} className="text-slate-400">Chưa có thao tác phiên bản trong phiên này.</td></tr>}
          </tbody>
        </table>
      </Card>
    </div>
  );
}
