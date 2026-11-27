import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter, useLocation } from "react-router-dom";
import { afterEach, expect, test, vi } from "vitest";
import GlobalSearchModal from "../components/GlobalSearchModal";

const { request } = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock("../api/client", () => ({ apiFetch: request }));
afterEach(() => {
  cleanup();
  request.mockReset();
});

function Location() {
  return <output>{useLocation().pathname}</output>;
}

test("searches projects on the server and opens the matching project", async () => {
  request.mockImplementation(async (path: string) => path === "/projects"
    ? { data: { items: [{ id: "p1", name: "Dự án cần tìm" }] } }
    : { data: path === "/notes" ? [] : { items: [] } });
  render(<MemoryRouter><GlobalSearchModal open onClose={() => {}} /><Location /></MemoryRouter>);
  fireEvent.change(screen.getByRole("textbox"), { target: { value: "cần tìm" } });
  fireEvent.click(await screen.findByText("Dự án cần tìm"));
  expect(request).toHaveBeenCalledWith("/projects", expect.objectContaining({
    query: { q: "cần tìm", limit: 8 },
  }));
  expect(screen.getByText("/projects/p1")).toBeInTheDocument();
});
