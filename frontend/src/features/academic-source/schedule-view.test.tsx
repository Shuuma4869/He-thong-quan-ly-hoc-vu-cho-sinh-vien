import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ScheduleView, validScheduleRange } from "./schedule-view";
import * as source from "./api";
import * as sync from "@/features/sync/api";

vi.mock("./api", async (importOriginal) => ({ ...(await importOriginal<typeof import("./api")>()),
  getSourceStatus: vi.fn(), getSchedule: vi.fn(), getExamPeriods: vi.fn(), getExams: vi.fn(),
}));
vi.mock("@/features/sync/api", async (importOriginal) => ({ ...(await importOriginal<typeof import("@/features/sync/api")>()),
  getPhenikaaConnection: vi.fn(),
}));

const periodRef = `ep_${"a".repeat(64)}`;
const connection: sync.PhenikaaConnection = { status: "CONNECTED", lastAuthenticatedAt: "2026-10-01T00:00:00Z",
  lastSuccessfulAccessAt: null, lastFailedAccessAt: null, lastFailureCode: null, reconnectionRequired: false };
const status: source.SourceStatus = { connectionState: "CONNECTED", lastSuccessfulAccessAt: null, capabilities: [
  { capability: "SCHEDULE", mode: "LIVE_READ_ONLY", completeness: "UNKNOWN" },
  { capability: "EXAMS", mode: "LIVE_READ_ONLY", completeness: "UNKNOWN" },
] };
const schedule: source.Schedule = { completeness: "UNKNOWN", identityScope: "UNVERIFIED", zone: "Asia/Ho_Chi_Minh",
  from: "2026-10-01", through: "2026-10-14", entries: [
    { courseName: null, date: "2026-10-01", startsAt: null, endsAt: null, room: null,
      lecturer: null, kind: "UNKNOWN" },
    { courseName: null, date: "2026-10-01", startsAt: null, endsAt: null, room: null,
      lecturer: null, kind: "UNKNOWN" },
  ] };
const exams: source.Exams = { completeness: "UNKNOWN", identityScope: "UNVERIFIED", zone: "Asia/Ho_Chi_Minh",
  period: { periodRef, label: "Kỳ nguồn giả định" }, entries: [
    { courseCode: "TEST101", courseName: "Môn kiểm thử", examAttempt: 2, examSession: null,
      date: "2026-10-01", startsAt: "08:30:00", endsAt: null, room: null },
    { courseCode: "TEST101", courseName: "Môn kiểm thử", examAttempt: 2, examSession: null,
      date: "2026-10-01", startsAt: "08:30:00", endsAt: null, room: null },
  ] };

function show() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(<QueryClientProvider client={client}><ScheduleView userId="synthetic-user" /></QueryClientProvider>);
}

beforeEach(() => {
  vi.mocked(sync.getPhenikaaConnection).mockResolvedValue(connection);
  vi.mocked(source.getSourceStatus).mockResolvedValue(status);
  vi.mocked(source.getSchedule).mockResolvedValue(schedule);
  vi.mocked(source.getExamPeriods).mockResolvedValue({ completeness: "UNKNOWN",
    periods: [{ periodRef, label: "Kỳ nguồn giả định" }] });
  vi.mocked(source.getExams).mockResolvedValue(exams);
});
afterEach(() => { cleanup(); vi.resetAllMocks(); });

