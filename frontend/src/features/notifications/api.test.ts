import { afterEach, expect, it, vi } from "vitest";
import { confirmEmailVerification, getEmailStatus, requestEmailVerification } from "./api";

afterEach(() => vi.unstubAllGlobals());

it("uses CSRF for verification and does not send a recipient supplied by the client", async () => {
  const fetcher = vi.fn().mockResolvedValueOnce(Response.json({ token: "synthetic-csrf", headerName: "X-CSRF-TOKEN" }))
    .mockResolvedValueOnce(new Response(null, { status: 202 }))
    .mockResolvedValueOnce(Response.json({ token: "synthetic-csrf", headerName: "X-CSRF-TOKEN" }))
    .mockResolvedValueOnce(Response.json({ featureEnabled: true, configured: true,
      notificationEmail: "notify@example.test", verified: true, verifiedAt: "2026-10-06T00:00:00Z",
      syncAlertsEnabled: false }));
  vi.stubGlobal("fetch", fetcher);
  await requestEmailVerification();
  await confirmEmailVerification("23456789ab");
  expect(fetcher.mock.calls[1][0]).toBe("/api/me/notifications/email/verification");
  expect(fetcher.mock.calls[1][1].headers["X-CSRF-TOKEN"]).toBe("synthetic-csrf");
  expect(fetcher.mock.calls[3][1].body).toBe(JSON.stringify({ code: "23456789AB" }));
  expect(fetcher.mock.calls[3][1].body).not.toContain("notify@example.test");
});

it("rejects a malformed status instead of assuming email is verified", async () => {
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json({ verified: true })));
  await expect(getEmailStatus()).rejects.toThrow();
});
