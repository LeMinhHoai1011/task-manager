import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { apiFetch } from "../api/client";
import { t } from "../i18n";
import { Button, Card, ErrorAlert, TextInput } from "../components/ui";

export default function ChangePassword() {
  const auth = useAuth();
  const nav = useNavigate();
  const [oldPassword, setOldPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const mustChange = auth.user?.mustChangePassword ?? false;

  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-slate-50 to-blue-50 px-4">
      <Card className="center w-full !max-w-sm !p-6 shadow-lg">
        <h1 className="page-title">{t("app.title")}</h1>
        <p className="page-subtitle">
          {mustChange ? t("auth.must_change_password") : t("auth.change_password")}
        </p>
        {auth.sessionNotice && (
          <div role="alert" className="mt-4 rounded-md border border-yellow-400 bg-yellow-50 p-2 text-sm">
            {t(auth.sessionNotice)}
            <button className="ml-2 rounded border px-2 py-1 text-xs" onClick={auth.dismissNotice}>
              {t("common.close")}
            </button>
          </div>
        )}
        <form
          className="mt-4 flex flex-col gap-3"
          onSubmit={async (e) => {
            e.preventDefault();
            setBusy(true);
            setErr(null);
            if (!oldPassword) {
              setErr(t("auth.old_password") + ": " + t("form.required"));
              setBusy(false);
              return;
            }
            if (!newPassword) {
              setErr(t("auth.password") + ": " + t("form.required"));
              setBusy(false);
              return;
            }
            if (newPassword !== confirmPassword) {
              setErr(t("auth.password_mismatch"));
              setBusy(false);
              return;
            }
            try {
              await apiFetch("/auth/change-password", {
                method: "POST",
                body: { old_password: oldPassword, new_password: newPassword },
              });
              setErr(null);
              if (mustChange && auth.user) {
                auth.user.mustChangePassword = false;
              }
              nav("/tasks");
            } catch (e: any) {
              setErr(e?.uiMessage ?? t("error.generic"));
            } finally {
              setBusy(false);
            }
          }}
        >
          {mustChange ? (
            <label className="block">
              <span className="label">{t("auth.temporary_password")}</span>
              <TextInput
                value={oldPassword}
                onChange={(e) => setOldPassword(e.target.value)}
                type="password"
                autoComplete="current-password"
                required
              />
            </label>
          ) : (
            <label className="block">
              <span className="label">{t("auth.old_password")}</span>
              <TextInput
                value={oldPassword}
                onChange={(e) => setOldPassword(e.target.value)}
                type="password"
                autoComplete="current-password"
                required
              />
            </label>
          )}
          <label className="block">
            <span className="label">{t("auth.password")}</span>
            <TextInput
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              type="password"
              autoComplete="new-password"
              required
              minLength={8}
            />
          </label>
          <label className="block">
            <span className="label">{t("auth.confirm_password")}</span>
            <TextInput
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              type="password"
              autoComplete="new-password"
              required
              minLength={8}
            />
          </label>
          <ErrorAlert message={err} />
          <Button disabled={busy} type="submit">
            {t("auth.change_password")}
          </Button>
          <Link className="text-center text-sm font-medium text-blue-700 hover:underline" to="/tasks">
            {t("auth.cancel")}
          </Link>
        </form>
      </Card>
    </div>
  );
}
