import { useCallback, useEffect, useState } from "react";
import { apiFetch, mutationOpts, newIdempotencyKey, unwrapData, unwrapList } from "../api/client";
import { t } from "../i18n";
import { Button, Card, EmptyState, ErrorAlert, Spinner } from "../components/ui";

interface Account {
  id?: string;
  user_id?: string;
  username?: string;
  email?: string;
  status?: string;
  locked?: boolean;
  locked_until?: string | null;
}

interface Setting {
  key?: string;
  value?: string;
  [k: string]: unknown;
}

interface AuditRow {
  id: string;
  actor_user_id?: string;
  action?: string;
  outcome?: string;
  reason?: string;
}

/**
 * Admin duyệt tài khoản qua endpoint canonical §7 GET /accounts (+approve/reject).
 * Giữ tương thích legacy /admin/accounts khi backend cũ chưa có /accounts.
 */
async function fetchAccounts(): Promise<Account[]> {
  try {
    const r = await apiFetch("/accounts");
    return unwrapList<Account>(r);
  } catch (e: any) {
    // Fallback legacy cho backend chưa có /accounts (giữ tương thích, không bịa path khác).
    if (e?.status === 404) {
      const r = await apiFetch("/admin/accounts", { query: { status: "pending" } });
      return unwrapList<Account>(r);
    }
    throw e;
  }
}

async function decideAccount(id: string, approve: boolean): Promise<void> {
  const body = {};
  try {
    await apiFetch(`/accounts/${id}/${approve ? "approve" : "reject"}`, {
      ...mutationOpts({ idempotencyKey: newIdempotencyKey() }),
      method: "POST",
      body,
    });
  } catch (e: any) {
    if (e?.status === 404) {
      await apiFetch(`/admin/accounts/${id}/${approve ? "approve" : "reject"}`, {
        ...mutationOpts({ idempotencyKey: newIdempotencyKey() }),
        method: "POST",
        body,
      });
      return;
    }
    throw e;
  }
}

