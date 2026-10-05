import { afterEach, describe, expect, it, vi } from "vitest";
import { AcademicSourceError, getPrograms, getRecords, getResultDetail, getSourceStatus, sourceErrorMessage } from "./api";

const program = `pr_${"a".repeat(64)}`;
const detail = `dt_${"b".repeat(64)}`;
afterEach(() => vi.unstubAllGlobals());

describe("Academic source API", () => {
  it("uses same-origin read-only requests and validates opaque references", async () => {
    const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify({ completeness: "UNKNOWN", records: [],
      unknownSemantics: { creditsEarned: "UNKNOWN", includedInGpa: "UNKNOWN", currentResult: "UNKNOWN" } }), { status: 200 }));
    vi.stubGlobal("fetch", fetcher);
    await getRecords(program);
    expect(fetcher).toHaveBeenCalledWith(`/api/me/academic/source/records?programRef=${program}`,
      expect.objectContaining({ credentials: "same-origin", cache: "no-store" }));
    expect(() => getRecords("student-id-or-raw-url")).toThrow();
    expect(fetcher).toHaveBeenCalledTimes(1);
  });

  it("rejects an unsupported completeness value instead of treating it as complete", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ completeness: "COMPLETE", programs: [] }))));
    await expect(getPrograms()).rejects.toThrow();
  });

  it("does not expose server details when the source fails", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: "SOURCE_SCHEMA_CHANGED", detail: "private data" }), { status: 502 })));
    try { await getSourceStatus(); throw new Error("expected an API error"); }
    catch (error) {
      expect(error).toBeInstanceOf(AcademicSourceError);
      expect(sourceErrorMessage(error as Error)).toContain("thay đổi cấu trúc");
      expect(sourceErrorMessage(error as Error)).not.toContain("private data");
    }
  });

  it("does not accept a malformed detail reference", async () => {
    const fetcher = vi.fn(); vi.stubGlobal("fetch", fetcher);
    expect(() => getResultDetail(program, `${detail}/other`)).toThrow();
    expect(fetcher).not.toHaveBeenCalled();
  });

  it.each([
    ["CONNECTION_NOT_FOUND", "Chưa có kết nối"],
    ["RECONNECTION_REQUIRED", "cần kết nối lại"],
    ["RATE_LIMITED", "Vui lòng chờ"],
    ["SOURCE_TIMEOUT", "quá chậm"],
    ["SOURCE_SCHEMA_CHANGED", "thay đổi cấu trúc"],
    ["SOURCE_DATA_INCOMPLETE", "chưa nhất quán"],
    ["SOURCE_UNAVAILABLE", "không sẵn sàng"],
    ["INVALID_SOURCE_REFERENCE", "đọc lại danh sách"],
  ] as const)("maps %s without revealing a server message", async (code, label) => {
    vi.stubGlobal("fetch", vi.fn().mockImplementation(() => Promise.resolve(new Response(JSON.stringify({ code, detail: "private source data" }), { status: 502 }))));
    try { await getSourceStatus(); } catch (error) {
      expect(error).toMatchObject({ code });
      expect(sourceErrorMessage(error as Error)).toContain(label);
      expect(sourceErrorMessage(error as Error)).not.toContain("private source data");
    }
  });

  it("falls back safely for an unknown server code", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: "UNKNOWN_SERVER_CODE", detail: "private source data" }), { status: 502 })));
    try { await getSourceStatus(); } catch (error) {
      expect(sourceErrorMessage(error as Error)).toBe("Chưa thể đọc dữ liệu học vụ từ nguồn.");
    }
  });
});
