import { afterEach, describe, expect, it, vi } from "vitest";
import { clearStudyPlanScenario, copyStudyPlanScenario, getStudyPlan, getStudyPlanComparison, getStudyPlanScenarios,
  planCourse, removePlannedCourse } from "./api";
import * as auth from "@/features/auth/api";

vi.mock("@/features/auth/api", async (original) => ({ ...(await original<typeof import("@/features/auth/api")>()),
  authMutation: vi.fn(),
}));
afterEach(() => { vi.unstubAllGlobals(); vi.resetAllMocks(); });

const curriculumId = "00000000-0000-4000-8000-000000000001";
const courseId = "00000000-0000-4000-8000-000000000002";
const plan = { mode: "USER_PLANNED_AMS", scenarioNo: 1, curriculum: { id: curriculumId, code: "CURR-A", name: "Chương trình kiểm thử" },
  terms: [{ plannedTerm: 1, courseCount: 1, plannedCredits: 3.25, courses: [{ courseId, code: "TEST101",
    name: "Môn kiểm thử", credits: 3.25, requirement: "REQUIRED", groupName: null }] }] };
const comparison = {
  mode: "USER_PLANNED_AMS", curriculum: plan.curriculum, leftScenario: 1, rightScenario: 2,
  left: { scenarioNo: 1, courseCount: 1, termCount: 1, plannedCredits: 3.25 },
  right: { scenarioNo: 2, courseCount: 1, termCount: 1, plannedCredits: 3.25 },
  terms: [{ plannedTerm: 1, leftCourseCount: 1, leftPlannedCredits: 3.25,
    rightCourseCount: 1, rightPlannedCredits: 3.25 }],
  courses: [{ ...plan.terms[0].courses[0], leftPlannedTerm: 1, rightPlannedTerm: 1, change: "UNCHANGED" }],
};

