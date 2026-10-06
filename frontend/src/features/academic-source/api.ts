import { z } from "zod";

const completeness = z.literal("UNKNOWN");
const statusCompleteness = z.enum(["SOURCE_VERIFIED", "UNKNOWN"]);
const programRef = z.string().regex(/^pr_[0-9a-f]{64}$/);
const registrationRef = z.string().regex(/^rg_[0-9a-f]{64}$/);
const detailRef = z.string().regex(/^dt_[0-9a-f]{64}$/);
const score = z.number().finite();

const statusSchema = z.object({
  connectionState: z.enum(["CONNECTED", "RECONNECTION_REQUIRED", "DISCONNECTED"]),
  lastSuccessfulAccessAt: z.string().nullable(),
  capabilities: z.array(z.object({
    capability: z.string(),
    mode: z.enum(["PERSISTED", "PERSISTED_PARTIAL", "LIVE_READ_ONLY", "ADAPTER_READ_ONLY_NO_API", "BLOCKED_SOURCE_LIMIT", "BLOCKED_PARTIAL"]),
    completeness: statusCompleteness,
  })),
});
const programsSchema = z.object({
  completeness,
  programs: z.array(z.object({ programRef, label: z.string() })),
});
const componentSchema = z.object({
  code: z.string(), name: z.string(), examAttempt: z.number().int(), score,
});
const recordsSchema = z.object({
  completeness,
  unknownSemantics: z.object({
    creditsEarned: z.literal("UNKNOWN"),
    includedInGpa: z.literal("UNKNOWN"),
    currentResult: z.literal("UNKNOWN"),
  }),
  records: z.array(z.object({
    registrationRef,
    course: z.object({ code: z.string(), name: z.string(), credits: score }),
    semester: z.object({ academicYearStart: z.number().int(), termCode: z.string(), code: z.string() }),
    reportedLearningAttempt: z.number().int(),
    components: z.array(componentSchema),
    finalResult: z.object({
      detailRef, examAttempt: z.number().int(),
      outcome: z.enum(["PASSED", "FAILED", "RETAKE_REQUIRED"]),
      numericScore: score.nullable(), letterGrade: z.string().nullable(), gradePoints: score.nullable(),
    }).nullable(),
  })),
});
const detailSchema = z.object({
  detailRef, completeness,
  components: z.array(componentSchema.extend({ registrationRef })),
});

export type SourceStatus = z.infer<typeof statusSchema>;
export type Programs = z.infer<typeof programsSchema>;
export type Records = z.infer<typeof recordsSchema>;
export type AcademicRecord = Records["records"][number];
export type ResultDetail = z.infer<typeof detailSchema>;
export type SourceErrorCode =
  | "CONNECTION_NOT_FOUND" | "RECONNECTION_REQUIRED" | "SOURCE_UNAVAILABLE" | "SOURCE_TIMEOUT"
  | "SOURCE_SCHEMA_CHANGED" | "SOURCE_DATA_INCOMPLETE" | "INVALID_SOURCE_REFERENCE" | "RATE_LIMITED";

const knownCode = z.enum([
  "CONNECTION_NOT_FOUND", "RECONNECTION_REQUIRED", "SOURCE_UNAVAILABLE", "SOURCE_TIMEOUT",
  "SOURCE_SCHEMA_CHANGED", "SOURCE_DATA_INCOMPLETE", "INVALID_SOURCE_REFERENCE", "RATE_LIMITED",
]);

export class AcademicSourceError extends Error {
  constructor(public readonly status: number, public readonly code: SourceErrorCode | null) {
    super("Academic source request failed");
  }
}

export const academicKeys = {
  status: (userId: string) => ["academic-source-status", userId] as const,
  programs: (userId: string) => ["academic-source-programs", userId] as const,
  records: (userId: string, ref: string) => ["academic-source-records", userId, ref] as const,
  detail: (userId: string, program: string, detail: string) => ["academic-source-detail", userId, program, detail] as const,
};

async function read<T>(path: string, schema: z.ZodType<T>, signal?: AbortSignal): Promise<T> {
  const response = await fetch(`/api/me/academic/source/${path}`, {
    credentials: "same-origin", cache: "no-store", signal,
  });
  if (!response.ok) {
    const problem = await response.json().catch(() => null);
    const code = knownCode.safeParse(problem?.code);
    throw new AcademicSourceError(response.status, code.success ? code.data : null);
  }
  return schema.parse(await response.json());
}

export const getSourceStatus = (signal?: AbortSignal) => read("status", statusSchema, signal);
export const getPrograms = (signal?: AbortSignal) => read("programs", programsSchema, signal);
export const getRecords = (ref: string, signal?: AbortSignal) =>
  read(`records?programRef=${encodeURIComponent(programRef.parse(ref))}`, recordsSchema, signal);
export const getResultDetail = (program: string, detail: string, signal?: AbortSignal) =>
  read(`records/${encodeURIComponent(detailRef.parse(detail))}/detail?programRef=${encodeURIComponent(programRef.parse(program))}`, detailSchema, signal);

const messages: Record<SourceErrorCode, string> = {
  CONNECTION_NOT_FOUND: "Chưa có kết nối học vụ có thể sử dụng.",
  RECONNECTION_REQUIRED: "Phiên học vụ đã hết hạn; cần kết nối lại.",
  RATE_LIMITED: "Vui lòng chờ trước khi đọc lại dữ liệu học vụ.",
  SOURCE_TIMEOUT: "Cổng học vụ phản hồi quá chậm.",
  SOURCE_SCHEMA_CHANGED: "Dữ liệu nguồn đã thay đổi cấu trúc; AMS chưa thể đọc an toàn.",
  SOURCE_DATA_INCOMPLETE: "Dữ liệu nguồn chưa nhất quán hoặc vượt giới hạn an toàn.",
  SOURCE_UNAVAILABLE: "Nguồn học vụ tạm thời không sẵn sàng.",
  INVALID_SOURCE_REFERENCE: "Dữ liệu đã thay đổi; hãy đọc lại danh sách.",
};

export function sourceErrorMessage(error: Error): string {
  if (error instanceof AcademicSourceError) {
    if (error.status === 401 || error.status === 403) return "Phiên đăng nhập không còn quyền truy cập. Vui lòng đăng nhập lại.";
    if (error.code) return messages[error.code];
  }
  return "Chưa thể đọc dữ liệu học vụ từ nguồn.";
}

export const hasLiveCapability = (status: SourceStatus, capability: string) =>
  status.connectionState === "CONNECTED" && status.capabilities.some((item) =>
    item.capability === capability && item.mode === "LIVE_READ_ONLY" && item.completeness === "UNKNOWN");
