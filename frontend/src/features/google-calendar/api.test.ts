import { afterEach, describe, expect, it, vi } from "vitest";
import { getGoogleConnection, startGoogleAuthorization } from "./api";

afterEach(() => vi.unstubAllGlobals());

describe("Google Calendar API", () => {
  it("reads only safe status metadata", async () => {
    const fetcher = vi.fn().mockResolvedValue(Response.json({
      available: false, status: "DISCONNECTED", calendarReady: false,
      connectedAt: null, lastSuccessfulAccessAt: null,
    }));
    vi.stubGlobal("fetch", fetcher);
    await expect(getGoogleConnection()).resolves.toMatchObject({ available: false, status: "DISCONNECTED" });
    expect(fetcher.mock.calls[0][0]).toBe("/api/me/connections/google-calendar");
    expect(fetcher.mock.calls[0][1]).toMatchObject({ credentials: "same-origin", cache: "no-store" });
  });

  it("starts OAuth through CSRF-protected backend POST and checks the returned URL", async () => {
    const url = "https://accounts.google.com/o/oauth2/v2/auth?client_id=synthetic";
    const fetcher = vi.fn().mockResolvedValueOnce(Response.json({ token: "synthetic-csrf", headerName: "X-CSRF-TOKEN" }))
      .mockResolvedValueOnce(Response.json({ authorizationUrl: url }));
    vi.stubGlobal("fetch", fetcher);
    await expect(startGoogleAuthorization()).resolves.toBe(url);
    expect(fetcher.mock.calls[1][0]).toBe("/api/me/connections/google-calendar/authorize");
    expect(fetcher.mock.calls[1][1].headers["X-CSRF-TOKEN"]).toBe("synthetic-csrf");
    expect(localStorage.getItem("google-token")).toBeNull();
  });

  it("rejects a non-Google redirect even if the backend response is valid JSON", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValueOnce(Response.json({ token: "synthetic-csrf", headerName: "X-CSRF-TOKEN" }))
      .mockResolvedValueOnce(Response.json({ authorizationUrl: "https://example.test/steal" })));
    await expect(startGoogleAuthorization()).rejects.toThrow("Địa chỉ kết nối Google không hợp lệ.");
  });
});
