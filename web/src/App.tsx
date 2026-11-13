import "./styles.css";
import { BrowserRouter, Navigate, Route, Routes, useLocation } from "react-router-dom";
import type { ReactNode } from "react";
import { AuthProvider } from "./auth/AuthContext";
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
import {AccessProvider,useAccess} from "./auth/AccessContext";
import RoleGuide from "./pages/RoleGuide";
import { ENABLE_AI } from "./config";
import { RealtimeProvider } from "./realtime/RealtimeContext";
import { ThemeProvider } from "./components/ThemeContext";

export default function App() {
  return (
    <ThemeProvider>
    <AuthProvider>
      <RealtimeProvider>
        <AccessProvider>
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
                      <Route path="/my-role" element={<RoleGuide />} />
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
        </AccessProvider>
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

/** System administration requires a live global grant, separate from scoped roles. */
export function RequireAdmin({ children }: { children: ReactNode }) {
  const access=useAccess();
  if(access.loading)return <p role="status">Đang kiểm tra quyền hệ thống…</p>;
  if(!access.systemAdmin)return <p role="alert">Bạn không có quyền quản trị hệ thống. Owner/Admin Workspace chỉ quản lý phạm vi Workspace được giao.</p>;
  return <>{children}</>;
}
