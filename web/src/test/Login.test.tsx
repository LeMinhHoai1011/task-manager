import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import Login from "../pages/Login";

vi.mock("../auth/AuthContext", () => ({
  useAuth: () => ({
    sessionNotice: null,
    login: vi.fn().mockRejectedValue({status: 401, uiMessage: "Session expired"}),
  }),
}));

describe("Login error", () => {
  it("explains invalid credentials rather than an expired session", async () => {
    localStorage.setItem("tm.locale", "en");
    const { container } = render(<MemoryRouter><Login /></MemoryRouter>);
    fireEvent.change(container.querySelector('input[autocomplete="username"]')!, {target: {value: "tester"}});
    fireEvent.change(container.querySelector('input[type="password"]')!, {target: {value: "test-password"}});
    fireEvent.submit(container.querySelector("form")!);
    expect(await screen.findByRole("alert")).toHaveTextContent("Incorrect username/email or password");
    expect(screen.getByRole("alert")).not.toHaveTextContent("Session expired");
    localStorage.removeItem("tm.locale");
  });
});
