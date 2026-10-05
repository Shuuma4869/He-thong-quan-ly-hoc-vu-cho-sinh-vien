import { afterEach, describe, expect, it, vi } from "vitest";
import { getCurrentRun, getHistory, getPhenikaaConnection, getRun } from "./api";
import { failureMessage, localTime, runStatus } from "./presentation";

const id = "10000000-0000-4000-8000-000000000001";
const run = { runId: id, status: "QUEUED", trigger: "MANUAL", requestedAt: "2026-01-01T00:00:00Z",
  startedAt: null, finishedAt: null, nextAttemptAt: null, currentStep: null,
  profileStepStatus: "PENDING", curriculumStepStatus: "PENDING", failureCode: null, attemptCount: 0 };

afterEach(() => vi.unstubAllGlobals());

describe("Sync API", () => {
  it("distinguishes a disabled Phenikaa endpoint and no recorded run", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ status: 404 });
    vi.stubGlobal("fetch", fetchMock);
    expect(await getPhenikaaConnection()).toBeNull();
    expect(await getCurrentRun()).toBeNull();
    expect(fetchMock).toHaveBeenCalledWith("/api/me/connections/phenikaa", expect.objectContaining({ credentials: "same-origin" }));
  });

  it("parses owned runs and passes cursor to a bounded history request", async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce({ ok: true, json: async () => run })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ items: [run], nextCursor: "next" }) });
    vi.stubGlobal("fetch", fetchMock);
    expect((await getRun(id)).status).toBe("QUEUED");
    expect((await getHistory("cursor")).nextCursor).toBe("next");
    expect(fetchMock.mock.calls[1][0]).toBe("/api/me/sync/runs?limit=10&cursor=cursor");
  });

  it("keeps failure codes and timestamps safe for display", () => {
    expect(runStatus.PARTIAL).toBe("Đồng bộ hoàn tất một phần");
    expect(failureMessage("SOURCE_TIMEOUT")).toMatch(/quá chậm/);
    expect(failureMessage("PRIVATE_STACK_TRACE")).not.toContain("PRIVATE_STACK_TRACE");
    expect(localTime("invalid")).toBe("Không xác định");
  });
});
