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

export type StudyPlan = z.infer<typeof plan>;
export type PlannedCourse = z.infer<typeof course>;
export type StudyPlanScenarios = z.infer<typeof summaries>;
export const studyPlanKeys = {
  current: (userId: string, scenarioNo: number) => ["study-plan", userId, scenarioNo] as const,
  scenarios: (userId: string) => ["study-plan-scenarios", userId] as const,
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
