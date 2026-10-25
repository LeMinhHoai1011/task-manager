import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { t } from "../i18n";
import { Button, Card, ErrorAlert, TextInput } from "../components/ui";

export default function Login() {
  const auth = useAuth();
  const nav = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-slate-50 to-blue-50 px-4">
      <Card className="center w-full !max-w-sm !p-6 shadow-lg">
        <h1 className="page-title">{t("app.title")}</h1>
        <p className="page-subtitle">{t("auth.login")}</p>
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
            const em = email.trim();
            // Backend resolve username-hoặc-email nên chỉ check non-empty, không ép format email.
            if (!em) {
              setErr(t("auth.email") + ": " + t("form.required"));
              setBusy(false);
              return;
            }
            if (!password) {
              setErr(t("auth.password") + ": " + t("form.required"));
              setBusy(false);
              return;
            }
            try {
              await auth.login(em, password);
              nav("/tasks");
            } catch (e: any) {
              // Không render text server: dùng uiMessage trung tính.
              setErr(e?.uiMessage ?? t("error.generic"));
            } finally {
              setBusy(false);
            }
          }}
        >
          <label className="block">
            <span className="label">{t("auth.email")}</span>
            {/* Backend resolve username HOAC email; type=email se chan username
                (vi du tai khoan bootstrap "admin") ngay tren browser. */}
            <TextInput
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              type="text"
              placeholder="email hoặc username"
              autoComplete="username"
              required
            />
          </label>
          <label className="block">
            <span className="label">{t("auth.password")}</span>
            <TextInput value={password} onChange={(e) => setPassword(e.target.value)} type="password" required />
          </label>
          <ErrorAlert message={err} />
          <Button disabled={busy} type="submit">
            {t("auth.login")}
          </Button>
          <Link className="text-center text-sm font-medium text-blue-700 hover:underline" to="/register">
            {t("auth.no_account")} {t("auth.register")}
          </Link>
        </form>
      </Card>
    </div>
  );
}
