import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { StudyPlanner } from "./study-planner";
import * as planApi from "./api";
import * as curriculum from "@/features/curriculum/api";

vi.mock("./api", async (original) => ({ ...(await original<typeof import("./api")>()),
  getStudyPlan: vi.fn(), planCourse: vi.fn(), removePlannedCourse: vi.fn(),
}));
vi.mock("@/features/curriculum/api", async (original) => ({ ...(await original<typeof import("@/features/curriculum/api")>()),
  getCurriculumCourses: vi.fn(),
}));

const curriculumId = "00000000-0000-4000-8000-000000000001";
const firstId = "00000000-0000-4000-8000-000000000002";
const secondId = "00000000-0000-4000-8000-000000000003";
const first = { courseId: firstId, code: "TEST101", name: "Môn kiểm thử A", credits: 3,
  requirement: "REQUIRED" as const, groupName: null };
const second = { courseId: secondId, code: "TEST102", name: "Môn kiểm thử B", credits: 4,
  requirement: "ELECTIVE" as const, groupName: "Nhóm kiểm thử" };
const selected = { mode: "USER_PLANNED_AMS" as const,
  curriculum: { id: curriculumId, code: "CURR-A", name: "Chương trình kiểm thử" }, terms: [] };
const multiple: planApi.StudyPlan = { ...selected, terms: [
  { plannedTerm: 1, courseCount: 1, plannedCredits: 3, courses: [first] },
  { plannedTerm: 2, courseCount: 1, plannedCredits: 4, courses: [second] },
] };

function show() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
  render(<QueryClientProvider client={client}><StudyPlanner userId="synthetic-user" /></QueryClientProvider>);
  return client;
}
beforeEach(() => {
  vi.mocked(planApi.getStudyPlan).mockResolvedValue(selected);
  vi.mocked(planApi.planCourse).mockResolvedValue(undefined);
  vi.mocked(planApi.removePlannedCourse).mockResolvedValue(undefined);
  vi.mocked(curriculum.getCurriculumCourses).mockResolvedValue({ items: [
    { id: firstId, ...first, groupId: null, recommendedTerm: 8 },
    { id: secondId, ...second, groupId: null, recommendedTerm: null },
  ], nextCursor: null });
});
afterEach(() => { cleanup(); vi.resetAllMocks(); });