describe("Study plan API", () => {
  it("reads a strict local-only plan without source identifiers", async () => {
    const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify(plan)));
    vi.stubGlobal("fetch", fetcher);
    expect(await getStudyPlan()).toEqual(plan);
    expect(fetcher).toHaveBeenCalledWith("/api/me/academic/study-plan?scenario=1",
      expect.objectContaining({ credentials: "same-origin", cache: "no-store" }));
  });

  it("rejects a scenario mismatch and reads five strict summaries", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify(plan))));
    await expect(getStudyPlan(2)).rejects.toThrow("mismatch");
    const summaries = { mode: "USER_PLANNED_AMS", curriculum: plan.curriculum,
      scenarios: [1, 2, 3, 4, 5].map((scenarioNo) => ({
        scenarioNo, courseCount: 0, termCount: 0, plannedCredits: 0,
      })) };
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify(summaries))));
    expect((await getStudyPlanScenarios()).scenarios).toHaveLength(5);
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ ...summaries,
      scenarios: [summaries.scenarios[0], summaries.scenarios[0]] }))));
    await expect(getStudyPlanScenarios()).rejects.toThrow();
  });

  it("reads a strict comparison only when requested", async () => {
    const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify(comparison)));
    vi.stubGlobal("fetch", fetcher);
    expect(await getStudyPlanComparison(1, 2)).toEqual(comparison);
    expect(fetcher).toHaveBeenCalledWith("/api/me/academic/study-plan/compare?left=1&right=2",
      expect.objectContaining({ credentials: "same-origin", cache: "no-store" }));
  });

  it("rejects invalid pairs before fetch and mismatched responses", async () => {
    const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify(comparison)));
    vi.stubGlobal("fetch", fetcher);
    for (const [left, right] of [[0, 2], [6, 2], [1.5, 2], [1, 0], [1, 6], [2, 2]])
      await expect(getStudyPlanComparison(left, right)).rejects.toThrow();
    expect(fetcher).not.toHaveBeenCalled();
    await expect(getStudyPlanComparison(2, 1)).rejects.toThrow("mismatch");
    fetcher.mockResolvedValue(new Response(JSON.stringify({ ...comparison, right: { ...comparison.right, scenarioNo: 3 } })));
    await expect(getStudyPlanComparison(1, 2)).rejects.toThrow("mismatch");
  });

  it.each([
    { mode: "SOURCE_PROGRESS" },
    { sourceId: "private" },
    { leftScenario: 0 },
    { rightScenario: 6 },
    { left: { ...comparison.left, plannedCredits: -1 } },
    { right: { ...comparison.right, scenarioNo: 0 } },
    { terms: [{ ...comparison.terms[0], plannedTerm: 0 }] },
    { terms: [{ ...comparison.terms[0], leftPlannedCredits: -1 }] },
    { courses: [{ ...comparison.courses[0], change: "BETTER" }] },
    { courses: [{ ...comparison.courses[0], leftPlannedTerm: 100 }] },
    { courses: [{ ...comparison.courses[0], sourceCourseId: "private" }] },
  ])("rejects an unsafe comparison contract %#", async (change) => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ ...comparison, ...change }))));
    await expect(getStudyPlanComparison(1, 2)).rejects.toThrow();
  });

  it("accepts the safe no-selection comparison response", async () => {
    const empty = { ...comparison, curriculum: null,
      left: { ...comparison.left, courseCount: 0, termCount: 0, plannedCredits: 0 },
      right: { ...comparison.right, courseCount: 0, termCount: 0, plannedCredits: 0 },
      terms: [], courses: [] };
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify(empty))));
    expect(await getStudyPlanComparison(1, 2)).toEqual(empty);
  });

  it.each([
    { mode: "SOURCE_PROGRESS" }, { sourceId: "private" }, { scenarioNo: 0 },
    { terms: [{ ...plan.terms[0], plannedTerm: 0 }] },
    { terms: [{ ...plan.terms[0], plannedCredits: -1 }] },
    { terms: [{ ...plan.terms[0], courses: [{ ...plan.terms[0].courses[0], sourceCourseId: "private" }] }] },
  ])("rejects an unsafe plan contract %#", async (change) => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ ...plan, ...change }))));
    await expect(getStudyPlan()).rejects.toThrow();
  });

  it("uses CSRF mutation convention and validates the local ordinal", async () => {
    vi.mocked(auth.authMutation).mockResolvedValue(new Response(null, { status: 204 }));
    await planCourse(curriculumId, courseId, 99, 2);
    expect(auth.authMutation).toHaveBeenCalledWith(
      `/api/me/academic/study-plan/curricula/${curriculumId}/courses/${courseId}?scenario=2`,
      JSON.stringify({ plannedTerm: 99 }), "PUT");
    await removePlannedCourse(curriculumId, courseId, 2);
    expect(auth.authMutation).toHaveBeenLastCalledWith(
      `/api/me/academic/study-plan/curricula/${curriculumId}/courses/${courseId}?scenario=2`, "{}", "DELETE");
    for (const invalid of [0, 100, 2.5]) await expect(planCourse(curriculumId, courseId, invalid)).rejects.toThrow();
    for (const invalid of [0, 6, 2.5]) await expect(planCourse(curriculumId, courseId, 1, invalid)).rejects.toThrow();
    expect(auth.authMutation).toHaveBeenCalledTimes(2);
  });

  it("uses CSRF for copy and clear without accepting invalid scenarios", async () => {
    vi.mocked(auth.authMutation).mockResolvedValue(new Response(null, { status: 204 }));
    await copyStudyPlanScenario(1, 5);
    expect(auth.authMutation).toHaveBeenCalledWith("/api/me/academic/study-plan/scenarios/5/copy",
      JSON.stringify({ sourceScenario: 1 }), "POST");
    await clearStudyPlanScenario(5);
    expect(auth.authMutation).toHaveBeenLastCalledWith("/api/me/academic/study-plan/scenarios/5", "{}", "DELETE");
    for (const values of [[1, 1], [0, 2], [1, 6], [2.5, 3]])
      await expect(copyStudyPlanScenario(values[0], values[1])).rejects.toThrow();
    await expect(clearStudyPlanScenario(0)).rejects.toThrow();
    expect(auth.authMutation).toHaveBeenCalledTimes(2);
  });
});
