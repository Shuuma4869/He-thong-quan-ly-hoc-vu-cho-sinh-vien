import { z } from "zod";
import { authMutation, checkResponse } from "@/features/auth/api";

const requirement = z.enum(["REQUIRED", "ELECTIVE"]);
const curriculumSchema = z.object({
  id: z.uuid(), code: z.string(), name: z.string(), cohort: z.string().nullable(), revision: z.string().nullable(),
  minimumCredits: z.number(), courseCount: z.number(), groupCount: z.number(),
});
const groupSchema = z.object({
  id: z.uuid(), code: z.string(), name: z.string(), requirement,
  minimumCredits: z.number().nullable(), minimumCourseCount: z.number().nullable(),
});
const courseSchema = z.object({
  id: z.uuid(), courseId: z.uuid(), code: z.string(), name: z.string(), credits: z.number(), requirement,
  groupId: z.uuid().nullable(), groupName: z.string().nullable(), recommendedTerm: z.number().nullable(),
});
const catalogCourseSchema = z.object({
  id: z.uuid(), code: z.string(), name: z.string(), credits: z.number(), curriculumLinked: z.boolean(),
});
const page = <T extends z.ZodType>(item: T) => z.object({ items: z.array(item), nextCursor: z.string().nullable() });
const detailSchema = z.object({ curriculum: curriculumSchema, groups: page(groupSchema) });
const selectionSchema = z.object({
  selectionMode: z.literal("USER_SELECTED_AMS"),
  curriculum: curriculumSchema.pick({ id: true, code: true, name: true, cohort: true, revision: true, minimumCredits: true }).nullable(),
});

export type Curriculum = z.infer<typeof curriculumSchema>;
export type CurriculumCourse = z.infer<typeof courseSchema>;
export type CatalogCourse = z.infer<typeof catalogCourseSchema>;
export type Page<T> = { items: T[]; nextCursor: string | null };
export type CurriculumSelection = z.infer<typeof selectionSchema>;

export const curriculumKeys = {
  selection: (userId: string) => ["curriculum-selection", userId] as const,
  list: (userId: string) => ["curricula", userId] as const,
  detail: (userId: string, id: string) => ["curriculum", userId, id] as const,
};

async function read<T>(path: string, schema: z.ZodType<T>, cursor?: string, search?: string, signal?: AbortSignal): Promise<T> {
  const params = new URLSearchParams();
  if (cursor) params.set("cursor", cursor);
  if (search?.trim()) params.set("search", search.trim());
  const response = await checkResponse(await fetch(`/api/me/academic/${path}?${params}`, {
    credentials: "same-origin", cache: "no-store", signal,
  }));
  return schema.parse(await response.json());
}

export const getCurricula = (cursor?: string, signal?: AbortSignal) => read("curricula", page(curriculumSchema), cursor, undefined, signal);
export const getCurriculum = (id: string, cursor?: string, signal?: AbortSignal) => read(`curricula/${encodeURIComponent(id)}`, detailSchema, cursor, undefined, signal);
export const getCurriculumCourses = (id: string, search: string, cursor?: string, signal?: AbortSignal) =>
  read(`curricula/${encodeURIComponent(id)}/courses`, page(courseSchema), cursor, search, signal);
export const getCatalogCourses = (search: string, cursor?: string, signal?: AbortSignal) =>
  read("catalog/courses", page(catalogCourseSchema), cursor, search, signal);

export async function getCurriculumSelection(signal?: AbortSignal): Promise<CurriculumSelection> {
  const response = await checkResponse(await fetch("/api/me/academic/curriculum-selection", {
    credentials: "same-origin", cache: "no-store", signal,
  }));
  return selectionSchema.parse(await response.json());
}

export async function selectCurriculum(id: string): Promise<CurriculumSelection> {
  const response = await authMutation("/api/me/academic/curriculum-selection", JSON.stringify({ curriculumId: id }), "PUT");
  return selectionSchema.parse(await response.json());
}

export async function clearCurriculumSelection(): Promise<CurriculumSelection> {
  const response = await authMutation("/api/me/academic/curriculum-selection", "{}", "DELETE");
  return selectionSchema.parse(await response.json());
}