export default function Admin() {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [settings, setSettings] = useState<Setting[]>([]);
  const [audit, setAudit] = useState<AuditRow[]>([]);
  const [err, setErr] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [notice, setNotice] = useState<string | null>(null);
  const [pending, setPending] = useState<{ id: string; approve: boolean } | null>(null);

  const load = useCallback(async () => {
    setErr(null);
    setLoading(true);
    try {
      setAccounts(await fetchAccounts());
    } catch (e: any) {
      setErr(e?.uiMessage ?? t("error.generic"));
    } finally {
      setLoading(false);
    }
    apiFetch("/admin/settings")
      .then((r) => {
        const d = unwrapData<unknown>(r);
        setSettings(Array.isArray(d) ? (d as Setting[]) : unwrapList<Setting>(r));
      })
      .catch(() => setSettings([]));
    // Audit scoped: chỉ fetch theo scope được phép; payload đã redacted phía server.
    apiFetch("/admin/audit")
      .then((r) => setAudit(unwrapList<AuditRow>(r)))
      .catch(() => setAudit([]));
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  async function confirmPending() {
    if (!pending) return;
    setErr(null);
    try {
      await decideAccount(pending.id, pending.approve);
      setPending(null);
      setNotice(t("admin.done"));
      setAccounts(await fetchAccounts());
    } catch (e: any) {
      setErr(e?.uiMessage ?? t("error.generic"));
      setPending(null);
    }
  }

  async function lockAccount(id: string, lockedUntilMinutes?: number) {
    try {
      await apiFetch(`/accounts/${id}/lock`, {
        ...mutationOpts({ idempotencyKey: newIdempotencyKey() }),
        method: "POST",
        body: lockedUntilMinutes ? { locked_until_minutes: lockedUntilMinutes } : {},
      });
      setNotice(t("admin.locked"));
      setAccounts(await fetchAccounts());
    } catch (e: any) {
      setErr(e?.uiMessage ?? t("error.generic"));
    }
  }

  async function unlockAccount(id: string) {
    try {
      await apiFetch(`/accounts/${id}/unlock`, {
        ...mutationOpts({ idempotencyKey: newIdempotencyKey() }),
        method: "POST",
      });
      setNotice(t("admin.unlocked"));
      setAccounts(await fetchAccounts());
    } catch (e: any) {
      setErr(e?.uiMessage ?? t("error.generic"));
    }
  }

  async function resetPassword(id: string) {
    if (!window.confirm(t("admin.confirm_reset_password"))) return;
    try {
      await apiFetch(`/accounts/${id}/reset-password`, {
        ...mutationOpts({ idempotencyKey: newIdempotencyKey() }),
        method: "POST",
      });
      setNotice(t("admin.password_reset"));
      setAccounts(await fetchAccounts());
    } catch (e: any) {
      setErr(e?.uiMessage ?? t("error.generic"));
    }
  }

  return (
    <div className="mx-auto max-w-6xl">
      <h1 className="page-title">{t("nav.admin")}</h1>
      <ErrorAlert message={err} />
      {notice && <p role="status" className="mt-2 rounded-lg border border-green-200 bg-green-50 px-3 py-2 text-sm text-green-800">{notice}</p>}
      <Card className="mt-3">
        <h2 className="text-sm font-semibold text-slate-900">{t("admin.accounts")}</h2>
        {loading ? (
          <div className="mt-2"><Spinner /></div>
        ) : accounts.length === 0 ? (
          <div className="mt-2"><EmptyState title={t("admin.no_pending")} /></div>
        ) : (
          <table className="table mt-2">
            <thead><tr><th>{t("admin.username")}</th><th>{t("admin.email")}</th><th>{t("common.status")}</th><th className="text-right">{t("common.actions")}</th></tr></thead>
            <tbody>
              {accounts.map((a) => {
                const aid = a.user_id ?? a.id ?? "";
                return (
                <tr key={aid}>
                  <td>{a.username ?? aid}</td>
                  <td>{a.email ?? "—"}</td>
                  <td>{a.status ?? "—"}</td>
                  <td>
                    <div className="flex justify-end gap-1">
                      {a.locked ? (
                        <Button variant="secondary" size="sm" onClick={() => unlockAccount(aid)}>
                          {t("admin.unlock")}
                        </Button>
                      ) : (
                        <>
                          <Button variant="warning" size="sm" onClick={() => lockAccount(aid)}>
                            {t("admin.lock")}
                          </Button>
                          <Button variant="danger" size="sm" onClick={() => resetPassword(aid)}>
                            {t("admin.reset_password")}
                          </Button>
                        </>
                      )}
                      <Button variant="success" size="sm" onClick={() => setPending({ id: aid, approve: true })}>
                        {t("admin.approve")}
                      </Button>
                      <Button variant="danger" size="sm" onClick={() => setPending({ id: aid, approve: false })}>
                        {t("admin.reject")}
                      </Button>
                    </div>
                  </td>
                </tr>
                );
              })}
            </tbody>
          </table>
        )}
        {pending && (
          <div role="dialog" aria-modal="true" aria-label={t("common.confirm")} className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
            <div className="w-full max-w-sm rounded-xl bg-white p-6 shadow-lg">
              <p className="text-sm text-slate-800">
                {pending.approve ? t("admin.confirm_approve") : t("admin.confirm_reject")}
              </p>
              <p className="mt-1 font-mono text-xs text-slate-500">{pending.id}</p>
              <div className="mt-4 flex justify-end gap-2">
                <Button variant="primary" size="sm" onClick={() => void confirmPending()}>
                  {t("common.confirm")}
                </Button>
                <Button variant="secondary" size="sm" onClick={() => setPending(null)}>
                  {t("common.cancel")}
                </Button>
              </div>
            </div>
          </div>
        )}
      </Card>
      <Card className="mt-3">
        <h2 className="text-sm font-semibold text-slate-900">{t("admin.settings")}</h2>
        {settings.length === 0 ? (
          <p className="mt-2 text-sm text-slate-400">—</p>
        ) : (
          <table className="table mt-2">
            <thead><tr><th>Key</th><th>Value</th></tr></thead>
            <tbody>
              {settings.map((s, i) => (
                <tr key={String(s.key ?? i)}>
                  <td className="font-mono text-xs">{String(s.key ?? Object.keys(s)[0] ?? "?")}</td>
                  <td className="font-mono text-xs">{String(s.value ?? Object.values(s)[1] ?? "—")}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>
      <Card className="mt-3">
        <h2 className="text-sm font-semibold text-slate-900">{t("admin.audit")}</h2>
        {audit.length === 0 ? (
          <div className="mt-2"><EmptyState title={t("admin.no_audit")} /></div>
        ) : (
          <table className="table mt-2">
            <thead><tr><th>{t("admin.actor")}</th><th>{t("admin.action")}</th><th>{t("admin.outcome")}</th></tr></thead>
            <tbody>
              {audit.map((a) => (
                <tr key={a.id}>
                  <td className="font-mono text-xs">{a.actor_user_id ?? "—"}</td>
                  <td>{a.action ?? a.id}</td>
                  <td>{a.outcome ?? a.reason ?? "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>
    </div>
  );
}
