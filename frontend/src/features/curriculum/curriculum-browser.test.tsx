import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { CurriculumBrowser } from "./curriculum-browser";
import * as api from "./api";

vi.mock("./api", async (importOriginal) => ({ ...(await importOriginal<typeof import("./api")>()),
  getCurricula: vi.fn(), getCurriculum: vi.fn(), getCurriculumCourses: vi.fn(), getCatalogCourses: vi.fn(),
  getCurriculumSelection: vi.fn(), selectCurriculum: vi.fn(), clearCurriculumSelection: vi.fn(),
}));
const id = "10000000-0000-4000-8000-000000000001";
const secondId = "10000000-0000-4000-8000-000000000002";
const curriculum: api.Curriculum = { id, code: "TEST", name: "Chương trình tổng hợp", cohort: null, revision: null, minimumCredits: 120, courseCount: 1, groupCount: 2 };
const course: api.CurriculumCourse = { id, courseId: id, code: "TEST101", name: "Môn tổng hợp", credits: 1.5, requirement: "REQUIRED", groupId: id, groupName: "Nhóm tổng hợp", recommendedTerm: null };
const detail = { curriculum, groups: { items: [
  { id, code: "R", name: "Nhóm tổng hợp", requirement: "REQUIRED" as const, minimumCredits: null, minimumCourseCount: null },
  { id: secondId, code: "E", name: "Nhóm tự chọn tổng hợp", requirement: "ELECTIVE" as const, minimumCredits: 3, minimumCourseCount: 1 },
], nextCursor: null } };

function show() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
  return render(<QueryClientProvider client={client}><CurriculumBrowser userId="synthetic-user" /></QueryClientProvider>);
}
beforeEach(() => {
  vi.mocked(api.getCurriculumSelection).mockResolvedValue({ selectionMode: "USER_SELECTED_AMS", curriculum: null });
  vi.mocked(api.selectCurriculum).mockImplementation(async (value) => ({ selectionMode: "USER_SELECTED_AMS",
    curriculum: { ...curriculum, id: value } }));
  vi.mocked(api.clearCurriculumSelection).mockResolvedValue({ selectionMode: "USER_SELECTED_AMS", curriculum: null });
  vi.mocked(api.getCurricula).mockResolvedValue({ items: [curriculum], nextCursor: null });
  vi.mocked(api.getCurriculum).mockResolvedValue(detail);
  vi.mocked(api.getCurriculumCourses).mockResolvedValue({ items: [course], nextCursor: null });
  vi.mocked(api.getCatalogCourses).mockResolvedValue({ items: [{ id: secondId, code: "TEST102", name: "Môn chưa liên kết", credits: 3, curriculumLinked: false }], nextCursor: null });
});
afterEach(() => { cleanup(); vi.resetAllMocks(); });

