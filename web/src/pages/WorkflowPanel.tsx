import { useState } from "react";
import { apiFetch, mutationOpts, newIdempotencyKey } from "../api/client";
import { useAuth } from "../auth/AuthContext";
import { t } from "../i18n";
import { Button, Card, ErrorAlert, TextInput } from "../components/ui";

// Workflow: start/submit/approve/reject+reason>=3/cancel/reopen+reason.
// Ẩn nút theo role phía client nhưng backend vẫn check.
const ACTIONS = ["start", "submit", "approve", "reject", "cancel", "reopen"] as const;

const ACTION_VARIANT: Record<(typeof ACTIONS)[number], "primary" | "indigo" | "success" | "warning" | "gray" | "purple"> = {
  start: "primary",
  submit: "indigo",
  approve: "success",
  reject: "warning",
  cancel: "gray",
  reopen: "purple",
};

export default function WorkflowPanel({ taskId, version, role, onDone }: { taskId: string; version: string; role?: string; onDone?: () => void }) {
  const auth = useAuth();
  const [reason, setReason] = useState("");
  const [err, setErr] = useState<string | null>(null);
  const [ok, setOk] = useState<string | null>(null);
  const [idem] = useState(() => newIdempotencyKey());

  const canShow = (a: (typeof ACTIONS)[number]): boolean => {
    if (!role) return true;
    if (a === "approve" && !(role === "admin" || role === "reviewer" || role === "manager")) return false;
    if (a === "reject" && !(role === "admin" || role === "reviewer" || role === "manager")) return false;
    return true;
  };

  async function run(action: (typeof ACTIONS)[number]) {
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
      await op();
      setOk(`${action} OK`);
      onDone?.();
    } catch (e: any) {
      if (e?.needsReauth) {
        // Giữ Idempotency-Key cũ retry đúng request.
        auth.requireReauth(op);
        return;
      }
      setErr(e?.uiMessage ?? t("error.generic"));
    }
  }

  return (
    <Card>
      <h2 className="text-sm font-semibold text-slate-900">Workflow</h2>
      <div className="mt-2">
        <TextInput aria-label="reason" placeholder="reason (>=3 cho reject/reopen)" value={reason} onChange={(e) => setReason(e.target.value)} />
      </div>
      <div className="row">
        {ACTIONS.filter(canShow).map((a) => (
          <Button key={a} size="sm" variant={ACTION_VARIANT[a]} onClick={() => void run(a)}>
            {a}
          </Button>
        ))}
      </div>
      <ErrorAlert message={err} />
      {ok && <p role="status" className="mt-2 text-sm text-green-700">{ok}</p>}
    </Card>
  );
}
