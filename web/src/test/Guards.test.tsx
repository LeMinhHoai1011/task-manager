import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { RequireAuth, RequireAdmin, isAdminRole } from "../App";

const { mockUseAuth } = vi.hoisted(() => ({
  mockUseAuth: vi.fn((): { user: { role?: string; id?: string } | null } => ({ user: null })),
}));

vi.mock("../auth/AuthContext", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../auth/AuthContext")>();
  return {
    ...actual,
    useAuth: () => mockUseAuth(),
  };
});

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

describe("isAdminRole", () => {
  it("nhận diện admin/system_admin (mọi case)", () => {
    expect(isAdminRole("admin")).toBe(true);
    expect(isAdminRole("SYSTEM_ADMIN")).toBe(true);
    expect(isAdminRole("user")).toBe(false);
    expect(isAdminRole(undefined)).toBe(false);
  });
});

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
    localStorage.clear();
  });

  it("role non-admin đã rõ thì redirect về /tasks", () => {
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
    expect(screen.getByText("Tasks page")).toBeInTheDocument();
    expect(screen.queryByText("Admin secret")).not.toBeInTheDocument();
  });

  it("role unknown (backend không có /me) thì cho vào, để backend 403", () => {
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
    expect(screen.getByText("Admin secret")).toBeInTheDocument();
  });

  it("role admin thì cho vào", () => {
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