describe("Live read-only schedule", () => {
  it.each([null, "DISCONNECTED", "RECONNECTION_REQUIRED"] as const)("does not read source when connection is %s", async (state) => {
    vi.mocked(sync.getPhenikaaConnection).mockResolvedValue(state === null ? null : { ...connection, status: state });
    show();
    expect(await screen.findByText(state === null ? /tích hợp Phenikaa chưa được bật/
      : state === "DISCONNECTED" ? /Chưa có kết nối học vụ/ : /Cần kết nối lại nguồn học vụ/)).toBeInTheDocument();
    expect(source.getSourceStatus).not.toHaveBeenCalled();
    expect(source.getSchedule).not.toHaveBeenCalled();
    expect(source.getExamPeriods).not.toHaveBeenCalled();
  });

  it.each(["DISCONNECTED", "RECONNECTION_REQUIRED"] as const)("stops before source reads when status races to %s", async (state) => {
    vi.mocked(source.getSourceStatus).mockResolvedValue({ ...status, connectionState: state });
    show();
    expect(await screen.findByText(state === "DISCONNECTED" ? /Chưa có kết nối học vụ/ : /Cần kết nối lại nguồn học vụ/))
      .toBeInTheDocument();
    expect(source.getSchedule).not.toHaveBeenCalled();
    expect(source.getExamPeriods).not.toHaveBeenCalled();
  });

  it("requires matching live capabilities for both tabs", async () => {
    vi.mocked(source.getSourceStatus).mockResolvedValue({ ...status, capabilities: [] });
    show();
    expect(await screen.findByText(/Khả năng đọc lịch cá nhân trực tiếp hiện không khả dụng/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Lịch thi" }));
    expect(screen.getByText(/Khả năng đọc lịch thi trực tiếp hiện không khả dụng/)).toBeInTheDocument();
    expect(source.getSchedule).not.toHaveBeenCalled();
    expect(source.getExamPeriods).not.toHaveBeenCalled();
  });

  it("validates 31 days and waits for manual read; preserves duplicates, nulls and UNKNOWN kind", async () => {
    show();
    expect(await screen.findByText(/Đã kết nối nguồn học vụ/)).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Từ ngày"), { target: { value: "2026-10-01" } });
    fireEvent.change(screen.getByLabelText("Đến ngày"), { target: { value: "2026-10-31" } });
    expect(source.getSchedule).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "Đọc lịch" })).toBeEnabled();
    fireEvent.change(screen.getByLabelText("Đến ngày"), { target: { value: "2026-11-01" } });
    expect(screen.getByRole("button", { name: "Đọc lịch" })).toBeDisabled();
    fireEvent.change(screen.getByLabelText("Đến ngày"), { target: { value: "2026-10-14" } });
    await userEvent.click(screen.getByRole("button", { name: "Đọc lịch" }));
    expect(await screen.findAllByText("Tên môn chưa được nguồn cung cấp")).toHaveLength(2);
    expect(screen.getAllByText(/Nguồn chưa phân loại/)).toHaveLength(2);
    expect(screen.getAllByText("Giờ chưa được nguồn cung cấp")).toHaveLength(2);
    expect(screen.getAllByText("Phòng chưa được nguồn cung cấp")).toHaveLength(2);
    expect(screen.getAllByText("Giảng viên chưa được nguồn cung cấp")).toHaveLength(2);
    expect(document.body.textContent).not.toContain("private-schedule-id");
  });

  it("loads periods and exams only on action without rendering periodRef", async () => {
    show();
    await screen.findByText(/Đã kết nối nguồn học vụ/);
    await userEvent.click(screen.getByRole("button", { name: "Lịch thi" }));
    expect(source.getExamPeriods).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole("button", { name: "Đọc danh sách kỳ thi" }));
    expect(await screen.findByLabelText("Kỳ thi đang xem")).toBeInTheDocument();
    expect(source.getExams).not.toHaveBeenCalled();
    expect(document.body.innerHTML).not.toContain(periodRef);
    await userEvent.click(screen.getByRole("button", { name: "Đọc lịch thi" }));
    expect(await screen.findAllByText("Lần thi nguồn báo: 2")).toHaveLength(2);
    expect(screen.getAllByText(/Giờ kết thúc chưa được nguồn cung cấp/)).toHaveLength(2);
    expect(document.body.innerHTML).not.toContain(periodRef);
    expect(source.getExams).toHaveBeenCalledWith(periodRef, expect.anything());
  });

  it("explains empty observations and does not retry a 429 automatically", async () => {
    vi.mocked(source.getSchedule).mockResolvedValue({ ...schedule, entries: [] });
    vi.mocked(source.getExamPeriods).mockResolvedValue({ completeness: "UNKNOWN", periods: [] });
    show();
    await screen.findByText(/Đã kết nối nguồn học vụ/);
    await userEvent.click(screen.getByRole("button", { name: "Đọc lịch" }));
    expect(await screen.findByText(/Nguồn không trả bản ghi lịch nào/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Lịch thi" }));
    await userEvent.click(screen.getByRole("button", { name: "Đọc danh sách kỳ thi" }));
    expect(await screen.findByText(/Nguồn không trả bộ lọc kỳ thi nào/)).toBeInTheDocument();
    expect(source.getExams).not.toHaveBeenCalled();
    vi.mocked(source.getExamPeriods).mockRejectedValue(new source.AcademicSourceError(429, "RATE_LIMITED"));
    await userEvent.click(screen.getByRole("button", { name: "Đọc danh sách kỳ thi" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Vui lòng chờ");
    await waitFor(() => expect(source.getExamPeriods).toHaveBeenCalledTimes(2));
  });
});

it("checks calendar-day boundaries without using browser local timezone", () => {
  expect(validScheduleRange("2026-10-01", "2026-10-01")).toBe(true);
  expect(validScheduleRange("2026-10-01", "2026-10-31")).toBe(true);
  expect(validScheduleRange("2026-10-01", "2026-11-01")).toBe(false);
  expect(validScheduleRange("2026-10-01", "2026-09-30")).toBe(false);
  expect(validScheduleRange("2026-02-30", "2026-03-01")).toBe(false);
});
