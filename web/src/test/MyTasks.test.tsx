import { render, screen, fireEvent, waitFor, act } from "@testing-library/react";
import { vi, test, expect } from "vitest";
import MyTasks from "../pages/MyTasks";
const { fetchMock } = vi.hoisted(() => ({ fetchMock: vi.fn() }));
vi.mock("../api/client", () => ({ apiFetch: fetchMock }));

test("edits private notes using the task version", async () => {
  fetchMock.mockReset();
  fetchMock.mockResolvedValue({ data: { content: [{ id: "private-1", title: "My task", state: "TODO", version: 4, priority: "MEDIUM" }], last: true } });
  render(<MyTasks />);
  fireEvent.click(await screen.findByText("Sửa chi tiết"));
  fireEvent.change(screen.getByLabelText("Ghi chú"), { target: { value: "Private notes" } });
  fireEvent.click(screen.getByText("Lưu"));
  await waitFor(() => expect(fetchMock).toHaveBeenCalledWith("/me/tasks/private-1", expect.objectContaining({ method: "PATCH", ifMatch: "4", body: expect.objectContaining({ notes: "Private notes" }) })));
});

test("late response cannot replace the selected status filter", async () => {
  fetchMock.mockReset();
  let resolveOld!: (value: unknown) => void;
  const old = new Promise(resolve => { resolveOld = resolve; });
  fetchMock.mockImplementation((path: string, opts: any) => {
    if (path === "/me/tasks/summary") return Promise.resolve({ data: { total: 1 } });
    if (opts?.query?.state === "COMPLETED") return Promise.resolve({ data: { content: [{ id: "new", title: "Completed result", state: "COMPLETED", version: 1 }], last: true } });
    return old;
  });
  const view = render(<MyTasks />);
  fireEvent.change(screen.getByLabelText("Trạng thái"), { target: { value: "COMPLETED" } });
  await screen.findByText("Completed result", { selector: "span" });
  await act(async () => {
    resolveOld({ data: { content: [{ id: "old", title: "Stale result", state: "TODO", version: 1 }], last: true } });
    await old;
  });
  await waitFor(() => expect(screen.queryByText("Stale result")).toBeNull());
  expect(screen.getByText("Completed result", { selector: "span" })).toBeTruthy();
  view.unmount();
});
