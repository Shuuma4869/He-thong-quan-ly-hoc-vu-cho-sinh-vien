import { z } from "zod";
import { currentUserSchema, settingsSchema, type Settings } from "./schema";

export class ApiError extends Error {
  constructor(public readonly status: number, message: string) { super(message); }
}

async function checkResponse(response: Response) {
  if (response.ok) return response;
  const fallback = response.status === 403 ? "Phiên thao tác đã hết hạn. Vui lòng thử lại." : "Không thể hoàn thành yêu cầu. Vui lòng thử lại.";
  const problem = await response.json().catch(() => null);
  throw new ApiError(response.status, typeof problem?.detail === "string" ? problem.detail : fallback);
}

export async function getCurrentUser(signal?: AbortSignal) {
  const response = await fetch("/api/me", { credentials: "same-origin", cache: "no-store", signal });
  if (response.status === 401 || response.status === 403) return null;
  await checkResponse(response);
  return currentUserSchema.parse(await response.json());
}

export async function authMutation(path: string, body: BodyInit, method = "POST", form = false) {
  const csrfResponse = await checkResponse(await fetch("/api/auth/csrf", { credentials: "same-origin", cache: "no-store" }));
  const csrf = z.object({ token: z.string(), headerName: z.literal("X-CSRF-TOKEN") }).parse(await csrfResponse.json());
  return checkResponse(await fetch(path, {
    method, credentials: "same-origin", cache: "no-store", body,
    headers: { "Content-Type": form ? "application/x-www-form-urlencoded" : "application/json", [csrf.headerName]: csrf.token },
  }));
}

export async function login(values: { email: string; password: string }) {
  await authMutation("/api/auth/login", new URLSearchParams(values), "POST", true);
}

export async function register(values: { email: string; password: string; displayName: string }) {
  await authMutation("/api/auth/register", JSON.stringify(values));
}

export async function logout() { await authMutation("/api/auth/logout", "{}"); }

export async function saveSettings(values: Settings) {
  const response = await authMutation("/api/me/settings", JSON.stringify(values), "PUT");
  return settingsSchema.parse(await response.json());
}
