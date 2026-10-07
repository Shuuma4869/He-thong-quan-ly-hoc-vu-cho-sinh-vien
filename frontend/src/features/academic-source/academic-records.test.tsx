import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AcademicRecords } from "./academic-records";
import { AcademicSourceError, type Records, type SourceStatus } from "./api";
import * as source from "./api";
import * as sync from "@/features/sync/api";
import * as curriculum from "@/features/curriculum/api";

vi.mock("./api", async (importOriginal) => ({ ...(await importOriginal<typeof import("./api")>()),
  getSourceStatus: vi.fn(), getPrograms: vi.fn(), getRecords: vi.fn(), getResultDetail: vi.fn(), getProgressSummary: vi.fn(),
}));
vi.mock("@/features/curriculum/api", async (importOriginal) => ({ ...(await importOriginal<typeof import("@/features/curriculum/api")>()),
  getCurriculumSelection: vi.fn(),
}));
vi.mock("@/features/sync/api", async (importOriginal) => ({ ...(await importOriginal<typeof import("@/features/sync/api")>()),
  getPhenikaaConnection: vi.fn(),
}));

const programOne = `pr_${"a".repeat(64)}`;
const programTwo = `pr_${"b".repeat(64)}`;
const registrationOne = `rg_${"c".repeat(64)}`;
const registrationTwo = `rg_${"d".repeat(64)}`;
const detailOne = `dt_${"e".repeat(64)}`;
const connection: sync.PhenikaaConnection = { status: "CONNECTED", lastAuthenticatedAt: "2026-01-01T00:00:00Z",
  lastSuccessfulAccessAt: null, lastFailedAccessAt: null, lastFailureCode: null, reconnectionRequired: false };
const sourceStatus: SourceStatus = { connectionState: "CONNECTED", lastSuccessfulAccessAt: null, capabilities: [
  { capability: "PROFILE", mode: "PERSISTED", completeness: "SOURCE_VERIFIED" },
  { capability: "CURRICULUM", mode: "PERSISTED_PARTIAL", completeness: "UNKNOWN" },
  { capability: "ACADEMIC_RECORDS", mode: "LIVE_READ_ONLY", completeness: "UNKNOWN" },
  { capability: "ACADEMIC_RESULT_DETAIL", mode: "LIVE_READ_ONLY", completeness: "UNKNOWN" },
  { capability: "STUDENT_COURSE", mode: "BLOCKED_SOURCE_LIMIT", completeness: "UNKNOWN" },
  { capability: "PREREQUISITE", mode: "BLOCKED_PARTIAL", completeness: "UNKNOWN" },
] };
const record: Records["records"][number] = {
  registrationRef: registrationOne, course: { code: "TEST101", name: "Môn tổng hợp", credits: 1.5 },
  semester: { academicYearStart: 2025, termCode: "1", code: "2025-1" }, reportedLearningAttempt: 2,
  components: [{ code: "QT", name: "Quá trình", examAttempt: 1, score: 6.25 }],
  finalResult: { detailRef: detailOne, examAttempt: 2, outcome: "FAILED", numericScore: null, letterGrade: null, gradePoints: null },
};
const records: Records = { completeness: "UNKNOWN", unknownSemantics: {
  creditsEarned: "UNKNOWN", includedInGpa: "UNKNOWN", currentResult: "UNKNOWN",
}, records: [record, { ...record, registrationRef: registrationTwo }] };

function show() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 300_000 } } });
  render(<QueryClientProvider client={client}><AcademicRecords userId="synthetic-user" /></QueryClientProvider>);
  return client;
}

beforeEach(() => {
  vi.mocked(curriculum.getCurriculumSelection).mockResolvedValue({ selectionMode: "USER_SELECTED_AMS", curriculum: null });
  vi.mocked(sync.getPhenikaaConnection).mockResolvedValue(connection);
  vi.mocked(source.getSourceStatus).mockResolvedValue(sourceStatus);
  vi.mocked(source.getPrograms).mockResolvedValue({ completeness: "UNKNOWN", programs: [
    { programRef: programOne, label: "Chương trình một" }, { programRef: programTwo, label: "Chương trình hai" },
  ] });
  vi.mocked(source.getRecords).mockResolvedValue(records);
  vi.mocked(source.getResultDetail).mockResolvedValue({ detailRef: detailOne, completeness: "UNKNOWN",
    components: [{ registrationRef: registrationOne, code: "THI", name: "Thi nguồn", examAttempt: 2, score: 4.5 }] });
});
afterEach(() => { cleanup(); vi.resetAllMocks(); });

