import "./styles.css";
import { BrowserRouter, Navigate, Route, Routes, useLocation } from "react-router-dom";
import type { ReactNode } from "react";
import { AuthProvider, useAuth } from "./auth/AuthContext";
import { getToken } from "./api/client";
import Layout from "./components/Layout";
import Login from "./pages/Login";
import Register from "./pages/Register";
import Workspaces from "./pages/Workspaces";
import Dashboard from "./pages/Dashboard";
import Projects from "./pages/Projects";
import ProjectDetail from "./pages/ProjectDetail";
import Tasks from "./pages/Tasks";
import MyTasks from "./pages/MyTasks";
import Notes from "./pages/Notes";
import Teams from "./pages/Teams";
import ProjectRoles from "./pages/ProjectRoles";
import TaskDetail from "./pages/TaskDetail";
import Files from "./pages/Files";
import Calendar from "./pages/Calendar";
import Notifications from "./pages/Notifications";
import Reports from "./pages/Reports";
import Settings from "./pages/Settings";
import Admin from "./pages/Admin";
import Ai from "./pages/Ai";
import ChangePassword from "./pages/ChangePassword";
import { ENABLE_AI } from "./config";
import { RealtimeProvider } from "./realtime/RealtimeContext";
import { ThemeProvider } from "./components/ThemeContext";

export default function App() {
  return (
    <ThemeProvider>
    <AuthProvider>
      <RealtimeProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<Login />} />
            <Route path="/register" element={<Register />} />
            <Route
              path="/*"
              element={
                <RequireAuth>
                  <Layout>
                    <Routes>
                      <Route path="/dashboard" element={<Dashboard />} />
                      <Route path="/workspaces" element={<Workspaces />} />
                      <Route path="/projects" element={<Projects />} />
                      <Route path="/projects/:id" element={<ProjectDetail />} />
                      <Route path="/tasks" element={<Tasks />} />
                      <Route path="/my-tasks" element={<MyTasks />} />
                      <Route path="/notes" element={<Notes />} />
                      <Route path="/teams" element={<Teams />} />
                      <Route path="/roles" element={<ProjectRoles />} />
                      <Route path="/tasks/:id" element={<TaskDetail />} />
                      <Route path="/files" element={<Files />} />
                      <Route path="/calendar" element={<Calendar />} />
                      <Route path="/notifications" element={<Notifications />} />
                      <Route path="/reports" element={<Reports />} />
                      <Route path="/settings" element={<Settings />} />
                      <Route path="/change-password" element={<ChangePassword />} />
                      <Route path="/ai" element={ENABLE_AI ? <Ai /> : <Navigate to="/tasks" replace />} />
                      <Route path="/admin" element={<RequireAdmin><Admin /></RequireAdmin>} />
                      <Route path="*" element={<Navigate to="/dashboard" replace />} />
                    </Routes>
                  </Layout>
                </RequireAuth>
              }
            />
          </Routes>
        </BrowserRouter>
      </RealtimeProvider>
    </AuthProvider>
    </ThemeProvider>
  );
}

/** Mọi route nghiệp vụ yêu cầu phiên đăng nhập; backend vẫn kiểm tra lại mọi request. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const loc = useLocation();
  if (!getToken()) return <Navigate to="/login" replace state={{ from: loc.pathname }} />;
  return <>{children}</>;
}

/** Route admin: backend không có endpoint profile/role nên web không biết role chắc chắn.
 * Chỉ redirect khi role đã rõ là non-admin; role unknown thì cho vào và để backend
 * trả 403 trung tính (backend mới là nguồn sự thật). */
export function RequireAdmin({ children }: { children: ReactNode }) {
  const role = useAuth().user?.role;
  if (role !== undefined && role !== null && role !== "" && !isAdminRole(role)) {
    return <Navigate to="/tasks" replace />;
  }
  return <>{children}</>;
}

function isAdminRole(role?: string): boolean {
  return role === "admin" || role === "system_admin" || role === "ADMIN" || role === "SYSTEM_ADMIN";
}

export { isAdminRole };
