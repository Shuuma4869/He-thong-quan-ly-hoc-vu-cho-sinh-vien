import "server-only";
import { cache } from "react";
import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { currentUserSchema } from "./schema";

export const getServerUser = cache(async () => {
  const session = (await cookies()).get("AMS_SESSION");
  if (!session || !/^[A-Za-z0-9+/_=-]+$/.test(session.value)) return null;
  const backend = process.env.API_INTERNAL_URL ?? "http://localhost:8080";
  const response = await fetch(`${backend}/api/me`, {
    headers: { Cookie: `AMS_SESSION=${session.value}` },
    cache: "no-store", signal: AbortSignal.timeout(5_000),
  });
  if (response.status === 401 || response.status === 403) return null;
  if (!response.ok) throw new Error("Không thể kiểm tra phiên đăng nhập.");
  return currentUserSchema.parse(await response.json());
});

export async function requireCurrentUser() {
  const user = await getServerUser();
  if (!user) redirect("/login");
  return user;
}
