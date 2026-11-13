import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { RequireAuth, RequireAdmin } from "../App";

const { mockUseAuth, mockUseAccess } = vi.hoisted(() => ({
  mockUseAccess: vi.fn(()=>({systemAdmin:false,loading:false})),
  mockUseAuth: vi.fn((): { user: { role?: string; id?: string } | null } => ({ user: null })),
}));

vi.mock("../auth/AuthContext", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../auth/AuthContext")>();
  return {
    ...actual,
    useAuth: () => mockUseAuth(),
  };
});

vi.mock("../auth/AccessContext",()=>({useAccess:()=>mockUseAccess()}));

function renderWithRouter(ui: React.ReactElement, path = "/tasks") {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/login" element={<div>Login page</div>} />
        <Route path="/tasks" element={ui} />
      </Routes>
    </MemoryRouter>
  );
}

describe("RequireAuth", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("không token thì redirect /login", () => {
    renderWithRouter(
      <RequireAuth>
        <div>Secret</div>
      </RequireAuth>
    );
    expect(screen.getByText("Login page")).toBeInTheDocument();
    expect(screen.queryByText("Secret")).not.toBeInTheDocument();
  });

  it("có token thì cho vào", () => {
    localStorage.setItem("tm.access_token", "tok");
    renderWithRouter(
      <RequireAuth>
        <div>Secret</div>
      </RequireAuth>
    );
    expect(screen.getByText("Secret")).toBeInTheDocument();
  });
});

describe("RequireAdmin", () => {
  beforeEach(() => {
    mockUseAccess.mockReturnValue({systemAdmin:false,loading:false});
    localStorage.clear();
  });

  it("Workspace Admin không có global grant bị chặn", () => {
    mockUseAuth.mockReturnValue({ user: { role: "member" } });
    render(
      <MemoryRouter initialEntries={["/admin"]}>
        <Routes>
          <Route path="/tasks" element={<div>Tasks page</div>} />
          <Route
            path="/admin"
            element={
              <RequireAdmin>
                <div>Admin secret</div>
              </RequireAdmin>
            }
          />
        </Routes>
      </MemoryRouter>
    );
    expect(screen.getByRole("alert")).toHaveTextContent("không có quyền quản trị hệ thống");
    expect(screen.queryByText("Admin secret")).not.toBeInTheDocument();
  });

  it("quyền chưa xác định không được truy cập", () => {
    mockUseAuth.mockReturnValue({ user: { id: "u1" } });
    render(
      <MemoryRouter initialEntries={["/admin"]}>
        <Routes>
          <Route path="/tasks" element={<div>Tasks page</div>} />
          <Route
            path="/admin"
            element={
              <RequireAdmin>
                <div>Admin secret</div>
              </RequireAdmin>
            }
          />
        </Routes>
      </MemoryRouter>
    );
    expect(screen.queryByText("Admin secret")).not.toBeInTheDocument();
  });

  it("global SYSTEM_ADMIN grant thì cho vào", () => {
    mockUseAccess.mockReturnValue({systemAdmin:true,loading:false});
    mockUseAuth.mockReturnValue({ user: { role: "admin" } });
    render(
      <MemoryRouter initialEntries={["/admin"]}>
        <Routes>
          <Route path="/tasks" element={<div>Tasks page</div>} />
          <Route
            path="/admin"
            element={
              <RequireAdmin>
                <div>Admin secret</div>
              </RequireAdmin>
            }
          />
        </Routes>
      </MemoryRouter>
    );
    expect(screen.getByText("Admin secret")).toBeInTheDocument();
  });
});
