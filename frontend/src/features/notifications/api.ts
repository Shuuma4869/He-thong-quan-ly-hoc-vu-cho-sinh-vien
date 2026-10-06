import { z } from "zod";
import { authMutation, checkResponse } from "@/features/auth/api";

export const emailStatusSchema = z.object({
  featureEnabled: z.boolean(), configured: z.boolean(), notificationEmail: z.string().nullable(),
  verified: z.boolean(), verifiedAt: z.string().nullable(), syncAlertsEnabled: z.boolean(),
});

export async function getEmailStatus(signal?: AbortSignal) {
  const response = await checkResponse(await fetch("/api/me/notifications/email", {
    credentials: "same-origin", cache: "no-store", signal,
  }));
  return emailStatusSchema.parse(await response.json());
}

export async function requestEmailVerification() {
  await authMutation("/api/me/notifications/email/verification", "{}");
}

export async function confirmEmailVerification(code: string) {
  const response = await authMutation("/api/me/notifications/email/verification/confirm",
    JSON.stringify({ code: code.trim().toUpperCase() }));
  return emailStatusSchema.parse(await response.json());
}