describe("Personal study planner", () => {
  it("shows loading, then requires an explicit curriculum selection", async () => {
    vi.mocked(planApi.getStudyPlan).mockReturnValueOnce(new Promise(() => {}));
    show();
    expect(screen.getByRole("status")).toHaveTextContent("Đang đọc kế hoạch học kỳ");
    cleanup();
    vi.mocked(planApi.getStudyPlan).mockResolvedValue({ mode: "USER_PLANNED_AMS", curriculum: null, terms: [] });
    show();
    expect(await screen.findByText("Bạn cần chọn chương trình theo dõi trước khi lập kế hoạch.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Mở Chương trình" })).toHaveAttribute("href", "/curriculum");
    expect(curriculum.getCurriculumCourses).not.toHaveBeenCalled();
  });

  it("keeps an empty plan empty and explains that it is not enrollment", async () => {
    show();
    expect(await screen.findByText("Bạn chưa xếp môn nào vào kế hoạch.")).toBeInTheDocument();
    expect(screen.getByText(/không đăng ký học phần với nhà trường/)).toBeInTheDocument();
    expect(screen.getByText(/chưa kiểm tra điều kiện tiên quyết/)).toBeInTheDocument();
    expect(planApi.planCourse).not.toHaveBeenCalled();
  });

  it("shows sorted terms, planned-credit wording, and no progress claim", async () => {
    vi.mocked(planApi.getStudyPlan).mockResolvedValue(multiple);
    show();
    expect(await screen.findByRole("heading", { name: "Kỳ kế hoạch 1" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Kỳ kế hoạch 2" })).toBeInTheDocument();
    expect(screen.getAllByText("Tín chỉ dự kiến trong kỳ:")).toHaveLength(2);
    expect(screen.getByText("TEST101")).toBeInTheDocument();
    expect(screen.getAllByText("TEST102").length).toBeGreaterThan(0);
    for (const phrase of ["Tín chỉ đã đạt", "Tín chỉ còn thiếu", "% hoàn thành", "Đủ điều kiện", "Đã đăng ký", "GPA"])
      expect(document.body.textContent).not.toContain(phrase);
  });

  it("searches the selected curriculum and allows add, move, and remove", async () => {
    show();
    const search = await screen.findByRole("searchbox", { name: "Tìm theo mã hoặc tên môn" });
    await userEvent.type(search, "TEST101");
    await waitFor(() => expect(curriculum.getCurriculumCourses).toHaveBeenCalledWith(curriculumId, "TEST101", undefined, expect.any(AbortSignal)));
    const term = await screen.findByRole("spinbutton", { name: "Kỳ kế hoạch cho TEST101" });
    await userEvent.clear(term);
    await userEvent.type(term, "2.5");
    expect(screen.getByText("Kỳ kế hoạch phải là số nguyên từ 1 đến 99.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Xếp TEST101 vào kế hoạch" })).toBeDisabled();
    await userEvent.clear(term);
    await userEvent.type(term, "3");
    await userEvent.click(screen.getByRole("button", { name: "Xếp TEST101 vào kế hoạch" }));
    await waitFor(() => expect(planApi.planCourse).toHaveBeenCalledWith(curriculumId, firstId, 3));
    cleanup();
    vi.mocked(planApi.getStudyPlan).mockResolvedValue(multiple);
    show();
    await screen.findByRole("heading", { name: "Kỳ kế hoạch 1" });
    await userEvent.clear(screen.getByRole("spinbutton", { name: "Kỳ mới cho TEST101" }));
    await userEvent.type(screen.getByRole("spinbutton", { name: "Kỳ mới cho TEST101" }), "2");
    await userEvent.click(screen.getByRole("button", { name: "Chuyển kỳ TEST101" }));
    await waitFor(() => expect(planApi.planCourse).toHaveBeenCalledWith(curriculumId, firstId, 2));
    await userEvent.click(screen.getByRole("button", { name: "Bỏ TEST101 khỏi kế hoạch" }));
    await waitFor(() => expect(planApi.removePlannedCourse).toHaveBeenCalledWith(curriculumId, firstId));
  });

  it("hides the old plan while refetching after a selection switch", async () => {
    vi.mocked(planApi.getStudyPlan).mockResolvedValue(multiple);
    const client = show();
    expect(await screen.findByRole("heading", { name: "Kỳ kế hoạch 1" })).toBeInTheDocument();
    vi.mocked(planApi.getStudyPlan).mockResolvedValue({ ...selected,
      curriculum: { id: secondId, code: "CURR-B", name: "Chương trình B" } });
    await client.invalidateQueries({ queryKey: planApi.studyPlanKeys.current("synthetic-user") });
    expect(await screen.findByText("CURR-B — Chương trình B")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Kỳ kế hoạch 1" })).not.toBeInTheDocument();
  });

  it("uses safe errors without exposing server details", async () => {
    vi.mocked(planApi.getStudyPlan).mockRejectedValueOnce(new Error("private SQL"));
    show();
    expect(await screen.findByRole("alert")).not.toHaveTextContent("SQL");
    await userEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByText("Bạn chưa xếp môn nào vào kế hoạch.")).toBeInTheDocument();
    vi.mocked(planApi.planCourse).mockRejectedValueOnce(new (await import("@/features/auth/api")).ApiError(409, "private id"));
    await userEvent.click(await screen.findByRole("button", { name: "Xếp TEST101 vào kế hoạch" }));
    expect(await screen.findByRole("alert")).not.toHaveTextContent("private");
  });
});
