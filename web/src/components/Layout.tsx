import { useEffect, useState, type ReactNode } from "react";
import { NavLink, useLocation, useNavigate, Navigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { useTheme } from "./ThemeContext";
import { t } from "../i18n";
import { RealtimeIndicator } from "../realtime/RealtimeContext";
import { ENABLE_AI } from "../config";
import ReauthModal from "./ReauthModal";
import NotificationBell from "./NotificationBell";
import AppIcon from "./AppIcon";
import OfflineBanner from "./OfflineBanner";
import GlobalSearchModal from "./GlobalSearchModal";

export default function Layout({ children }: { children: ReactNode }) {
  const auth = useAuth();
  const nav = useNavigate();
  const location = useLocation();
  const { theme, toggleTheme } = useTheme();

  const [menuOpen, setMenuOpen] = useState(false);
  const [searchModalOpen, setSearchModalOpen] = useState(false);
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const [quickCreateOpen, setQuickCreateOpen] = useState(false);

  useEffect(() => {
    setMenuOpen(false);
    setUserMenuOpen(false);
    setQuickCreateOpen(false);
  }, [location.pathname]);

  // Global keyboard shortcuts (Ctrl+K for search, Escape to close)
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        setSearchModalOpen(true);
      }
      if (e.key === "Escape") {
        setMenuOpen(false);
        setSearchModalOpen(false);
        setUserMenuOpen(false);
        setQuickCreateOpen(false);
      }
    };
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, []);

  // Force change password redirect
  if (auth.user?.mustChangePassword && location.pathname !== "/change-password") {
    return <Navigate to="/change-password" replace />;
  }

  const role = auth.user?.role?.toUpperCase() || "";
  const isAdmin = role === "ADMIN" || role === "SYSTEM_ADMIN" || role === "OWNER";

  const titleByPath: Record<string, string> = {
    "/dashboard": "Tổng quan",
    "/workspaces": t("nav.workspaces"),
    "/projects": t("nav.projects"),
    "/tasks": t("nav.tasks"),
    "/my-tasks": "Việc của tôi",
    "/notes": "Ghi chú",
    "/teams": "Nhóm",
    "/roles": "Vai trò dự án",
    "/calendar": t("nav.calendar"),
    "/files": t("nav.files"),
    "/notifications": t("nav.notifications"),
    "/reports": t("nav.reports"),
    "/settings": "Cài đặt",
    "/ai": t("nav.ai"),
    "/admin": t("nav.admin"),
  };

  const title = location.pathname.startsWith("/tasks/")
    ? "Chi tiết công việc"
    : location.pathname.startsWith("/projects/")
    ? "Chi tiết dự án"
    : titleByPath[location.pathname] ?? t("app.title");

  const link = (to: string, label: string, icon: string) => (
    <NavLink
      to={to}
      title={label}
      className={({ isActive }) => `nav-link ${isActive ? "nav-link-active" : ""}`}
    >
      <AppIcon name={icon} />
      <span>{label}</span>
    </NavLink>
  );

  return (
    <div className="layout app-shell text-slate-900 dark:text-slate-100 bg-[#f2f5f7] dark:bg-[#0f1a22] transition-colors">
      <OfflineBanner />
      <a className="skip-link" href="#main-content">
        Đến nội dung chính
      </a>

      {menuOpen && (
        <button
          className="nav-scrim"
          aria-label="Đóng điều hướng"
          onClick={() => setMenuOpen(false)}
        />
      )}

      {/* Sidebar - 3 Tier Experience (Admin, Manager, Member) */}
      <nav
        id="main-navigation"
        aria-label="Điều hướng chính"
        className={`side app-sidebar ${menuOpen ? "is-open" : ""}`}
      >
        <NavLink to="/dashboard" className="brand">
          <span className="brand-mark">
            <AppIcon name="tasks" size={22} />
          </span>
          <span>
            SmartTask<span className="brand-caption">V3.0 Modern SaaS</span>
          </span>
        </NavLink>

        {/* NHÓM 1: THÀNH VIÊN (Member) */}
        <p className="nav-group-label">Thành viên</p>
        {link("/dashboard", "Tổng quan", "overview")}
        {link("/my-tasks", "Việc của tôi", "personal")}
        {link("/tasks", t("nav.tasks"), "tasks")}
        {link("/notes", "Ghi chú", "files")}
        {link("/calendar", t("nav.calendar"), "calendar")}
        {link("/files", t("nav.files"), "files")}

        {/* NHÓM 2: QUẢN LÝ (Manager) */}
        <p className="nav-group-label">Quản lý dự án</p>
        {link("/projects", t("nav.projects"), "project")}
        {link("/teams", "Nhóm", "team")}
        {link("/workspaces", t("nav.workspaces"), "workspace")}
        {link("/reports", t("nav.reports"), "report")}

        {/* NHÓM 3: QUẢN TRỊ (Admin) */}
        {isAdmin && (
          <>
            <p className="nav-group-label">Quản trị hệ thống</p>
            {link("/roles", "Vai trò dự án", "roles")}
            {link("/admin", t("nav.admin"), "settings")}
          </>
        )}

        {/* TIỆN ÍCH KHÁC */}
        <p className="nav-group-label">Hệ thống</p>
        {link("/notifications", t("nav.notifications"), "bell")}
        {link("/settings", "Cài đặt", "settings")}
        {ENABLE_AI && link("/ai", t("nav.ai"), "ai")}

        <div className="mt-auto border-t border-slate-700/50 pt-3">
          <RealtimeIndicator />
          <button
            className="btn btn-ghost mt-2 w-full justify-start text-xs text-slate-400 hover:text-white"
            onClick={async () => {
              await auth.logout();
              nav("/login");
            }}
          >
            <AppIcon name="logout" size={16} />
            <span>{t("auth.logout")}</span>
          </button>
        </div>
      </nav>

      {/* Main App Container */}
      <main className="app-main flex flex-col min-w-0">
        {/* Topbar */}
        <header className="app-header dark:bg-slate-900 dark:border-slate-800 transition-colors">
          <div className="header-location flex items-center gap-2">
            <button
              className="mobile-menu"
              aria-label={menuOpen ? "Đóng menu" : "Mở menu"}
              aria-expanded={menuOpen}
              aria-controls="main-navigation"
              onClick={() => setMenuOpen((v) => !v)}
            >
              <AppIcon name={menuOpen ? "close" : "menu"} />
            </button>
            <span className="header-home hidden sm:inline text-xs text-slate-400">
              Không gian làm việc
            </span>
            <span className="header-divider hidden sm:inline text-slate-300" aria-hidden="true">
              /
            </span>
            <span className="header-title font-bold text-sm text-slate-900 dark:text-white">
              {title}
            </span>
          </div>

          {/* Topbar Actions: Global Search, Quick Create, Theme Toggle, Notification, User Dropdown */}
          <div className="flex min-w-0 items-center gap-2.5">
            {/* Global Search Button */}
            <button
              onClick={() => setSearchModalOpen(true)}
              className="hidden md:flex items-center gap-2 px-3 py-1.5 rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 text-xs text-slate-500 hover:border-primary transition"
              title="Tìm kiếm nhanh (Ctrl+K)"
            >
              <AppIcon name="search" size={15} />
              <span>Tìm kiếm...</span>
              <kbd className="font-mono text-[10px] bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 px-1.5 py-0.5 rounded shadow-2xs">
                Ctrl K
              </kbd>
            </button>

            {/* Quick Create Menu */}
            <div className="relative">
              <button
                onClick={() => setQuickCreateOpen(!quickCreateOpen)}
                className="btn btn-primary !h-8 !px-2.5 text-xs flex items-center gap-1 shadow-xs"
                title="Tạo nhanh"
              >
                <AppIcon name="plus" size={14} />
                <span className="hidden sm:inline">Tạo mới</span>
              </button>

              {quickCreateOpen && (
                <div
                  className="absolute right-0 mt-2 w-48 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 shadow-xl py-1.5 z-40 text-xs text-slate-700 dark:text-slate-300 animate-scale-in"
                  onClick={() => setQuickCreateOpen(false)}
                >
                  <NavLink
                    to="/my-tasks"
                    className="flex items-center gap-2.5 px-3 py-2 hover:bg-slate-100 dark:hover:bg-slate-800"
                  >
                    <AppIcon name="tasks" size={16} />
                    <span>+ Việc cá nhân mới</span>
                  </NavLink>
                  <NavLink
                    to="/tasks"
                    className="flex items-center gap-2.5 px-3 py-2 hover:bg-slate-100 dark:hover:bg-slate-800"
                  >
                    <AppIcon name="tasks" size={16} />
                    <span>+ Việc dự án mới</span>
                  </NavLink>
                  <NavLink
                    to="/notes"
                    className="flex items-center gap-2.5 px-3 py-2 hover:bg-slate-100 dark:hover:bg-slate-800"
                  >
                    <AppIcon name="files" size={16} />
                    <span>+ Ghi chú mới</span>
                  </NavLink>
                  <NavLink
                    to="/projects"
                    className="flex items-center gap-2.5 px-3 py-2 hover:bg-slate-100 dark:hover:bg-slate-800 border-t border-slate-100 dark:border-slate-800"
                  >
                    <AppIcon name="project" size={16} />
                    <span>+ Dự án mới</span>
                  </NavLink>
                </div>
              )}
            </div>

            {/* Theme Toggle (Sun/Moon) */}
            <button
              onClick={toggleTheme}
              className="p-2 rounded-xl text-slate-500 hover:text-slate-900 dark:text-slate-400 dark:hover:text-white transition"
              title={theme === "dark" ? "Chuyển giao diện Sáng" : "Chuyển giao diện Tối"}
              aria-label="Đổi giao diện"
            >
              {theme === "dark" ? "☀️" : "🌙"}
            </button>

            {/* Notification Bell */}
            <NotificationBell />

            {/* User Avatar & Dropdown */}
            <div className="relative">
              <button
                onClick={() => setUserMenuOpen(!userMenuOpen)}
                className="flex items-center gap-2 p-1 rounded-xl hover:bg-slate-100 dark:hover:bg-slate-800 transition"
                aria-label="Tài khoản cá nhân"
              >
                <span className="avatar !h-8 !w-8 text-xs font-bold">
                  {(auth.user?.email ?? "U").slice(0, 1).toUpperCase()}
                </span>
              </button>

              {userMenuOpen && (
                <div
                  className="absolute right-0 mt-2 w-56 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 shadow-xl py-2 z-40 text-xs text-slate-700 dark:text-slate-300 animate-scale-in"
                  onClick={() => setUserMenuOpen(false)}
                >
                  <div className="px-3 py-2 border-b border-slate-100 dark:border-slate-800">
                    <p className="font-semibold text-slate-900 dark:text-white truncate">
                      {auth.user?.email ?? "Tài khoản"}
                    </p>
                    <p className="text-[11px] text-slate-400 capitalize">
                      Vai trò: {auth.user?.role ?? "Member"}
                    </p>
                  </div>

                  <NavLink
                    to="/settings"
                    className="flex items-center gap-2 px-3 py-2 hover:bg-slate-100 dark:hover:bg-slate-800"
                  >
                    <AppIcon name="settings" size={15} />
                    <span>Cài đặt hệ thống</span>
                  </NavLink>
                  <NavLink
                    to="/change-password"
                    className="flex items-center gap-2 px-3 py-2 hover:bg-slate-100 dark:hover:bg-slate-800"
                  >
                    <AppIcon name="settings" size={15} />
                    <span>Đổi mật khẩu</span>
                  </NavLink>

                  <div className="border-t border-slate-100 dark:border-slate-800 mt-1 pt-1">
                    <button
                      className="w-full text-left flex items-center gap-2 px-3 py-2 text-rose-600 hover:bg-rose-50 dark:hover:bg-rose-950/40"
                      onClick={async () => {
                        await auth.logout();
                        nav("/login");
                      }}
                    >
                      <AppIcon name="logout" size={15} />
                      <span>{t("auth.logout")}</span>
                    </button>
                  </div>
                </div>
              )}
            </div>
          </div>
        </header>

        {/* Page Content */}
        <div id="main-content" tabIndex={-1} className="app-content flex-1">
          {auth.sessionNotice && (
            <div
              role="alert"
              className="notice mb-4 rounded-xl border border-amber-300 bg-amber-50 dark:bg-amber-950/30 dark:border-amber-800 p-3 text-xs"
            >
              {t(auth.sessionNotice)}
              <button
                className="ml-3 font-semibold underline text-amber-900 dark:text-amber-200"
                onClick={auth.dismissNotice}
              >
                {t("common.close")}
              </button>
            </div>
          )}
          {children}
        </div>
      </main>

      {/* Global Search Modal */}
      <GlobalSearchModal
        open={searchModalOpen}
        onClose={() => setSearchModalOpen(false)}
      />

      {/* Reauth Modal */}
      <ReauthModal
        open={!!auth.reauth}
        message={auth.reauth?.message ?? "auth.reauth_desc"}
        onConfirm={auth.confirmReauth}
        onCancel={auth.dismissReauth}
      />
    </div>
  );
}
