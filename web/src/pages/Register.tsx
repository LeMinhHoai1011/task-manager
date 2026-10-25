import { useState } from "react";
import { Link } from "react-router-dom";
import { apiFetch } from "../api/client";
import { t } from "../i18n";
import { Button, Card, ErrorAlert, TextInput } from "../components/ui";

/**
 * Self-registration screen. Backend creates the account in PENDING state;
 * an administrator must approve it before login is possible. This is the
 * onboarding path for a fresh deployment (bootstrap only activates the first
 * admin, it does not create accounts).
 */
export default function Register() {
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [err, setErr] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const [busy, setBusy] = useState(false);

  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-slate-50 to-blue-50 px-4">
      <Card className="center w-full !max-w-sm !p-6 shadow-lg">
        <h1 className="page-title">{t("app.title")}</h1>
        <p className="page-subtitle">{t("auth.register_desc")}</p>

        {done ? (
          <div className="mt-4 flex flex-col gap-4">
            <div className="rounded-lg border border-green-200 bg-green-50 px-3 py-2 text-sm text-green-800">
              {t("auth.register_success")}
            </div>
            <Link className="text-center text-sm font-medium text-blue-700 hover:underline" to="/login">
              {t("auth.have_account")} {t("auth.login")}
            </Link>
          </div>
        ) : (
          <form
            className="mt-4 flex flex-col gap-3"
            onSubmit={async (e) => {
              e.preventDefault();
              setErr(null);
              const u = username.trim();
              const em = email.trim();
              const dn = displayName.trim();
              if (!u || !em || !dn) {
                setErr(t("form.required"));
                return;
              }
              if (password.length < 8) {
                setErr(t("auth.password_min"));
                return;
              }
              if (password !== confirm) {
                setErr(t("auth.password_mismatch"));
                return;
              }
              setBusy(true);
              try {
                await apiFetch("/auth/register", {
                  method: "POST",
                  skipRefresh: true,
                  body: {
                    username: u,
                    email: em,
                    password,
                    display_name: dn,
                  },
                });
                setDone(true);
              } catch (e: any) {
                setErr(e?.uiMessage ?? t("error.generic"));
              } finally {
                setBusy(false);
              }
            }}
          >
            <label className="block">
              <span className="label">{t("auth.username")}</span>
              <TextInput
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                autoComplete="username"
                required
              />
            </label>
            <label className="block">
              <span className="label">{t("auth.email")}</span>
              <TextInput
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                type="email"
                autoComplete="email"
                required
              />
            </label>
            <label className="block">
              <span className="label">{t("auth.display_name")}</span>
              <TextInput
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
                autoComplete="name"
                required
              />
            </label>
            <label className="block">
              <span className="label">{t("auth.password")}</span>
              <TextInput
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                type="password"
                autoComplete="new-password"
                minLength={8}
                required
              />
            </label>
            <label className="block">
              <span className="label">{t("auth.confirm_password")}</span>
              <TextInput
                value={confirm}
                onChange={(e) => setConfirm(e.target.value)}
                type="password"
                autoComplete="new-password"
                minLength={8}
                required
              />
            </label>
            <ErrorAlert message={err} />
            <Button disabled={busy} type="submit">
              {t("auth.register")}
            </Button>
            <Link className="text-center text-sm font-medium text-blue-700 hover:underline" to="/login">
              {t("auth.have_account")} {t("auth.login")}
            </Link>
          </form>
        )}
      </Card>
    </div>
  );
}
