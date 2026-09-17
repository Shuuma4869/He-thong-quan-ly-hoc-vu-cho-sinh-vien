import { render, screen } from "@testing-library/react";
import { GraduationCap } from "lucide-react";
import { describe, expect, it } from "vitest";
import { MetricCard } from "./metric-card";

describe("MetricCard", () => {
  it("hiển thị nhãn, giá trị và ghi chú", () => {
    render(<MetricCard label="GPA tích lũy" value="3.20" note="Đã đồng bộ" icon={GraduationCap} />);
    expect(screen.getByText("GPA tích lũy")).toBeInTheDocument();
    expect(screen.getByText("3.20")).toBeInTheDocument();
    expect(screen.getByText("Đã đồng bộ")).toBeInTheDocument();
  });
});