describe("Persisted curriculum browser", () => {
  it("keeps a single curriculum unselected until the user chooses it", async () => {
    show();
    expect(await screen.findByText("Bạn chưa chọn chương trình để theo dõi.")).toBeInTheDocument();
    expect(await screen.findByRole("button", { name: "Đặt làm chương trình theo dõi" })).toBeEnabled();
    expect(api.selectCurriculum).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole("button", { name: "Đặt làm chương trình theo dõi" }));
    expect(await screen.findAllByText("Đang theo dõi trong AMS")).toHaveLength(2);
    expect(screen.getByRole("link", { name: "Mở kế hoạch học kỳ" })).toHaveAttribute("href", "/planner");
    expect(screen.queryByText("Bạn chưa chọn chương trình để theo dõi.")).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Bỏ chương trình theo dõi" }));
    expect(await screen.findByText("Bạn chưa chọn chương trình để theo dõi.")).toBeInTheDocument();
    expect(api.clearCurriculumSelection).toHaveBeenCalledOnce();
  });
  it("keeps tracked A visible while viewing B, then switches tracking to B", async () => {
    const second = { ...curriculum, id: secondId, code: "CURR-B", name: "Chương trình kiểm thử B" };
    vi.mocked(api.getCurricula).mockResolvedValue({ items: [curriculum, second], nextCursor: null });
    vi.mocked(api.getCurriculum).mockImplementation(async (value) => ({ curriculum: value === secondId ? second : curriculum,
      groups: { items: [], nextCursor: null } }));
    vi.mocked(api.selectCurriculum).mockImplementation(async (value) => ({ selectionMode: "USER_SELECTED_AMS",
      curriculum: value === secondId ? second : curriculum }));
    show();
    await userEvent.click(await screen.findByRole("button", { name: "Đặt làm chương trình theo dõi" }));
    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Chọn chương trình để xem" }), secondId);
    expect(await screen.findByRole("heading", { name: second.name })).toBeInTheDocument();
    expect(screen.getByText(`${curriculum.code} — ${curriculum.name}`)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Đặt làm chương trình theo dõi" }));
    expect(await screen.findByText(`${second.code} — ${second.name}`)).toBeInTheDocument();
  });
  it("shows selected curriculum even when it is outside the loaded list page", async () => {
    vi.mocked(api.getCurriculumSelection).mockResolvedValue({ selectionMode: "USER_SELECTED_AMS",
      curriculum: { ...curriculum, id: secondId, code: "CURR-B", name: "Chương trình kiểm thử B" } });
    show();
    expect(await screen.findByText("CURR-B — Chương trình kiểm thử B")).toBeInTheDocument();
    expect(api.getCurricula).toHaveBeenCalled();
  });
  it("shows a safe error for a missing curriculum and a retry for selection reads", async () => {
    vi.mocked(api.getCurriculumSelection).mockRejectedValueOnce(new Error("private SQL"));
    show();
    expect(await screen.findByText("Chưa thể đọc dữ liệu đã lưu. Vui lòng thử lại.")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    await screen.findByText("Bạn chưa chọn chương trình để theo dõi.");
    vi.mocked(api.selectCurriculum).mockRejectedValueOnce(new (await import("@/features/auth/api")).ApiError(404, "private id"));
    await userEvent.click(screen.getByRole("button", { name: "Đặt làm chương trình theo dõi" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Chương trình không còn tồn tại");
    expect(document.body.textContent).not.toContain("private");
  });
  it("shows a loading state", () => {
    vi.mocked(api.getCurricula).mockReturnValue(new Promise(() => {}));
    show(); expect(screen.getByText("Đang tải chương trình…")).toBeInTheDocument();
  });
  it("shows one curriculum directly, keeps unknowns, decimals and requirement labels", async () => {
    show();
    expect(await screen.findByText("Môn tổng hợp")).toBeInTheDocument();
    expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
    expect(screen.getAllByText("Chưa xác định").length).toBeGreaterThanOrEqual(4);
    expect(screen.getByText("1.5")).toBeInTheDocument();
    expect(screen.getByText("Tự chọn")).toBeInTheDocument();
    expect(screen.getAllByText("Bắt buộc").length).toBeGreaterThan(0);
    for (const word of ["Chương trình hiện tại", "Đã hoàn thành", "Còn thiếu", "Đủ điều kiện", "GPA"])
      expect(document.body.textContent).not.toContain(word);
  });
  it("lets users choose between curricula without changing their profile", async () => {
    vi.mocked(api.getCurricula).mockResolvedValue({ items: [curriculum, { ...curriculum, id: secondId, name: "Chương trình thứ hai" }], nextCursor: null });
    vi.mocked(api.getCurriculum).mockImplementation(async (value) => value === secondId
      ? { curriculum: { ...curriculum, id: secondId, name: "Chương trình thứ hai" }, groups: { items: [], nextCursor: null } } : detail);
    show();
    await userEvent.selectOptions(await screen.findByRole("combobox", { name: "Chọn chương trình để xem" }), secondId);
    expect(await screen.findByRole("heading", { name: "Chương trình thứ hai" })).toBeInTheDocument();
    expect(api.getCurriculumCourses).toHaveBeenCalledWith(secondId, "", undefined, expect.any(AbortSignal));
  });
  it("keeps the catalog available with no curriculum", async () => {
    vi.mocked(api.getCurricula).mockResolvedValue({ items: [], nextCursor: null });
    show();
    expect(await screen.findByText(/Chưa có chương trình được lưu/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Danh mục môn" }));
    expect(await screen.findByText("Môn chưa liên kết")).toBeInTheDocument();
    expect(screen.getByText("Chưa có liên kết đã lưu")).toBeInTheDocument();
    expect(screen.queryByText("Tự chọn", { exact: true })).not.toBeInTheDocument();
  });
  it("debounces search, shows no results and clears the search", async () => {
    vi.mocked(api.getCurriculumCourses).mockImplementation(async (_id, search) => ({ items: search ? [] : [course], nextCursor: null }));
    show();
    const input = await screen.findByRole("searchbox");
    await userEvent.type(input, "nothing");
    expect(await screen.findByText(/Không tìm thấy môn phù hợp/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Xóa tìm kiếm" }));
    expect(await screen.findByText("Môn tổng hợp")).toBeInTheDocument();
    expect(input).toHaveValue("");
  });
  it("can retry a failed request without displaying internal errors", async () => {
    vi.mocked(api.getCurricula).mockRejectedValueOnce(new Error("SQL private details"));
    show();
    expect(await screen.findByRole("alert")).not.toHaveTextContent("SQL");
    await userEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByText("Môn tổng hợp")).toBeInTheDocument();
  });
  it("shows empty groups and courses without making up requirements", async () => {
    vi.mocked(api.getCurriculum).mockResolvedValue({ curriculum: { ...curriculum, groupCount: 0, courseCount: 0 }, groups: { items: [], nextCursor: null } });
    vi.mocked(api.getCurriculumCourses).mockResolvedValue({ items: [], nextCursor: null });
    show();
    expect(await screen.findByText("Chưa có nhóm môn được lưu.")).toBeInTheDocument();
    expect(await screen.findByText("Chưa có môn được lưu trong danh sách này.")).toBeInTheDocument();
  });
  it("loads another course page and refetches changed persisted names", async () => {
    vi.mocked(api.getCurriculumCourses).mockResolvedValueOnce({ items: [course], nextCursor: "synthetic-cursor" })
      .mockResolvedValueOnce({ items: [{ ...course, id: secondId, code: "TEST102", name: "Môn thứ hai" }], nextCursor: null });
    show();
    await userEvent.click(await screen.findByRole("button", { name: "Tải thêm môn" }));
    expect(await screen.findByText("Môn thứ hai")).toBeInTheDocument();
    expect(api.getCurriculumCourses).toHaveBeenCalledWith(id, "", "synthetic-cursor", expect.any(AbortSignal));
    vi.mocked(api.getCurriculumCourses).mockResolvedValue({ items: [{ ...course, name: "Tên đã cập nhật" }], nextCursor: null });
    await userEvent.click(screen.getByRole("button", { name: "Đọc lại môn" }));
    await waitFor(() => expect(screen.getByText("Tên đã cập nhật")).toBeInTheDocument());
  });
});
