import { useState } from "react";
import { apiFetch, mutationOpts, newIdempotencyKey } from "../api/client";
import { useAuth } from "../auth/AuthContext";
import { t } from "../i18n";
import { Button, Card, ErrorAlert, TextInput } from "../components/ui";

// Workflow: start/submit/approve/reject+reason>=3/cancel/reopen+reason.
// Ẩn nút theo role phía client nhưng backend vẫn check.
const ACTIONS = ["start", "submit", "approve", "reject", "cancel", "reopen"] as const;
const LABELS = {start:"Bắt đầu",submit:"Nộp hoàn thành",approve:"Phê duyệt",reject:"Yêu cầu sửa",cancel:"Hủy công việc",reopen:"Mở lại"};
const ACTION_STATES: Record<string,string[]> = {TODO:["start","cancel"],IN_PROGRESS:["submit","cancel"],PENDING_APPROVAL:["approve","reject","cancel"],COMPLETED:["reopen"],CANCELLED:["reopen"]};

const ACTION_VARIANT: Record<(typeof ACTIONS)[number], "primary" | "indigo" | "success" | "warning" | "gray" | "purple"> = {
  start: "primary",
  submit: "indigo",
  approve: "success",
  reject: "warning",
  cancel: "gray",
  reopen: "purple",
};

export default function WorkflowPanel({ taskId, version, state, onDone,disabled=false,allowedActions=[] }: { taskId: string; version: string; role?: string;state?:string;allowedActions?:string[]; onDone?: () => void;disabled?:boolean }) {
  const auth = useAuth();
  const [reason, setReason] = useState("");
  const [err, setErr] = useState<string | null>(null);
  const [ok, setOk] = useState<string | null>(null);
  const [idem] = useState(() => newIdempotencyKey());
  const [busy,setBusy]=useState(false);

  const canShow = (a: (typeof ACTIONS)[number]): boolean => {
    if(!allowedActions.includes(a))return false;
    return !!state && (ACTION_STATES[state]??[]).includes(a);
  };

  async function run(action: (typeof ACTIONS)[number]) {
    if(busy||disabled||!canShow(action))return;
    setErr(null);
    setOk(null);
    if ((action === "reject" || action === "reopen") && reason.trim().length < 3) {
      setErr(t("workflow.reason_min"));
      return;
    }
    const op = async () =>
      apiFetch(`/tasks/${taskId}/workflow/${action}`, {
        ...mutationOpts({ ifMatch: version, idempotencyKey: idem, body: reason ? { reason } : {} }),
        method: "POST",
      });
    try {
      setBusy(true);
      await op();
      setOk(`Đã thực hiện: ${LABELS[action].toLowerCase()}`);
      onDone?.();
    } catch (e: any) {
      if (e?.needsReauth) {
        // Giữ Idempotency-Key cũ retry đúng request.
        auth.requireReauth(op);
        return;
      }
      setErr(e?.uiMessage ?? t("error.generic"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card>
      <h2 className="section-title">Tiến trình công việc</h2>
      {ACTIONS.some(a=>canShow(a)&&["reject","reopen"].includes(a))&&<div className="mt-2">
        <TextInput aria-label="Lý do thay đổi trạng thái" placeholder="Lý do yêu cầu sửa hoặc mở lại (ít nhất 3 ký tự)" value={reason} onChange={(e) => setReason(e.target.value)} />
      </div>}
      {!ACTIONS.some(canShow)&&<p>Không có thao tác chuyển trạng thái cho vai trò hoặc trạng thái hiện tại.</p>}
      <div className="row">
        {ACTIONS.filter(canShow).map((a) => (
          <Button key={a} disabled={busy||disabled} size="sm" variant={ACTION_VARIANT[a]} onClick={() => void run(a)}>
            {LABELS[a]}
          </Button>
        ))}
      </div>
      <ErrorAlert message={err} />
      {ok && <p role="status" className="mt-2 text-sm text-green-700">{ok}</p>}
    </Card>
  );
}
