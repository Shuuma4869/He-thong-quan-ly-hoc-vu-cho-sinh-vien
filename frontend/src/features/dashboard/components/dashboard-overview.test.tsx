import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { DashboardOverview } from "./dashboard-overview";
import * as sync from "@/features/sync/api";
import * as curriculum from "@/features/curriculum/api";
import * as google from "@/features/google-calendar/api";

vi.mock("next/link", () => ({ default: ({ children, href }: { children: React.ReactNode; href: string }) => <a href={href}>{children}</a> }));
vi.mock("./api-status", () => ({ ApiStatus: () => null }));
vi.mock("@/features/sync/api", async (importOriginal) => ({ ...(await importOriginal<typeof import("@/features/sync/api")>()),
  getPhenikaaConnection: vi.fn(), getCurrentRun: vi.fn(),
}));
vi.mock("@/features/curriculum/api", () => ({ getCurricula: vi.fn() }));
vi.mock("@/features/google-calendar/api", () => ({ getGoogleConnection: vi.fn() }));

const connection: sync.PhenikaaConnection = { status: "CONNECTED", lastAuthenticatedAt: "2026-01-01T00:00:00Z",
  lastSuccessfulAccessAt: null, lastFailedAccessAt: null, lastFailureCode: null, reconnectionRequired: false };

function show() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
  render(<QueryClientProvider client={client}><DashboardOverview userId="synthetic-user" /></QueryClientProvider>);
}

afterEach(() => { cleanup(); vi.resetAllMocks(); });

describe("Dashboard sync status", () => {
  it("shows unavailable instead of no run when both endpoints return 404", async () => {
    vi.mocked(sync.getPhenikaaConnection).mockResolvedValue(null);
    vi.mocked(sync.getCurrentRun).mockResolvedValue(null);
    vi.mocked(google.getGoogleConnection).mockResolvedValue({ available: false, status: "DISCONNECTED",
      calendarReady: false, connectedAt: null, lastSuccessfulAccessAt: null });
    vi.mocked(curriculum.getCurricula).mockResolvedValue({ items: [], nextCursor: null });
    show();
    expect(await screen.findByText("Đồng bộ không khả dụng khi tích hợp Phenikaa chưa được bật.")).toBeInTheDocument();
    expect(screen.queryByText("Chưa có lượt đồng bộ được ghi nhận.")).not.toBeInTheDocument();
  });

  it("shows no run only when Phenikaa is available", async () => {
    vi.mocked(sync.getPhenikaaConnection).mockResolvedValue(connection);
    vi.mocked(sync.getCurrentRun).mockResolvedValue(null);
    vi.mocked(google.getGoogleConnection).mockResolvedValue({ available: false, status: "DISCONNECTED",
      calendarReady: false, connectedAt: null, lastSuccessfulAccessAt: null });
    vi.mocked(curriculum.getCurricula).mockResolvedValue({ items: [], nextCursor: null });
    show();
    expect(await screen.findByText("Đã kết nối")).toBeInTheDocument();
    expect(await screen.findByText("Chưa có lượt đồng bộ được ghi nhận.")).toBeInTheDocument();
    expect(screen.queryByText("Đồng bộ không khả dụng khi tích hợp Phenikaa chưa được bật.")).not.toBeInTheDocument();
  });
});
