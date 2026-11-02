import type { ReactNode } from "react";
import { NavLink, useLocation, useNavigate, Navigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { t } from "../i18n";
import { RealtimeIndicator } from "../realtime/RealtimeContext";
import { ENABLE_AI } from "../config";
import ReauthModal from "./ReauthModal";
import NotificationBell from "./NotificationBell";

export default function Layout({ children }: { children: ReactNode }) {
  const auth = useAuth();
  const nav = useNavigate();
  const location = useLocation();

  // Nếu tài khoản bắt buộc đổi mật khẩu (sau khi admin reset), chuyển hướng đến
  // trang đổi mật khẩu (trừ khi đang ở trang đó hoặc đăng nhập).
  if (auth.user?.mustChangePassword && location.pathname !== "/change-password") {
    return <Navigate to="/change-password" replace />;
  }

  const titleByPath: Record<string, string> = { "/dashboard": "Tổng quan", "/workspaces": t("nav.workspaces"), "/projects": t("nav.projects"), "/tasks": t("nav.tasks"), "/calendar": t("nav.calendar"), "/files": t("nav.files"), "/notifications": t("nav.notifications"), "/reports": t("nav.reports"), "/ai": t("nav.ai"), "/admin": t("nav.admin") };
  const title = location.pathname.startsWith("/tasks/") ? "Chi tiết công việc" : titleByPath[location.pathname] ?? t("app.title");
  const link = (to: string, label: string, icon: string) => <NavLink to={to} className={({ isActive }) => `nav-link ${isActive ? "nav-link-active" : ""}`}><span aria-hidden>{icon}</span><span>{label}</span></NavLink>;
  return (
    <div className="layout app-shell text-slate-900">
      <nav aria-label="Điều hướng chính" className="side app-sidebar">
        <NavLink to="/dashboard" className="brand"><span className="brand-mark">S</span><span>SmartTask</span></NavLink>
        <p className="nav-group-label">KHÔNG GIAN LÀM VIỆC</p>
        {link("/dashboard", "Tổng quan", "⌂")}
        {link("/my-tasks", "Việc của tôi", "✓")}
        {link("/teams", "Nhóm", "♧")}
        {link("/roles", "Vai trò dự án", "⚿")}
        {link("/workspaces", t("nav.workspaces"), "◫")}
        {link("/projects", t("nav.projects"), "▣")}
        {link("/tasks", t("nav.tasks"), "✓")}
        {link("/calendar", t("nav.calendar"), "□")}
        <p className="nav-group-label mt-4">THEO DÕI</p>
        {link("/files", t("nav.files"), "▤")}
        {link("/notifications", t("nav.notifications"), "●")}
        {link("/reports", t("nav.reports"), "▥")}
        {ENABLE_AI && (
          link("/ai", t("nav.ai"), "✦")
        )}
        {link("/admin", t("nav.admin"), "⚙")}
        <div className="mt-auto border-t border-slate-200 pt-4"><RealtimeIndicator /><button
          className="btn btn-ghost mt-3 w-full justify-start"
          onClick={async () => {
            await auth.logout();
            nav("/login");
          }}
        >
          {t("auth.logout")}
        </button></div>
      </nav>
      <main className="app-main"><header className="app-header"><div><p className="text-xs font-medium text-slate-500">SMARTTASK / {title.toUpperCase()}</p><h1 className="sr-only">{title}</h1></div><div className="flex items-center gap-3"><NotificationBell /><span className="hidden text-sm text-slate-500 sm:inline">{auth.user?.email ?? ""}</span><span className="avatar" aria-label="Tài khoản">{(auth.user?.email ?? "U").slice(0, 1).toUpperCase()}</span></div></header><div className="app-content">
        {auth.sessionNotice && (
          <div role="alert" className="notice mb-3 rounded-md border border-yellow-400 bg-yellow-50 p-2">
            {t(auth.sessionNotice)}
            <button className="ml-2 rounded border px-2 py-1 text-xs" onClick={auth.dismissNotice}>{t("common.close")}</button>
          </div>
        )}
        {children}
      </div></main>
      <ReauthModal
        open={!!auth.reauth}
        message={auth.reauth?.message ?? "auth.reauth_desc"}
        onConfirm={auth.confirmReauth}
        onCancel={auth.dismissReauth}
      />
    </div>
  );
}
