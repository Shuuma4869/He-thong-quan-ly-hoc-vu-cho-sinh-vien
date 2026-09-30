import { z } from "zod";
import { authMutation, checkResponse } from "@/features/auth/api";

export const googleConnectionSchema = z.object({
  available: z.boolean(),
  status: z.enum(["CONNECTED", "SETUP_REQUIRED", "RECONNECTION_REQUIRED", "DISCONNECTED"]),
  calendarReady: z.boolean(),
  connectedAt: z.string().nullable(),
  lastSuccessfulAccessAt: z.string().nullable(),
});
export type GoogleConnection = z.infer<typeof googleConnectionSchema>;

const base = "/api/me/connections/google-calendar";

export async function getGoogleConnection(signal?: AbortSignal): Promise<GoogleConnection> {
  const response = await checkResponse(await fetch(base, { credentials: "same-origin", cache: "no-store", signal }));
  return googleConnectionSchema.parse(await response.json());
}

export async function startGoogleAuthorization(): Promise<string> {
  const response = await authMutation(`${base}/authorize`, "{}");
  const { authorizationUrl } = z.object({ authorizationUrl: z.url() }).parse(await response.json());
  const url = new URL(authorizationUrl);
  if (url.protocol !== "https:" || url.hostname !== "accounts.google.com" || url.pathname !== "/o/oauth2/v2/auth")
    throw new Error("Địa chỉ kết nối Google không hợp lệ.");
  return authorizationUrl;
}

export function openGoogleAuthorization(authorizationUrl: string): void {
  window.location.assign(authorizationUrl);
}

export async function retryGoogleSetup(): Promise<GoogleConnection> {
  const response = await authMutation(`${base}/setup`, "{}");
  return googleConnectionSchema.parse(await response.json());
}

export async function disconnectGoogle(): Promise<void> {
  const response = await authMutation(`${base}/disconnect`, "{}");
  z.object({ remoteRevocationConfirmed: z.boolean() }).parse(await response.json());
}
