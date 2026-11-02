import { useEffect, useState, type ReactNode } from "react";
import { NavLink, useLocation, useNavigate, Navigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { t } from "../i18n";
import { RealtimeIndicator } from "../realtime/RealtimeContext";
import { ENABLE_AI } from "../config";
import ReauthModal from "./ReauthModal";
import NotificationBell from "./NotificationBell";
import AppIcon from "./AppIcon";

export default function Layout({ children }: { children: ReactNode }) {
  const auth = useAuth();
  const nav = useNavigate();
  const location = useLocation();
  const [menuOpen, setMenuOpen] = useState(false);
  useEffect(() => { setMenuOpen(false); }, [location.pathname]);
  useEffect(() => {
    if (!menuOpen) return;
    const close = (event: KeyboardEvent) => { if (event.key === "Escape") setMenuOpen(false); };
    document.addEventListener("keydown", close);
    return () => document.removeEventListener("keydown", close);
  }, [menuOpen]);

  // Nếu tài khoản bắt buộc đổi mật khẩu (sau khi admin reset), chuyển hướng đến
  // trang đổi mật khẩu (trừ khi đang ở trang đó hoặc đăng nhập).
  if (auth.user?.mustChangePassword && location.pathname !== "/change-password") {
    return <Navigate to="/change-password" replace />;
  }

  const titleByPath: Record<string, string> = { "/dashboard": "Tổng quan", "/workspaces": t("nav.workspaces"), "/projects": t("nav.projects"), "/tasks": t("nav.tasks"), "/calendar": t("nav.calendar"), "/files": t("nav.files"), "/notifications": t("nav.notifications"), "/reports": t("nav.reports"), "/ai": t("nav.ai"), "/admin": t("nav.admin") };
  Object.assign(titleByPath, {"/my-tasks":"Việc của tôi","/teams":"Nhóm","/roles":"Vai trò dự án"});
  const title = location.pathname.startsWith("/tasks/") ? "Chi tiết công việc" : titleByPath[location.pathname] ?? t("app.title");
  const link = (to: string, label: string, icon: string) => <NavLink to={to} title={label} className={({ isActive }) => `nav-link ${isActive ? "nav-link-active" : ""}`}><AppIcon name={icon}/><span>{label}</span></NavLink>;
  return (
    <div className="layout app-shell text-slate-900">
      <a className="skip-link" href="#main-content">Đến nội dung chính</a>
      {menuOpen && <button className="nav-scrim" aria-label="Đóng điều hướng" onClick={()=>setMenuOpen(false)}/>}
      <nav id="main-navigation" aria-label="Điều hướng chính" className={`side app-sidebar ${menuOpen ? "is-open" : ""}`}>
        <NavLink to="/dashboard" className="brand"><span className="brand-mark"><AppIcon name="tasks" size={22}/></span><span>SmartTask<span className="brand-caption">Không gian làm việc</span></span></NavLink>
        <p className="nav-group-label">Công việc</p>
        {link("/dashboard", "Tổng quan", "overview")}
        {link("/my-tasks", "Việc của tôi", "personal")}
        {link("/tasks", t("nav.tasks"), "tasks")}
        {link("/calendar", t("nav.calendar"), "calendar")}
        <p className="nav-group-label">Cộng tác</p>
        {link("/workspaces", t("nav.workspaces"), "workspace")}
        {link("/projects", t("nav.projects"), "project")}
        {link("/teams", "Nhóm", "team")}
        {link("/roles", "Vai trò dự án", "roles")}
        <p className="nav-group-label">Theo dõi</p>
        {link("/files", t("nav.files"), "files")}
        {link("/notifications", t("nav.notifications"), "bell")}
        {link("/reports", t("nav.reports"), "report")}
        {ENABLE_AI && (
          link("/ai", t("nav.ai"), "ai")
        )}
        {link("/admin", t("nav.admin"), "settings")}
        <div className="mt-auto border-t border-slate-200 pt-4"><RealtimeIndicator /><button
          className="btn btn-ghost mt-3 w-full justify-start"
          onClick={async () => {
            await auth.logout();
            nav("/login");
          }}
        >
          <AppIcon name="logout" size={18}/>{t("auth.logout")}
        </button></div>
      </nav>
      <main className="app-main"><header className="app-header"><div className="header-location"><button className="mobile-menu" aria-label={menuOpen?"Đóng menu":"Mở menu"} aria-expanded={menuOpen} aria-controls="main-navigation" onClick={()=>setMenuOpen(v=>!v)}><AppIcon name={menuOpen?"close":"menu"}/></button><span className="header-home">Không gian làm việc</span><span className="header-divider" aria-hidden="true">/</span><span className="header-title">{title}</span></div><div className="flex min-w-0 items-center gap-3"><NotificationBell /><span className="account-name hidden text-sm sm:inline">{auth.user?.email ?? ""}</span><span className="avatar" aria-label="Tài khoản">{(auth.user?.email ?? "U").slice(0, 1).toUpperCase()}</span></div></header><div id="main-content" tabIndex={-1} className="app-content">
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
