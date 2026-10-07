import { afterEach, describe, expect, it, vi } from "vitest";
import { getStudyPlan, planCourse, removePlannedCourse } from "./api";
import * as auth from "@/features/auth/api";

vi.mock("@/features/auth/api", async (original) => ({ ...(await original<typeof import("@/features/auth/api")>()),
  authMutation: vi.fn(),
}));
afterEach(() => { vi.unstubAllGlobals(); vi.resetAllMocks(); });

const curriculumId = "00000000-0000-4000-8000-000000000001";
const courseId = "00000000-0000-4000-8000-000000000002";
const plan = { mode: "USER_PLANNED_AMS", curriculum: { id: curriculumId, code: "CURR-A", name: "Chương trình kiểm thử" },
  terms: [{ plannedTerm: 1, courseCount: 1, plannedCredits: 3.25, courses: [{ courseId, code: "TEST101",
    name: "Môn kiểm thử", credits: 3.25, requirement: "REQUIRED", groupName: null }] }] };

describe("Study plan API", () => {
  it("reads a strict local-only plan without source identifiers", async () => {
    const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify(plan)));
    vi.stubGlobal("fetch", fetcher);
    expect(await getStudyPlan()).toEqual(plan);
    expect(fetcher).toHaveBeenCalledWith("/api/me/academic/study-plan",
      expect.objectContaining({ credentials: "same-origin", cache: "no-store" }));
  });

  it.each([
    { mode: "SOURCE_PROGRESS" }, { sourceId: "private" },
    { terms: [{ ...plan.terms[0], plannedTerm: 0 }] },
    { terms: [{ ...plan.terms[0], plannedCredits: -1 }] },
    { terms: [{ ...plan.terms[0], courses: [{ ...plan.terms[0].courses[0], sourceCourseId: "private" }] }] },
  ])("rejects an unsafe plan contract %#", async (change) => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ ...plan, ...change }))));
    await expect(getStudyPlan()).rejects.toThrow();
  });

  it("uses CSRF mutation convention and validates the local ordinal", async () => {
    vi.mocked(auth.authMutation).mockResolvedValue(new Response(null, { status: 204 }));
    await planCourse(curriculumId, courseId, 99);
    expect(auth.authMutation).toHaveBeenCalledWith(
      `/api/me/academic/study-plan/curricula/${curriculumId}/courses/${courseId}`,
      JSON.stringify({ plannedTerm: 99 }), "PUT");
    await removePlannedCourse(curriculumId, courseId);
    expect(auth.authMutation).toHaveBeenLastCalledWith(
      `/api/me/academic/study-plan/curricula/${curriculumId}/courses/${courseId}`, "{}", "DELETE");
    for (const invalid of [0, 100, 2.5]) await expect(planCourse(curriculumId, courseId, invalid)).rejects.toThrow();
    expect(auth.authMutation).toHaveBeenCalledTimes(2);
  });
});
