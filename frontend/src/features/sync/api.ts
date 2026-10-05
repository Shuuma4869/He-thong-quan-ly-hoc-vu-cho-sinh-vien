import { z } from "zod";
import { ApiError, authMutation, checkResponse } from "@/features/auth/api";

const runSchema = z.object({
  runId: z.uuid(),
  status: z.enum(["QUEUED", "RUNNING", "SUCCEEDED", "PARTIAL", "FAILED"]),
  trigger: z.enum(["MANUAL", "SCHEDULED"]),
  requestedAt: z.string(), startedAt: z.string().nullable(), finishedAt: z.string().nullable(),
  nextAttemptAt: z.string().nullable(), currentStep: z.enum(["PROFILE", "CURRICULUM"]).nullable(),
  profileStepStatus: z.enum(["PENDING", "SUCCEEDED", "FAILED"]),
  curriculumStepStatus: z.enum(["PENDING", "SUCCEEDED", "FAILED"]),
  failureCode: z.string().nullable(), attemptCount: z.number().int(),
});
const historySchema = z.object({ items: z.array(runSchema), nextCursor: z.string().nullable() });
const phenikaaSchema = z.object({
  status: z.enum(["CONNECTED", "RECONNECTION_REQUIRED", "DISCONNECTED"]),
  lastAuthenticatedAt: z.string(), lastSuccessfulAccessAt: z.string().nullable(),
  lastFailedAccessAt: z.string().nullable(), lastFailureCode: z.string().nullable(),
  reconnectionRequired: z.boolean(),
});

export type SyncRun = z.infer<typeof runSchema>;
export type PhenikaaConnection = z.infer<typeof phenikaaSchema>;
export const syncKeys = {
  phenikaa: (userId: string) => ["phenikaa-connection", userId] as const,
  current: (userId: string) => ["sync-current", userId] as const,
  run: (userId: string, runId: string) => ["sync-run", userId, runId] as const,
  history: (userId: string) => ["sync-history", userId] as const,
};

export async function getPhenikaaConnection(signal?: AbortSignal): Promise<PhenikaaConnection | null> {
  const response = await fetch("/api/me/connections/phenikaa", { credentials: "same-origin", cache: "no-store", signal });
  if (response.status === 404) return null;
  await checkResponse(response);
  return phenikaaSchema.parse(await response.json());
}

export async function getCurrentRun(signal?: AbortSignal): Promise<SyncRun | null> {
  const response = await fetch("/api/me/sync/current", { credentials: "same-origin", cache: "no-store", signal });
  if (response.status === 404) return null;
  await checkResponse(response);
  return runSchema.parse(await response.json());
}

export async function getRun(runId: string, signal?: AbortSignal): Promise<SyncRun> {
  const response = await checkResponse(await fetch(`/api/me/sync/runs/${encodeURIComponent(runId)}`, {
    credentials: "same-origin", cache: "no-store", signal,
  }));
  return runSchema.parse(await response.json());
}

export async function getHistory(cursor?: string, signal?: AbortSignal) {
  const params = new URLSearchParams({ limit: "10" });
  if (cursor) params.set("cursor", cursor);
  const response = await checkResponse(await fetch(`/api/me/sync/runs?${params}`, {
    credentials: "same-origin", cache: "no-store", signal,
  }));
  return historySchema.parse(await response.json());
}

export async function requestSync(): Promise<SyncRun> {
  const response = await authMutation("/api/me/sync", "{}");
  return runSchema.parse(await response.json());
}

export const isActive = (run: SyncRun) => run.status === "QUEUED" || run.status === "RUNNING";

export function readError(error: Error, unavailable = "Chưa thể đọc dữ liệu. Vui lòng thử lại.") {
  if (error instanceof ApiError && (error.status === 401 || error.status === 403))
    return "Phiên đăng nhập không còn quyền truy cập. Vui lòng đăng nhập lại.";
  return unavailable;
}
