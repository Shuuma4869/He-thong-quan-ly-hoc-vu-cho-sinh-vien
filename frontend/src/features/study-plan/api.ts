import { z } from "zod";
import { authMutation, checkResponse } from "@/features/auth/api";

const course = z.object({
  courseId: z.uuid(), code: z.string(), name: z.string(), credits: z.number().finite().nonnegative(),
  requirement: z.enum(["REQUIRED", "ELECTIVE"]), groupName: z.string().nullable(),
}).strict();
const term = z.object({
  plannedTerm: z.number().int().min(1).max(99), courseCount: z.number().int().nonnegative(),
  plannedCredits: z.number().finite().nonnegative(), courses: z.array(course),
}).strict();
const plan = z.object({
  mode: z.literal("USER_PLANNED_AMS"),
  curriculum: z.object({ id: z.uuid(), code: z.string(), name: z.string() }).strict().nullable(),
  terms: z.array(term),
}).strict();

export type StudyPlan = z.infer<typeof plan>;
export type PlannedCourse = z.infer<typeof course>;
export const studyPlanKeys = { current: (userId: string) => ["study-plan", userId] as const };

export async function getStudyPlan(signal?: AbortSignal): Promise<StudyPlan> {
  const response = await checkResponse(await fetch("/api/me/academic/study-plan", {
    credentials: "same-origin", cache: "no-store", signal,
  }));
  return plan.parse(await response.json());
}

function path(curriculumId: string, courseId: string) {
  return `/api/me/academic/study-plan/curricula/${z.uuid().parse(curriculumId)}/courses/${z.uuid().parse(courseId)}`;
}

export async function planCourse(curriculumId: string, courseId: string, plannedTerm: number) {
  if (!Number.isInteger(plannedTerm) || plannedTerm < 1 || plannedTerm > 99)
    throw new Error("Kỳ kế hoạch phải là số nguyên từ 1 đến 99.");
  await authMutation(path(curriculumId, courseId), JSON.stringify({ plannedTerm }), "PUT");
}

export async function removePlannedCourse(curriculumId: string, courseId: string) {
  await authMutation(path(curriculumId, courseId), "{}", "DELETE");
}
