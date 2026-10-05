import { afterEach, describe, expect, it, vi } from "vitest";
import { getCatalogCourses, getCurriculumCourses } from "./api";

afterEach(() => vi.unstubAllGlobals());
describe("Curriculum API", () => {
  it("uses same-origin no-store GET with encoded query parameters", async () => {
    const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify({ items: [], nextCursor: null })));
    vi.stubGlobal("fetch", fetcher);
    const signal = new AbortController().signal;
    await getCatalogCourses("  A%_  ", "cursor", signal);
    expect(fetcher).toHaveBeenCalledWith("/api/me/academic/catalog/courses?cursor=cursor&search=A%25_", {
      credentials: "same-origin", cache: "no-store", signal,
    });
  });
  it("rejects invalid DTOs rather than displaying invented empty data", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ wrong: true }))));
    await expect(getCatalogCourses("")).rejects.toThrow();
  });
  it("preserves authenticated error status", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("{}", { status: 401 })));
    await expect(getCurriculumCourses("test", "")).rejects.toMatchObject({ status: 401 });
  });
});
