import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { expect, test, vi } from "vitest";
import Layout from "../components/Layout";
import { ThemeProvider } from "../components/ThemeContext";

vi.mock("../auth/AuthContext", () => ({ useAuth: () => ({ user: { id: "user" } }) }));
vi.mock("../components/NotificationBell", () => ({ default: () => null }));

test("collapses and expands desktop navigation with accessible links", () => {
  render(<MemoryRouter><ThemeProvider><Layout>Trang làm việc</Layout></ThemeProvider></MemoryRouter>);
  const navigation = screen.getByRole("navigation", { name: "Điều hướng chính" });
  fireEvent.click(screen.getByRole("button", { name: "Thu gọn thanh điều hướng" }));
  expect(navigation).toHaveClass("is-collapsed");
  expect(screen.getByRole("link", { name: "Việc của tôi" })).toHaveAttribute("href", "/my-tasks");
  const expand = screen.getByRole("button", { name: "Mở rộng thanh điều hướng" });
  expect(expand).toHaveAttribute("aria-expanded", "false");
  fireEvent.click(expand);
  expect(navigation).not.toHaveClass("is-collapsed");
});
