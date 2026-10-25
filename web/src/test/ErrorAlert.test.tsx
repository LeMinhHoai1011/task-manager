import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ErrorAlert } from "../components/ui";

describe("ErrorAlert (envelope trung tính)", () => {
  it("render message trung tính qua role=alert", () => {
    render(<ErrorAlert message="Có lỗi xảy ra. Vui lòng thử lại." />);
    const el = screen.getByRole("alert");
    expect(el).toBeInTheDocument();
    expect(el).toHaveTextContent("Có lỗi xảy ra. Vui lòng thử lại.");
  });

  it("message null thì render rỗng (không lộ text server)", () => {
    const { container } = render(<ErrorAlert message={null} />);
    expect(container).toBeEmptyDOMElement();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});
