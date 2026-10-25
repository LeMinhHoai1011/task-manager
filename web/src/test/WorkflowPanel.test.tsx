import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AuthProvider } from "../auth/AuthContext";
import WorkflowPanel from "../pages/WorkflowPanel";

const { mockApiFetch } = vi.hoisted(() => ({ mockApiFetch: vi.fn() }));

vi.mock("../api/client", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../api/client")>();
  return {
    ...actual,
    apiFetch: mockApiFetch,
    newIdempotencyKey: () => "test-idem",
  };
});

function renderPanel() {
  return render(
    <AuthProvider>
      <WorkflowPanel taskId="task-1" version="3" />
    </AuthProvider>
  );
}

describe("WorkflowPanel", () => {
  beforeEach(() => {
    mockApiFetch.mockReset();
    mockApiFetch.mockResolvedValue({ data: {} });
    localStorage.clear();
  });

  it("reject không reason thì chặn với lỗi reason>=3, không gọi API", async () => {
    renderPanel();
    fireEvent.click(screen.getByRole("button", { name: "reject" }));
    expect(await screen.findByText("Lý do phải từ 3 ký tự trở lên.")).toBeInTheDocument();
    expect(mockApiFetch).not.toHaveBeenCalled();
  });

  it("reject reason 2 ký tự vẫn chặn", async () => {
    renderPanel();
    fireEvent.change(screen.getByLabelText("reason"), { target: { value: "ab" } });
    fireEvent.click(screen.getByRole("button", { name: "reject" }));
    expect(await screen.findByText("Lý do phải từ 3 ký tự trở lên.")).toBeInTheDocument();
    expect(mockApiFetch).not.toHaveBeenCalled();
  });

  it("reject reason>=3 thì gọi API kèm If-Match + Idempotency-Key", async () => {
    renderPanel();
    fireEvent.change(screen.getByLabelText("reason"), { target: { value: "đủ dài" } });
    fireEvent.click(screen.getByRole("button", { name: "reject" }));
    await waitFor(() => expect(mockApiFetch).toHaveBeenCalledTimes(1));
    const [path, opts] = mockApiFetch.mock.calls[0];
    expect(path).toBe("/tasks/task-1/workflow/reject");
    expect(opts.ifMatch).toBe("3");
    expect(opts.idempotencyKey).toBeTruthy();
  });
});