describe("Live academic records", () => {
  const tracked = { id: "00000000-0000-4000-8000-000000000001", code: "TEST", name: "Chương trình kiểm thử",
    cohort: null, revision: null, minimumCredits: 100 };
  const progress = { mode: "SOURCE_REPORTED_LIVE_READ_ONLY" as const, completeness: "UNKNOWN" as const,
    selectionMode: "USER_SELECTED_AMS" as const,
    curriculum: { id: tracked.id, code: tracked.code, name: tracked.name },
    summary: { cumulativeAverageScale4: 3.25, cumulativeAverageScale10: 8.1, sourceAccumulatedCredits: 72 } };

  it("requires a tracked curriculum without reading the source", async () => {
    show();
    expect(await screen.findByRole("link", { name: "Mở Chương trình" })).toHaveAttribute("href", "/curriculum");
    expect(source.getProgressSummary).not.toHaveBeenCalled();
  });

  it("reads progress only after a click and keeps source semantics", async () => {
    vi.mocked(curriculum.getCurriculumSelection).mockResolvedValue({ selectionMode: "USER_SELECTED_AMS", curriculum: tracked });
    vi.mocked(source.getSourceStatus).mockResolvedValue({ ...sourceStatus, capabilities: [...sourceStatus.capabilities,
      { capability: "ACADEMIC_PROGRESS_SUMMARY", mode: "LIVE_READ_ONLY", completeness: "UNKNOWN" }] });
    vi.mocked(source.getProgressSummary).mockResolvedValue(progress);
    show();
    const button = await screen.findByRole("button", { name: "Đọc tổng hợp tích lũy từ nguồn" });
    expect(source.getProgressSummary).not.toHaveBeenCalled();
    await userEvent.click(button);
    expect(await screen.findByText("Giá trị tín chỉ tích lũy — nguồn báo")).toBeInTheDocument();
    expect(screen.getByText("AMS không tự tính các giá trị dưới đây. Độ đầy đủ của dữ liệu nguồn chưa được xác nhận.")).toBeInTheDocument();
    expect(screen.getByText("3.25")).toBeInTheDocument();
    expect(screen.getByText("8.1")).toBeInTheDocument();
    expect(screen.getByText("72")).toBeInTheDocument();
    expect(source.getProgressSummary).toHaveBeenCalledTimes(1);
    for (const phrase of ["GPA AMS", "Tín chỉ đã đạt", "Tín chỉ còn thiếu", "% hoàn thành"])
      expect(document.body.textContent).not.toContain(phrase);
  });

  it("does not show a cached summary after a failed manual reread", async () => {
    vi.mocked(curriculum.getCurriculumSelection).mockResolvedValue({ selectionMode: "USER_SELECTED_AMS", curriculum: tracked });
    vi.mocked(source.getSourceStatus).mockResolvedValue({ ...sourceStatus, capabilities: [...sourceStatus.capabilities,
      { capability: "ACADEMIC_PROGRESS_SUMMARY", mode: "LIVE_READ_ONLY", completeness: "UNKNOWN" }] });
    vi.mocked(source.getProgressSummary).mockResolvedValueOnce(progress)
      .mockRejectedValueOnce(new AcademicSourceError(502, "SOURCE_SCHEMA_CHANGED"));
    show();
    const button = await screen.findByRole("button", { name: "Đọc tổng hợp tích lũy từ nguồn" });
    await userEvent.click(button);
    expect(await screen.findByText("Giá trị tín chỉ tích lũy — nguồn báo")).toBeInTheDocument();
    await userEvent.click(button);
    expect(await screen.findByRole("alert")).toHaveTextContent("thay đổi cấu trúc");
    expect(screen.queryByText("Giá trị tín chỉ tích lũy — nguồn báo")).not.toBeInTheDocument();
  });

  it("does not allow progress read when capability or connection is missing", async () => {
    vi.mocked(curriculum.getCurriculumSelection).mockResolvedValue({ selectionMode: "USER_SELECTED_AMS", curriculum: tracked });
    show();
    expect(await screen.findByText("Tổng hợp tích lũy trực tiếp hiện không khả dụng từ nguồn.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Đọc tổng hợp tích lũy từ nguồn" })).not.toBeInTheDocument();
    expect(source.getProgressSummary).not.toHaveBeenCalled();
  });

  it("drops the old summary when the tracked curriculum changes", async () => {
    vi.mocked(curriculum.getCurriculumSelection).mockResolvedValue({ selectionMode: "USER_SELECTED_AMS", curriculum: tracked });
    vi.mocked(source.getSourceStatus).mockResolvedValue({ ...sourceStatus, capabilities: [...sourceStatus.capabilities,
      { capability: "ACADEMIC_PROGRESS_SUMMARY", mode: "LIVE_READ_ONLY", completeness: "UNKNOWN" }] });
    vi.mocked(source.getProgressSummary).mockResolvedValue(progress);
    const client = show();
    await userEvent.click(await screen.findByRole("button", { name: "Đọc tổng hợp tích lũy từ nguồn" }));
    expect(await screen.findByText("Giá trị tín chỉ tích lũy — nguồn báo")).toBeInTheDocument();
    vi.mocked(curriculum.getCurriculumSelection).mockResolvedValue({ selectionMode: "USER_SELECTED_AMS",
      curriculum: { ...tracked, id: "00000000-0000-4000-8000-000000000002", name: "Chương trình B" } });
    await client.invalidateQueries({ queryKey: curriculum.curriculumKeys.selection("synthetic-user") });
    expect(await screen.findByText("Chương trình B")).toBeInTheDocument();
    expect(screen.queryByText("Giá trị tín chỉ tích lũy — nguồn báo")).not.toBeInTheDocument();
  });

  it.each([
    ["CURRICULUM_SELECTION_REQUIRED", 409, "cần chọn chương trình"],
    ["SOURCE_PROGRESS_UNAVAILABLE", 409, "Chưa thể đối chiếu"],
    ["RATE_LIMITED", 429, "Vui lòng chờ"],
    ["SOURCE_SCHEMA_CHANGED", 502, "thay đổi cấu trúc"],
    ["SOURCE_DATA_INCOMPLETE", 502, "chưa nhất quán"],
  ] as const)("shows safe progress error %s without retry", async (code, status, text) => {
    vi.mocked(curriculum.getCurriculumSelection).mockResolvedValue({ selectionMode: "USER_SELECTED_AMS", curriculum: tracked });
    vi.mocked(source.getSourceStatus).mockResolvedValue({ ...sourceStatus, capabilities: [...sourceStatus.capabilities,
      { capability: "ACADEMIC_PROGRESS_SUMMARY", mode: "LIVE_READ_ONLY", completeness: "UNKNOWN" }] });
    vi.mocked(source.getProgressSummary).mockRejectedValue(new AcademicSourceError(status, code));
    show();
    await userEvent.click(await screen.findByRole("button", { name: "Đọc tổng hợp tích lũy từ nguồn" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(text);
    await waitFor(() => expect(source.getProgressSummary).toHaveBeenCalledTimes(1));
  });
  it.each([null, "DISCONNECTED", "RECONNECTION_REQUIRED"] as const)("does not request source data when connection is %s", async (state) => {
    vi.mocked(sync.getPhenikaaConnection).mockResolvedValue(state === null ? null : { ...connection, status: state });
    show();
    expect(await screen.findByText(state === null ? /tích hợp Phenikaa chưa được bật/ : state === "DISCONNECTED"
      ? /Chưa có kết nối học vụ/ : /Cần kết nối lại nguồn học vụ/)).toBeInTheDocument();
    expect(source.getSourceStatus).not.toHaveBeenCalled();
    expect(source.getPrograms).not.toHaveBeenCalled();
    expect(source.getRecords).not.toHaveBeenCalled();
  });

  it("does not read programs without an explicitly supported capability", async () => {
    vi.mocked(source.getSourceStatus).mockResolvedValue({ connectionState: "CONNECTED", lastSuccessfulAccessAt: null, capabilities: [] });
    show();
    expect(await screen.findByText(/Khả năng đọc kết quả trực tiếp hiện không khả dụng/)).toBeInTheDocument();
    expect(source.getPrograms).not.toHaveBeenCalled();
  });

  it.each(["RECONNECTION_REQUIRED", "DISCONNECTED"] as const)("stops before programs when source status changes to %s", async (state) => {
    vi.mocked(source.getSourceStatus).mockResolvedValue({ ...sourceStatus, connectionState: state });
    show();
    expect(await screen.findByText(state === "RECONNECTION_REQUIRED" ? /Cần kết nối lại nguồn học vụ/ : /Chưa có kết nối học vụ có thể sử dụng/)).toBeInTheDocument();
    expect(screen.queryByText("Đã kết nối nguồn học vụ.")).not.toBeInTheDocument();
    expect(source.getSourceStatus).toHaveBeenCalledTimes(1);
    expect(source.getPrograms).not.toHaveBeenCalled();
    expect(source.getRecords).not.toHaveBeenCalled();
  });

  it("preserves duplicate observations and unknown semantics without computing a result", async () => {
    show();
    expect(await screen.findAllByRole("heading", { name: "Môn tổng hợp" })).toHaveLength(2);
    expect(screen.getByLabelText("Chương trình đang xem")).toBeInTheDocument();
    expect(screen.getAllByText("1.5")).toHaveLength(2);
    expect(screen.getAllByText("Nguồn báo chưa đạt · Lần thi nguồn báo: 2")).toHaveLength(2);
    expect(screen.getAllByText("Chưa có")).toHaveLength(6);
    expect(screen.getByText(/AMS chưa xác định tín chỉ đạt/)).toBeInTheDocument();
    expect(document.body.textContent).not.toContain(registrationOne);
    expect(document.body.textContent).not.toContain(detailOne);
    for (const phrase of ["GPA:", "Tín chỉ đã đạt", "Kết quả hiện hành"])
      expect(document.body.textContent).not.toContain(phrase);
    expect(source.getRecords).toHaveBeenCalledWith(programOne, expect.any(AbortSignal));
  });

  it("loads detail only after opening, retains it on reopen and discards open state on program switch", async () => {
    show();
    const buttons = await screen.findAllByRole("button", { name: "Xem chi tiết tổng kết" });
    expect(source.getResultDetail).not.toHaveBeenCalled();
    await userEvent.click(buttons[0]);
    expect(await screen.findByText(/THI · Thi nguồn/)).toBeInTheDocument();
    expect(buttons[0]).toHaveAttribute("aria-expanded", "true");
    expect(within(document.getElementById(buttons[0].getAttribute("aria-controls")!)!).getByText(/THI · Thi nguồn/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Đóng chi tiết tổng kết" }));
    await userEvent.click(screen.getAllByRole("button", { name: "Xem chi tiết tổng kết" })[0]);
    expect(await screen.findByText(/THI · Thi nguồn/)).toBeInTheDocument();
    expect(source.getResultDetail).toHaveBeenCalledTimes(1);
    await userEvent.selectOptions(screen.getByLabelText("Chương trình đang xem"), "1");
    await waitFor(() => expect(source.getRecords).toHaveBeenCalledWith(programTwo, expect.any(AbortSignal)));
    expect(screen.queryByText(/THI · Thi nguồn/)).not.toBeInTheDocument();
    expect(source.getResultDetail).toHaveBeenCalledTimes(1);
  });

  it("keeps empty programs and records neutral", async () => {
    vi.mocked(source.getPrograms).mockResolvedValueOnce({ completeness: "UNKNOWN", programs: [] });
    show();
    expect(await screen.findByText(/Không có chương trình nào được nguồn trả về/)).toBeInTheDocument();
    expect(source.getRecords).not.toHaveBeenCalled();
    vi.mocked(source.getRecords).mockResolvedValueOnce({ ...records, records: [] });
    await userEvent.click(screen.getByRole("button", { name: "Đọc lại chương trình" }));
    expect(await screen.findByText(/Không có bản ghi kết quả nào được nguồn trả về/)).toBeInTheDocument();
  });

  it("shows a stable rate-limit error without automatic retries or exposing technical content", async () => {
    vi.mocked(source.getRecords).mockRejectedValue(new AcademicSourceError(429, "RATE_LIMITED"));
    show();
    expect(await screen.findByRole("alert")).toHaveTextContent("Vui lòng chờ trước khi đọc lại dữ liệu học vụ.");
    expect(source.getRecords).toHaveBeenCalledTimes(1);
    expect(screen.queryByText("Academic source request failed")).not.toBeInTheDocument();
  });

  it("offers to reload programs when a source reference expires", async () => {
    vi.mocked(source.getRecords).mockRejectedValue(new AcademicSourceError(409, "INVALID_SOURCE_REFERENCE"));
    show();
    expect(await screen.findByRole("alert")).toHaveTextContent("Dữ liệu đã thay đổi");
    expect(screen.getAllByRole("button", { name: "Đọc lại chương trình" })).toHaveLength(2);
  });
});
