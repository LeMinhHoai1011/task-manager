import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { expect, test, vi } from "vitest";
import Notifications from "../pages/Notifications";

const { request } = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock("../api/client", async (original) => ({
  ...await original<typeof import("../api/client")>(), apiFetch: request,
}));
vi.mock("../realtime/RealtimeContext", () => ({
  useRealtimeEvent: () => {}, RealtimeIndicator: () => null,
}));

test("filters on the server and ignores an earlier inbox response", async () => {
  let resolveOld!: (value: unknown) => void;
  request.mockImplementation((path: string, options: any) => {
    if (path === "/reminders/preferences") return Promise.resolve({ data: {} });
    if (options?.query?.filter === "all") return new Promise(resolve => { resolveOld = resolve; });
    return Promise.resolve({ data: { items: [{ id: "new", kind: "TASK_ASSIGNED" }], next_cursor: null } });
  });
  render(<MemoryRouter><Notifications /></MemoryRouter>);
  await waitFor(() => expect(resolveOld).toBeTypeOf("function"));
  fireEvent.click(screen.getByRole("button", { name: "Công việc" }));
  await screen.findByText("Bạn được giao công việc");
  expect(request).toHaveBeenCalledWith("/notifications", expect.objectContaining({ query: expect.objectContaining({ filter: "task" }) }));
  await act(async () => {
    resolveOld({ data: { items: [{ id: "old", kind: "OLD_SYSTEM_MESSAGE" }], next_cursor: "old" } });
  });
  expect(screen.queryByText("OLD_SYSTEM_MESSAGE")).not.toBeInTheDocument();
  expect(screen.getByText("Bạn được giao công việc")).toBeInTheDocument();
});
