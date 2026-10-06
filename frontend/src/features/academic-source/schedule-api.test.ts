import { afterEach, describe, expect, it, vi } from "vitest";
import { getExamPeriods, getExams, getSchedule, sourceErrorMessage, AcademicSourceError } from "./api";

const periodRef = `ep_${"a".repeat(64)}`;
afterEach(() => vi.unstubAllGlobals());

describe("Schedule and exam source API", () => {
  it("uses read-only requests and validates nullable schedule fields", async () => {
    const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      completeness: "UNKNOWN", identityScope: "UNVERIFIED", zone: "Asia/Ho_Chi_Minh",
      from: "2026-10-01", through: "2026-10-01", entries: [{ courseName: null, date: "2026-10-01",
        startsAt: null, endsAt: null, room: null, lecturer: null, kind: "UNKNOWN" }],
    })));
    vi.stubGlobal("fetch", fetcher);
    expect((await getSchedule("2026-10-01", "2026-10-01")).entries[0].startsAt).toBeNull();
    expect(fetcher).toHaveBeenCalledWith("/api/me/academic/source/schedule?from=2026-10-01&through=2026-10-01",
      expect.objectContaining({ credentials: "same-origin", cache: "no-store" }));
    expect(() => getSchedule("bad", "2026-10-01")).toThrow();
  });

  it("rejects invented completeness, identity and candidate IDs", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({
      completeness: "COMPLETE", identityScope: "STABLE", zone: "Asia/Ho_Chi_Minh",
      from: "2026-10-01", through: "2026-10-01", entries: [],
    }))));
    await expect(getSchedule("2026-10-01", "2026-10-01")).rejects.toThrow();
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({
      completeness: "UNKNOWN", identityScope: "UNVERIFIED", zone: "Asia/Ho_Chi_Minh",
      from: "2026-10-01", through: "2026-10-01", entries: [{ courseName: null, date: "2026-10-01",
        startsAt: null, endsAt: null, room: null, lecturer: null, kind: "CLASS", scheduleId: "raw" }],
    }))));
    await expect(getSchedule("2026-10-01", "2026-10-01")).rejects.toThrow();
  });

  it("only accepts opaque period references and source-safe exam observations", async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({ completeness: "UNKNOWN",
      periods: [{ periodRef, label: "Kỳ nguồn giả định" }] }))).mockResolvedValueOnce(new Response(JSON.stringify({
      completeness: "UNKNOWN", identityScope: "UNVERIFIED", zone: "Asia/Ho_Chi_Minh",
      period: { periodRef, label: "Kỳ nguồn giả định" }, entries: [{ courseCode: "TEST101",
        courseName: "Môn kiểm thử", examAttempt: 2, examSession: null, date: "2026-10-01",
        startsAt: "08:30:00", endsAt: null, room: null }],
    })));
    vi.stubGlobal("fetch", fetcher);
    expect((await getExamPeriods()).periods[0].periodRef).toBe(periodRef);
    expect((await getExams(periodRef)).entries[0].examAttempt).toBe(2);
    expect(fetcher).toHaveBeenCalledWith(`/api/me/academic/source/exams?periodRef=${periodRef}`,
      expect.objectContaining({ credentials: "same-origin", cache: "no-store" }));
    expect(() => getExams("private-period-id")).toThrow();
  });

  it("maps safe range and stale-period errors without exposing provider details", () => {
    expect(sourceErrorMessage(new AcademicSourceError(400, "INVALID_SOURCE_RANGE"))).toContain("31 ngày");
    expect(sourceErrorMessage(new AcademicSourceError(404, "INVALID_SOURCE_REFERENCE"), "exam-period"))
      .toContain("Danh sách kỳ thi đã thay đổi");
    expect(sourceErrorMessage(new AcademicSourceError(429, "RATE_LIMITED"))).toContain("Vui lòng chờ");
  });
});
