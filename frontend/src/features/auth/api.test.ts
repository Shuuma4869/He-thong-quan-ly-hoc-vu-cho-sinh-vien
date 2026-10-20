import { afterEach, describe, expect, it, vi } from "vitest";
import { changeAccountPassword, getAccountSessions, getCurrentUser, login, logout, revokeAccountSession } from "./api";
import { accountSessionsSchema, passwordChangeSchema, passwordSchema, registerSchema } from "./schema";

afterEach(() => vi.unstubAllGlobals());

describe("account authentication", () => {
  it("treats an expired session as unauthenticated", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status: 401 })));
    await expect(getCurrentUser()).resolves.toBeNull();
  });

  it("does not treat a network failure as a successful login or logout", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("Offline")));
    await expect(login({ email: "student@example.test", password: "Phase1-test-password" })).rejects.toThrow("Offline");
    await expect(logout()).rejects.toThrow("Offline");
  });

  it("fetches a fresh CSRF token and sends credentials through the form body", async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(Response.json({ token: "test-token", headerName: "X-CSRF-TOKEN" }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetcher);
    await login({ email: "student@example.test", password: "Phase1-test-password" });
    expect(fetcher.mock.calls[0][0]).toBe("/api/auth/csrf");
    const [url, options] = fetcher.mock.calls[1];
    expect(url).toBe("/api/auth/login");
    expect(options.headers["X-CSRF-TOKEN"]).toBe("test-token");
    expect(options.credentials).toBe("same-origin");
    expect(options.body.get("password")).toBe("Phase1-test-password");
  });

  it("rejects wrong credentials without retaining a current user", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValueOnce(Response.json({ token: "test-token", headerName: "X-CSRF-TOKEN" }))
      .mockResolvedValueOnce(Response.json({ detail: "Email hoặc mật khẩu không đúng." }, { status: 401 })));
    await expect(login({ email: "student@example.test", password: "Phase1-test-password" })).rejects.toMatchObject({ status: 401 });
  });

  it("validates email, display name, password length and the BCrypt byte limit", () => {
    expect(registerSchema.safeParse({ email: "invalid", displayName: "", password: "short" }).success).toBe(false);
    expect(passwordSchema.safeParse("ậ".repeat(30)).success).toBe(false);
    expect(passwordSchema.safeParse("a".repeat(64)).success).toBe(true);
  });

  it("validates password confirmation and rejects malformed session references", () => {
    expect(passwordChangeSchema.safeParse({ currentPassword: "current-password", newPassword: "new-password-123",
      confirmPassword: "different-password" }).success).toBe(false);
    expect(passwordChangeSchema.safeParse({ currentPassword: "same-password-123", newPassword: "same-password-123",
      confirmPassword: "same-password-123" }).success).toBe(false);
    expect(accountSessionsSchema.safeParse([{ sessionRef: "raw-cookie", current: true,
      createdAt: "2026-10-20T00:00:00Z", lastAccessedAt: "2026-10-20T00:00:00Z",
      expiresAt: "2026-10-20T00:30:00Z" }]).success).toBe(false);
  });

  it("reads sessions without caching and validates the response", async () => {
    const session = { sessionRef: "a".repeat(64), current: true, createdAt: "2026-10-20T00:00:00Z",
      lastAccessedAt: "2026-10-20T00:00:00Z", expiresAt: "2026-10-20T00:30:00Z" };
    const fetcher = vi.fn().mockResolvedValue(Response.json([session]));
    vi.stubGlobal("fetch", fetcher);
    await expect(getAccountSessions()).resolves.toEqual([session]);
    expect(fetcher).toHaveBeenCalledWith("/api/me/security/sessions", expect.objectContaining({ cache: "no-store" }));
  });

  it("sends password only in the JSON mutation body and never sends confirmation", async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(Response.json({ token: "test-token", headerName: "X-CSRF-TOKEN" }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetcher);
    await changeAccountPassword("current-password", "new-password-123");
    const [url, options] = fetcher.mock.calls[1];
    expect(url).toBe("/api/me/security/password");
    expect(JSON.parse(options.body)).toEqual({ currentPassword: "current-password", newPassword: "new-password-123" });
    expect(options.headers["X-CSRF-TOKEN"]).toBe("test-token");
    expect(JSON.stringify(fetcher.mock.calls[0])).not.toContain("current-password");
  });

  it("uses CSRF for session revocation", async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(Response.json({ token: "test-token", headerName: "X-CSRF-TOKEN" }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetcher);
    await revokeAccountSession("a".repeat(64));
    expect(fetcher.mock.calls[1][1]).toMatchObject({ method: "DELETE", headers: expect.objectContaining({ "X-CSRF-TOKEN": "test-token" }) });
  });
});
