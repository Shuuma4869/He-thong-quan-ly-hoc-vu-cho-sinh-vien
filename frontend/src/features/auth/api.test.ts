import { afterEach, describe, expect, it, vi } from "vitest";
import { getCurrentUser, login, logout } from "./api";
import { passwordSchema, registerSchema } from "./schema";

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
});
