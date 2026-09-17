import { z } from "zod";

const healthSchema = z.object({
  status: z.literal("UP"),
  service: z.literal("ams-api"),
  timestamp: z.iso.datetime(),
});

export async function getApiHealth(signal?: AbortSignal) {
  const baseUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
  const timeout = AbortSignal.timeout(5_000);
  const response = await fetch(`${baseUrl.replace(/\/$/, "")}/api/health`, {
    headers: { Accept: "application/json" },
    signal: signal ? AbortSignal.any([signal, timeout]) : timeout,
    cache: "no-store",
  });
  if (!response.ok) throw new Error(`Health check failed (${response.status})`);
  return healthSchema.parse(await response.json());
}
