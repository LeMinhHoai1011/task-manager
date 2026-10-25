import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import Tasks from "../pages/Tasks";

const { mockApiFetch } = vi.hoisted(() => ({ mockApiFetch: vi.fn() }));

vi.mock("../api/client", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../api/client")>();
  return {
    ...actual,
    apiFetch: mockApiFetch,
  };
});

function renderTasks() {
  return render(
    <MemoryRouter initialEntries={["/tasks"]}>
      <Tasks />
    </MemoryRouter>
  );
}

const emptyPage = { data: { items: [], next_cursor: null, has_more: false, total: 0 } };

describe("Tasks", () => {
  beforeEach(() => {
    mockApiFetch.mockReset();
    mockApiFetch.mockResolvedValue(emptyPage);
    localStorage.clear();
  });

  it("hiển thị empty state khi không có task", async () => {
    renderTasks();
    expect(await screen.findByText("Chưa có công việc")).toBeInTheDocument();
    expect(screen.getByText("Thử đổi bộ lọc hoặc tạo việc mới.")).toBeInTheDocument();
  });

  it("đọc envelope canonical data.items", async () => {
    mockApiFetch.mockResolvedValue({
      data: {
        items: [{ id: "t1", title: "Việc A", state: "TODO" }],
        next_cursor: null,
        has_more: false,
      },
    });
    renderTasks();
    expect(await screen.findByText("Việc A")).toBeInTheDocument();
  });

  it("fallback legacy data.data (alias tạm backend)", async () => {
    mockApiFetch.mockResolvedValue({
      data: { data: [{ id: "t2", title: "Việc legacy", state: "TODO" }], next_cursor: null },
    });
    renderTasks();
    expect(await screen.findByText("Việc legacy")).toBeInTheDocument();
  });

  it("đổi filter state thì refetch kèm query state", async () => {
    renderTasks();
    await screen.findByText("Chưa có công việc");
    const sel = screen.getByLabelText("Trạng thái") as HTMLSelectElement;
    fireEvent.change(sel, { target: { value: "TODO" } });
    await waitFor(() => {
      const calls = mockApiFetch.mock.calls.filter((c) => c[0] === "/tasks");
      expect(calls.length).toBeGreaterThan(0);
      const last = calls[calls.length - 1][1];
      expect(last.query.state).toBe("TODO");
      expect(last.query.sort).toBe("created_at");
      expect(last.query.order).toBe("desc");
    });
  });
});
