import { afterEach, describe, expect, it, vi } from "vitest";
import { getApiHealth } from "./health";

afterEach(() => vi.unstubAllGlobals());

describe("getApiHealth", () => {
  it("accepts the backend health contract", async () => {
    const payload = { status: "UP", service: "ams-api", timestamp: "2026-09-17T00:00:00Z" };
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json(payload)));
    await expect(getApiHealth()).resolves.toEqual(payload);
  });

  it("rejects an unavailable backend", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status: 503 })));
    await expect(getApiHealth()).rejects.toThrow("503");
  });

  it("rejects an invalid response instead of displaying success", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json({ status: "UP" })));
    await expect(getApiHealth()).rejects.toThrow();
  });
});
