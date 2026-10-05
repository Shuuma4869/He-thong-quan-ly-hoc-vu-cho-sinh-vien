import { z } from "zod";
import { checkResponse } from "@/features/auth/api";

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

export type Curriculum = z.infer<typeof curriculumSchema>;
export type CurriculumCourse = z.infer<typeof courseSchema>;
export type CatalogCourse = z.infer<typeof catalogCourseSchema>;
export type Page<T> = { items: T[]; nextCursor: string | null };

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
