import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { StudyPlanner } from "./study-planner";
import * as planApi from "./api";
import * as curriculum from "@/features/curriculum/api";

vi.mock("./api", async (original) => ({ ...(await original<typeof import("./api")>()),
  getStudyPlan: vi.fn(), getStudyPlanScenarios: vi.fn(), planCourse: vi.fn(), removePlannedCourse: vi.fn(),
  copyStudyPlanScenario: vi.fn(), clearStudyPlanScenario: vi.fn(),
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
const selected = { mode: "USER_PLANNED_AMS" as const, scenarioNo: 1,
  curriculum: { id: curriculumId, code: "CURR-A", name: "Chương trình kiểm thử" }, terms: [] };
const summaries: planApi.StudyPlanScenarios = { mode: "USER_PLANNED_AMS", curriculum: selected.curriculum,
  scenarios: [1, 2, 3, 4, 5].map((scenarioNo) => ({
    scenarioNo, courseCount: 0, termCount: 0, plannedCredits: 0,
  })) };
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
  vi.mocked(planApi.getStudyPlanScenarios).mockResolvedValue(summaries);
  vi.mocked(planApi.planCourse).mockResolvedValue(undefined);
  vi.mocked(planApi.removePlannedCourse).mockResolvedValue(undefined);
  vi.mocked(planApi.copyStudyPlanScenario).mockResolvedValue(undefined);
  vi.mocked(planApi.clearStudyPlanScenario).mockResolvedValue(undefined);
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
    vi.mocked(planApi.getStudyPlan).mockResolvedValue({ mode: "USER_PLANNED_AMS", scenarioNo: 1, curriculum: null, terms: [] });
    vi.mocked(planApi.getStudyPlanScenarios).mockResolvedValue({ mode: "USER_PLANNED_AMS", curriculum: null, scenarios: [] });
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
    await waitFor(() => expect(planApi.planCourse).toHaveBeenCalledWith(curriculumId, firstId, 3, 1));
    cleanup();
    vi.mocked(planApi.getStudyPlan).mockResolvedValue(multiple);
    show();
    await screen.findByRole("heading", { name: "Kỳ kế hoạch 1" });
    await userEvent.clear(screen.getByRole("spinbutton", { name: "Kỳ mới cho TEST101" }));
    await userEvent.type(screen.getByRole("spinbutton", { name: "Kỳ mới cho TEST101" }), "2");
    await userEvent.click(screen.getByRole("button", { name: "Chuyển kỳ TEST101" }));
    await waitFor(() => expect(planApi.planCourse).toHaveBeenCalledWith(curriculumId, firstId, 2, 1));
    await userEvent.click(screen.getByRole("button", { name: "Bỏ TEST101 khỏi kế hoạch" }));
    await waitFor(() => expect(planApi.removePlannedCourse).toHaveBeenCalledWith(curriculumId, firstId, 1));
  });

  it("hides the old plan while refetching after a selection switch", async () => {
    vi.mocked(planApi.getStudyPlan).mockResolvedValue(multiple);
    const client = show();
    expect(await screen.findByRole("heading", { name: "Kỳ kế hoạch 1" })).toBeInTheDocument();
    vi.mocked(planApi.getStudyPlan).mockResolvedValue({ ...selected,
      curriculum: { id: secondId, code: "CURR-B", name: "Chương trình B" } });
    vi.mocked(planApi.getStudyPlanScenarios).mockResolvedValue({ ...summaries,
      curriculum: { id: secondId, code: "CURR-B", name: "Chương trình B" } });
    await client.invalidateQueries({ queryKey: planApi.studyPlanKeys.current("synthetic-user", 1) });
    await client.invalidateQueries({ queryKey: planApi.studyPlanKeys.scenarios("synthetic-user") });
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

  it("switches among five scenarios without showing the previous plan during fetch", async () => {
    vi.mocked(planApi.getStudyPlan).mockImplementation((number) => number === 1
      ? Promise.resolve(multiple) : new Promise(() => {}));
    show();
    expect(await screen.findByRole("heading", { name: "Kỳ kế hoạch 1" })).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "Chọn phương án kế hoạch" })).toHaveValue("1");
    expect(screen.getAllByText("Phương án 5").length).toBeGreaterThan(0);
    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Chọn phương án kế hoạch" }), "2");
    expect(screen.getByRole("status")).toHaveTextContent("Đang đọc kế hoạch học kỳ");
    expect(screen.queryByRole("heading", { name: "Kỳ kế hoạch 1" })).not.toBeInTheDocument();
    expect(planApi.getStudyPlan).toHaveBeenCalledWith(2, expect.any(AbortSignal));
  });

  it("sends add, move and remove mutations only to the selected scenario", async () => {
    let secondPlan: planApi.StudyPlan = { ...selected, scenarioNo: 2 };
    vi.mocked(planApi.getStudyPlan).mockImplementation(async (number) => number === 2 ? secondPlan : selected);
    show();
    await screen.findByText("Bạn chưa xếp môn nào vào kế hoạch.");
    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Chọn phương án kế hoạch" }), "2");
    await screen.findByRole("button", { name: "Xếp TEST101 vào kế hoạch" });
    secondPlan = { ...multiple, scenarioNo: 2 };
    await userEvent.click(screen.getByRole("button", { name: "Xếp TEST101 vào kế hoạch" }));
    await waitFor(() => expect(planApi.planCourse).toHaveBeenCalledWith(curriculumId, firstId, 1, 2));
    await screen.findByRole("heading", { name: "Kỳ kế hoạch 1" });
    const term = screen.getByRole("spinbutton", { name: "Kỳ mới cho TEST101" });
    await userEvent.clear(term);
    await userEvent.type(term, "3");
    await userEvent.click(screen.getByRole("button", { name: "Chuyển kỳ TEST101" }));
    await waitFor(() => expect(planApi.planCourse).toHaveBeenCalledWith(curriculumId, firstId, 3, 2));
    await userEvent.click(screen.getByRole("button", { name: "Bỏ TEST101 khỏi kế hoạch" }));
    await waitFor(() => expect(planApi.removePlannedCourse).toHaveBeenCalledWith(curriculumId, firstId, 2));
    expect(planApi.planCourse).not.toHaveBeenCalledWith(curriculumId, firstId, 1, 1);
  });

  it("copies to an empty target, switches there, and clears only after confirmation", async () => {
    const copied = { ...multiple, scenarioNo: 2 };
    vi.mocked(planApi.getStudyPlan).mockImplementation(async (number) => number === 1 ? multiple : copied);
    vi.mocked(planApi.getStudyPlanScenarios).mockResolvedValue({ ...summaries,
      scenarios: summaries.scenarios.map((item) => item.scenarioNo === 1
        ? { ...item, courseCount: 2, termCount: 2, plannedCredits: 7 } : item) });
    show();
    await screen.findByRole("heading", { name: "Kỳ kế hoạch 1" });
    await userEvent.click(screen.getByRole("button", { name: "Sao chép phương án" }));
    await waitFor(() => expect(planApi.copyStudyPlanScenario).toHaveBeenCalledWith(1, 2));
    expect(await screen.findByRole("combobox", { name: "Chọn phương án kế hoạch" })).toHaveValue("2");
    await screen.findByRole("heading", { name: "Kỳ kế hoạch 2" });
    await userEvent.click(screen.getByRole("button", { name: "Xóa các môn trong Phương án 2" }));
    expect(planApi.clearStudyPlanScenario).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole("button", { name: "Hủy" }));
    expect(planApi.clearStudyPlanScenario).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole("button", { name: "Xóa các môn trong Phương án 2" }));
    vi.mocked(planApi.getStudyPlan).mockImplementation(async (number) => number === 1
      ? multiple : { ...selected, scenarioNo: 2 });
    await userEvent.click(screen.getByRole("button", { name: "Xác nhận xóa các môn trong Phương án 2" }));
    await waitFor(() => expect(planApi.clearStudyPlanScenario).toHaveBeenCalledWith(2));
    expect(await screen.findByText("Bạn chưa xếp môn nào vào kế hoạch.")).toBeInTheDocument();
    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Chọn phương án kế hoạch" }), "1");
    expect(await screen.findByRole("heading", { name: "Kỳ kế hoạch 2" })).toBeInTheDocument();
  });

  it("does not offer occupied or current scenario as a copy target", async () => {
    vi.mocked(planApi.getStudyPlanScenarios).mockResolvedValue({ ...summaries,
      scenarios: summaries.scenarios.map((item) => item.scenarioNo === 2
        ? { ...item, courseCount: 1, termCount: 1, plannedCredits: 3 } : item) });
    show();
    await screen.findByText("Bạn chưa xếp môn nào vào kế hoạch.");
    const target = screen.getByRole("combobox", { name: "Sao chép sang phương án" });
    expect(target).toHaveValue("3");
    expect(Array.from((target as HTMLSelectElement).options).map((option) => option.value)).toEqual(["3", "4", "5"]);
    await userEvent.click(screen.getByRole("button", { name: "Sao chép phương án" }));
    await waitFor(() => expect(planApi.copyStudyPlanScenario).toHaveBeenCalledWith(1, 3));
  });

  it("reports a copy conflict without exposing server details", async () => {
    vi.mocked(planApi.copyStudyPlanScenario).mockRejectedValueOnce(
      new (await import("@/features/auth/api")).ApiError(409, "private SQL"));
    show();
    await screen.findByText("Bạn chưa xếp môn nào vào kế hoạch.");
    await userEvent.click(screen.getByRole("button", { name: "Sao chép phương án" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Kế hoạch hoặc chương trình theo dõi đã thay đổi.");
    expect(document.body.textContent).not.toContain("private SQL");
  });
});
