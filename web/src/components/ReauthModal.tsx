import { useState } from "react";
import { t } from "../i18n";
import { Button, ErrorAlert, TextInput } from "./ui";

export default function ReauthModal({
  open,
  message,
  onConfirm,
  onCancel,
}: {
  open: boolean;
  message: string;
  onConfirm: (password: string) => Promise<void>;
  onCancel: () => void;
}) {
  const [pw, setPw] = useState("");
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  if (!open) return null;
  return (
    <div role="dialog" aria-modal="true" aria-label={t("auth.reauth_title")} className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
      <div className="w-full max-w-sm rounded-xl bg-white p-6 shadow-[var(--shadow-modal)]">
        <h2 className="text-lg font-bold text-slate-900">{t("auth.reauth_title")}</h2>
        <p className="mt-1 text-sm text-slate-500">{t(message)}</p>
        <div className="mt-4">
          <TextInput
            type="password"
            aria-label={t("auth.password")}
            value={pw}
            onChange={(e) => setPw(e.target.value)}
            placeholder={t("auth.password")}
          />
        </div>
        <ErrorAlert message={err} />
        <div className="mt-4 flex justify-end gap-2">
          <Button
            variant="primary"
            disabled={busy}
            onClick={async () => {
              setBusy(true);
              setErr(null);
              try {
                await onConfirm(pw);
                setPw("");
              } catch {
                setErr(t("error.generic"));
              } finally {
                setBusy(false);
              }
            }}
          >
            {t("auth.reauth_confirm")}
          </Button>
          <Button variant="secondary" disabled={busy} onClick={onCancel}>
            {t("auth.reauth_cancel")}
          </Button>
        </div>
      </div>
    </div>
  );
}
