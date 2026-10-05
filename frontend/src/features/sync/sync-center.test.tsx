import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { SyncCenter } from "./sync-center";
import * as api from "./api";

vi.mock("next/link", () => ({ default: ({ children, href }: { children: React.ReactNode; href: string }) => <a href={href}>{children}</a> }));
vi.mock("./api", async (importOriginal) => ({ ...(await importOriginal<typeof import("./api")>()),
  getPhenikaaConnection: vi.fn(), getCurrentRun: vi.fn(), getRun: vi.fn(), getHistory: vi.fn(), requestSync: vi.fn(),
}));

const id = "10000000-0000-4000-8000-000000000001";
const queued: api.SyncRun = { runId: id, status: "QUEUED", trigger: "MANUAL", requestedAt: "2026-01-01T00:00:00Z",
  startedAt: null, finishedAt: null, nextAttemptAt: null, currentStep: null,
  profileStepStatus: "PENDING", curriculumStepStatus: "PENDING", failureCode: null, attemptCount: 0 };
const connection: api.PhenikaaConnection = { status: "CONNECTED", lastAuthenticatedAt: "2026-01-01T00:00:00Z",
  lastSuccessfulAccessAt: null, lastFailedAccessAt: null, lastFailureCode: null, reconnectionRequired: false };

function show(seed?: (client: QueryClient) => void) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 10_000 } } });
  seed?.(client);
  return { client, ...render(<QueryClientProvider client={client}><SyncCenter userId="synthetic-user" /></QueryClientProvider>) };
}
beforeEach(() => {
  vi.mocked(api.getPhenikaaConnection).mockResolvedValue(connection);
  vi.mocked(api.getCurrentRun).mockResolvedValue(null);
  vi.mocked(api.getHistory).mockResolvedValue({ items: [], nextCursor: null });
  vi.mocked(api.getRun).mockResolvedValue(queued);
  vi.mocked(api.requestSync).mockResolvedValue(queued);
});
afterEach(() => { cleanup(); vi.resetAllMocks(); });

describe("Sync Center", () => {
  it("disables manual sync when the source is unavailable", async () => {
    vi.mocked(api.getPhenikaaConnection).mockResolvedValue(null);
    show();
    expect(await screen.findByText("Không khả dụng: tích hợp Phenikaa chưa được bật trên máy chủ.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Đồng bộ ngay" })).toBeDisabled();
  });

  it("starts one run and prevents another click while active", async () => {
    show();
    const button = await screen.findByRole("button", { name: "Đồng bộ ngay" });
    await waitFor(() => expect(button).toBeEnabled());
    await userEvent.click(button);
    await waitFor(() => expect(api.requestSync).toHaveBeenCalledOnce());
    expect(button).toBeDisabled();
    expect(await screen.findByText(/Đã có lượt đồng bộ đang hoạt động/)).toBeInTheDocument();
  });

  it("resumes an active run after opening the page and shows partial steps safely", async () => {
    vi.mocked(api.getCurrentRun).mockResolvedValue(queued);
    vi.mocked(api.getRun).mockResolvedValue({ ...queued, status: "PARTIAL", finishedAt: "2026-01-01T00:01:00Z",
      profileStepStatus: "SUCCEEDED", curriculumStepStatus: "FAILED", failureCode: "CURRICULUM_REFRESH_FAILED" });
    const { client } = show((cache) => {
      cache.setQueryData(["curricula", "synthetic-user", "availability"], { items: [], nextCursor: null });
      cache.setQueryData(["curriculum-courses", "synthetic-user", "catalog", ""], { pages: [], pageParams: [] });
    });
    expect(await screen.findByText("Đồng bộ hoàn tất một phần")).toBeInTheDocument();
    expect(screen.getByText(/Một bước đã thành công/)).toBeInTheDocument();
    expect(screen.getByText("Chưa làm mới được chương trình và danh mục.")).toBeInTheDocument();
    expect(api.getRun).toHaveBeenCalledWith(id, expect.any(AbortSignal));
    await waitFor(() => expect(client.getQueryState(["curricula", "synthetic-user", "availability"])?.isInvalidated).toBe(true));
    expect(client.getQueryState(["curriculum-courses", "synthetic-user", "catalog", ""])?.isInvalidated).toBe(true);
  });

  it("loads only the next history page when asked", async () => {
    vi.mocked(api.getHistory).mockResolvedValueOnce({ items: [queued], nextCursor: "cursor" })
      .mockResolvedValueOnce({ items: [{ ...queued, runId: "10000000-0000-4000-8000-000000000002" }], nextCursor: null });
    show();
    await userEvent.click(await screen.findByRole("button", { name: "Tải thêm" }));
    await waitFor(() => expect(api.getHistory).toHaveBeenCalledWith("cursor", expect.any(AbortSignal)));
    expect(screen.queryByRole("button", { name: "Tải thêm" })).not.toBeInTheDocument();
  });
});
