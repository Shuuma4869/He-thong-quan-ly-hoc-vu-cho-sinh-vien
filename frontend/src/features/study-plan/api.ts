import { z } from "zod";
import { authMutation, checkResponse } from "@/features/auth/api";

const scenario = z.number().int().min(1).max(5);
const curriculum = z.object({ id: z.uuid(), code: z.string(), name: z.string() }).strict();
const course = z.object({
  courseId: z.uuid(), code: z.string(), name: z.string(), credits: z.number().finite().nonnegative(),
  requirement: z.enum(["REQUIRED", "ELECTIVE"]), groupName: z.string().nullable(),
}).strict();
const term = z.object({
  plannedTerm: z.number().int().min(1).max(99), courseCount: z.number().int().nonnegative(),
  plannedCredits: z.number().finite().nonnegative(), courses: z.array(course),
}).strict();
const plan = z.object({
  mode: z.literal("USER_PLANNED_AMS"), scenarioNo: scenario,
  curriculum: curriculum.nullable(), terms: z.array(term),
}).strict();
const summary = z.object({
  scenarioNo: scenario, courseCount: z.number().int().nonnegative(), termCount: z.number().int().nonnegative(),
  plannedCredits: z.number().finite().nonnegative(),
}).strict();
const summaries = z.object({
  mode: z.literal("USER_PLANNED_AMS"), curriculum: curriculum.nullable(),
  scenarios: z.array(summary),
}).strict();
const comparisonTerm = z.object({
  plannedTerm: z.number().int().min(1).max(99),
  leftCourseCount: z.number().int().nonnegative(), leftPlannedCredits: z.number().finite().nonnegative(),
  rightCourseCount: z.number().int().nonnegative(), rightPlannedCredits: z.number().finite().nonnegative(),
}).strict();
const comparisonCourse = course.extend({
  leftPlannedTerm: z.number().int().min(1).max(99).nullable(),
  rightPlannedTerm: z.number().int().min(1).max(99).nullable(),
  change: z.enum(["UNCHANGED", "MOVED", "ONLY_LEFT", "ONLY_RIGHT"]),
}).strict().refine((item) => {
  if (item.change === "ONLY_LEFT") return item.leftPlannedTerm !== null && item.rightPlannedTerm === null;
  if (item.change === "ONLY_RIGHT") return item.leftPlannedTerm === null && item.rightPlannedTerm !== null;
  if (item.leftPlannedTerm === null || item.rightPlannedTerm === null) return false;
  return item.change === "UNCHANGED"
    ? item.leftPlannedTerm === item.rightPlannedTerm : item.leftPlannedTerm !== item.rightPlannedTerm;
});
const comparison = z.object({
  mode: z.literal("USER_PLANNED_AMS"), curriculum: curriculum.nullable(),
  leftScenario: scenario, rightScenario: scenario, left: summary, right: summary,
  terms: z.array(comparisonTerm), courses: z.array(comparisonCourse),
}).strict();

export type StudyPlan = z.infer<typeof plan>;
export type PlannedCourse = z.infer<typeof course>;
export type StudyPlanScenarios = z.infer<typeof summaries>;
export type StudyPlanComparison = z.infer<typeof comparison>;
export const studyPlanKeys = {
  current: (userId: string, scenarioNo: number) => ["study-plan", userId, scenarioNo] as const,
  scenarios: (userId: string) => ["study-plan-scenarios", userId] as const,
  comparisons: (userId: string) => ["study-plan-comparison", userId] as const,
  comparison: (userId: string, curriculumId: string, left: number, right: number) =>
    ["study-plan-comparison", userId, curriculumId, left, right] as const,
};

function validScenario(value: number): number { return scenario.parse(value); }

export async function getStudyPlan(scenarioNo = 1, signal?: AbortSignal): Promise<StudyPlan> {
  validScenario(scenarioNo);
  const response = await checkResponse(await fetch(`/api/me/academic/study-plan?scenario=${scenarioNo}`, {
    credentials: "same-origin", cache: "no-store", signal,
  }));
  const result = plan.parse(await response.json());
  if (result.scenarioNo !== scenarioNo) throw new Error("Study plan scenario mismatch");
  return result;
}

export async function getStudyPlanScenarios(signal?: AbortSignal): Promise<StudyPlanScenarios> {
  const response = await checkResponse(await fetch("/api/me/academic/study-plan/scenarios", {
    credentials: "same-origin", cache: "no-store", signal,
  }));
  const result = summaries.parse(await response.json());
  const numbers = result.scenarios.map((item) => item.scenarioNo);
  if (result.curriculum === null ? numbers.length !== 0 : numbers.join(",") !== "1,2,3,4,5")
    throw new Error("Study plan scenarios mismatch");
  return result;
}

export async function getStudyPlanComparison(leftScenario: number, rightScenario: number,
                                              signal?: AbortSignal): Promise<StudyPlanComparison> {
  validScenario(leftScenario); validScenario(rightScenario);
  if (leftScenario === rightScenario) throw new Error("Study plan comparison scenarios must differ");
  const response = await checkResponse(await fetch(
    `/api/me/academic/study-plan/compare?left=${leftScenario}&right=${rightScenario}`, {
      credentials: "same-origin", cache: "no-store", signal,
    }));
  const result = comparison.parse(await response.json());
  if (result.leftScenario !== leftScenario || result.rightScenario !== rightScenario
    || result.left.scenarioNo !== leftScenario || result.right.scenarioNo !== rightScenario)
    throw new Error("Study plan comparison mismatch");
  if (result.curriculum === null && (result.terms.length !== 0 || result.courses.length !== 0
    || result.left.courseCount !== 0 || result.right.courseCount !== 0))
    throw new Error("Study plan comparison without curriculum contains assignments");
  return result;
}

function path(curriculumId: string, courseId: string, scenarioNo: number) {
  return `/api/me/academic/study-plan/curricula/${z.uuid().parse(curriculumId)}/courses/${z.uuid().parse(courseId)}?scenario=${validScenario(scenarioNo)}`;
}

export async function planCourse(curriculumId: string, courseId: string, plannedTerm: number, scenarioNo = 1) {
  if (!Number.isInteger(plannedTerm) || plannedTerm < 1 || plannedTerm > 99)
    throw new Error("Kỳ kế hoạch phải là số nguyên từ 1 đến 99.");
  await authMutation(path(curriculumId, courseId, scenarioNo), JSON.stringify({ plannedTerm }), "PUT");
}

export async function removePlannedCourse(curriculumId: string, courseId: string, scenarioNo = 1) {
  await authMutation(path(curriculumId, courseId, scenarioNo), "{}", "DELETE");
}

export async function copyStudyPlanScenario(sourceScenario: number, targetScenario: number) {
  validScenario(sourceScenario); validScenario(targetScenario);
  if (sourceScenario === targetScenario) throw new Error("Phương án nguồn và đích phải khác nhau.");
  await authMutation(`/api/me/academic/study-plan/scenarios/${targetScenario}/copy`,
    JSON.stringify({ sourceScenario }), "POST");
}

export async function clearStudyPlanScenario(scenarioNo: number) {
  await authMutation(`/api/me/academic/study-plan/scenarios/${validScenario(scenarioNo)}`, "{}", "DELETE");
}
