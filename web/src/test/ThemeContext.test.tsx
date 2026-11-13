import { act, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { ThemeProvider, useTheme } from "../components/ThemeContext";

function ThemeProbe() {
  const { theme, toggleTheme } = useTheme();
  return <button onClick={toggleTheme}>{theme}</button>;
}

afterEach(() => {
  localStorage.removeItem("tm.theme");
  localStorage.removeItem("tm.palette-version");
  document.documentElement.classList.remove("dark");
});

describe("Daylight theme", () => {
  it("migrates the previous dark preference to the new light default", () => {
    localStorage.setItem("tm.theme", "dark");
    render(<ThemeProvider><ThemeProbe /></ThemeProvider>);
    expect(screen.getByRole("button", { name: "light" })).toBeInTheDocument();
    expect(document.documentElement).not.toHaveClass("dark");
    expect(localStorage.getItem("tm.palette-version")).toBe("daylight-1");
  });

  it("retains an explicit choice after the palette migration", () => {
    localStorage.setItem("tm.theme", "dark");
    localStorage.setItem("tm.palette-version", "daylight-1");
    render(<ThemeProvider><ThemeProbe /></ThemeProvider>);
    expect(document.documentElement).toHaveClass("dark");
    act(() => fireEvent.click(screen.getByRole("button", { name: "dark" })));
    expect(document.documentElement).not.toHaveClass("dark");
    expect(localStorage.getItem("tm.theme")).toBe("light");
  });
});
