import type { ReactNode } from "react";
import { NavLink, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { t } from "../i18n";
import { RealtimeIndicator } from "../realtime/RealtimeContext";
import { ENABLE_AI } from "../config";
import ReauthModal from "./ReauthModal";

export default function Layout({ children }: { children: ReactNode }) {
  const auth = useAuth();
  const nav = useNavigate();
  return (
    <div className="layout flex min-h-screen bg-slate-100 text-slate-900">
      <nav className="side flex w-56 flex-col gap-1 bg-gradient-to-b from-blue-900 via-blue-950 to-slate-950 p-4 text-blue-50 shadow-lg">
        <div className="mb-3 px-3 pt-1 text-sm font-bold uppercase tracking-wider text-blue-200">
          {t("app.title")}
        </div>
        <NavLink to="/workspaces" className={({ isActive }) => `rounded-lg px-3 py-2 text-sm transition hover:bg-white/10 ${isActive ? "bg-white font-semibold text-blue-900 shadow" : "text-blue-50"}`}>{t("nav.workspaces")}</NavLink>
        <NavLink to="/projects" className={({ isActive }) => `rounded-lg px-3 py-2 text-sm transition hover:bg-white/10 ${isActive ? "bg-white font-semibold text-blue-900 shadow" : "text-blue-50"}`}>{t("nav.projects")}</NavLink>
        <NavLink to="/tasks" className={({ isActive }) => `rounded-lg px-3 py-2 text-sm transition hover:bg-white/10 ${isActive ? "bg-white font-semibold text-blue-900 shadow" : "text-blue-50"}`}>{t("nav.tasks")}</NavLink>
        <NavLink to="/calendar" className={({ isActive }) => `rounded-lg px-3 py-2 text-sm transition hover:bg-white/10 ${isActive ? "bg-white font-semibold text-blue-900 shadow" : "text-blue-50"}`}>{t("nav.calendar")}</NavLink>
        <NavLink to="/files" className={({ isActive }) => `rounded-lg px-3 py-2 text-sm transition hover:bg-white/10 ${isActive ? "bg-white font-semibold text-blue-900 shadow" : "text-blue-50"}`}>{t("nav.files")}</NavLink>
        <NavLink to="/notifications" className={({ isActive }) => `rounded-lg px-3 py-2 text-sm transition hover:bg-white/10 ${isActive ? "bg-white font-semibold text-blue-900 shadow" : "text-blue-50"}`}>{t("nav.notifications")}</NavLink>
        <NavLink to="/reports" className={({ isActive }) => `rounded-lg px-3 py-2 text-sm transition hover:bg-white/10 ${isActive ? "bg-white font-semibold text-blue-900 shadow" : "text-blue-50"}`}>{t("nav.reports")}</NavLink>
        {ENABLE_AI && (
          <NavLink to="/ai" className={({ isActive }) => `rounded-lg px-3 py-2 text-sm transition hover:bg-white/10 ${isActive ? "bg-white font-semibold text-blue-900 shadow" : "text-blue-50"}`}>{t("nav.ai")}</NavLink>
        )}
        <NavLink to="/admin" className={({ isActive }) => `rounded-lg px-3 py-2 text-sm transition hover:bg-white/10 ${isActive ? "bg-white font-semibold text-blue-900 shadow" : "text-blue-50"}`}>{t("nav.admin")}</NavLink>
        <div className="mt-2 px-3"><RealtimeIndicator /></div>
        <button
          className="mt-4 rounded-lg border border-white/30 px-3 py-2 text-sm text-white transition hover:bg-white/10"
          onClick={async () => {
            await auth.logout();
            nav("/login");
          }}
        >
          {t("auth.logout")}
        </button>
      </nav>
      <main className="flex-1 p-4">
        {auth.sessionNotice && (
          <div role="alert" className="notice mb-3 rounded-md border border-yellow-400 bg-yellow-50 p-2">
            {t(auth.sessionNotice)}
            <button className="ml-2 rounded border px-2 py-1 text-xs" onClick={auth.dismissNotice}>{t("common.close")}</button>
          </div>
        )}
        {children}
      </main>
      <ReauthModal
        open={!!auth.reauth}
        message={auth.reauth?.message ?? "auth.reauth_desc"}
        onConfirm={auth.confirmReauth}
        onCancel={auth.dismissReauth}
      />
    </div>
  );
}
