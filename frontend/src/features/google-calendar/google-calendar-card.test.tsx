import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { GoogleCalendarCard } from "./google-calendar-card";
import * as api from "./api";

vi.mock("./api", () => ({
  getGoogleConnection: vi.fn(), startGoogleAuthorization: vi.fn(),
  openGoogleAuthorization: vi.fn(), retryGoogleSetup: vi.fn(), disconnectGoogle: vi.fn(),
}));

const disconnected = { available: true, status: "DISCONNECTED" as const, calendarReady: false,
  connectedAt: null, lastSuccessfulAccessAt: null };
const connected = { ...disconnected, status: "CONNECTED" as const, calendarReady: true };
const setup = { ...disconnected, status: "SETUP_REQUIRED" as const };

function show(callbackResult?: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}><GoogleCalendarCard callbackResult={callbackResult} /></QueryClientProvider>);
}

beforeEach(() => vi.mocked(api.getGoogleConnection).mockResolvedValue(disconnected));
afterEach(() => vi.clearAllMocks());

describe("Google Calendar settings", () => {
  it("shows unavailable without pretending the user is connected", async () => {
    vi.mocked(api.getGoogleConnection).mockResolvedValue({ ...disconnected, available: false });
    show("connected");
    expect(await screen.findByText("Chưa được cấu hình")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Kết nối" })).not.toBeInTheDocument();
    expect(screen.queryByText("Đã kết nối lịch riêng của AMS.")).not.toBeInTheDocument();
  });

  it("starts connect from the backend URL", async () => {
    vi.mocked(api.startGoogleAuthorization).mockResolvedValue("https://accounts.google.com/o/oauth2/v2/auth?state=synthetic");
    show();
    const button = await screen.findByRole("button", { name: "Kết nối" });
    await userEvent.click(button);
    expect(api.openGoogleAuthorization).toHaveBeenCalledWith("https://accounts.google.com/o/oauth2/v2/auth?state=synthetic");
    expect(button).toBeDisabled();
  });

  it("reports a failed authorization start", async () => {
    vi.mocked(api.startGoogleAuthorization).mockRejectedValue(new Error("Không thể bắt đầu kết nối."));
    show();
    const button = await screen.findByRole("button", { name: "Kết nối" });
    await userEvent.click(button);
    expect(await screen.findByRole("alert")).toHaveTextContent("Không thể bắt đầu kết nối.");
    expect(button).toBeEnabled();
  });

  it("shows reconnect and safe callback feedback", async () => {
    vi.mocked(api.getGoogleConnection).mockResolvedValue({ ...disconnected, status: "RECONNECTION_REQUIRED" });
    show("cancelled");
    expect(await screen.findByRole("button", { name: "Kết nối lại" })).toBeInTheDocument();
    expect(screen.getByText("Bạn đã hủy cấp quyền Google Calendar.")).toBeInTheDocument();
  });

  it("finishes setup and disconnects without displaying tokens", async () => {
    vi.mocked(api.getGoogleConnection).mockResolvedValueOnce(setup).mockResolvedValueOnce(connected)
      .mockResolvedValueOnce(disconnected);
    vi.mocked(api.retryGoogleSetup).mockResolvedValue(connected);
    vi.mocked(api.disconnectGoogle).mockResolvedValue();
    show("setup");
    await userEvent.click(await screen.findByRole("button", { name: "Hoàn tất thiết lập" }));
    await waitFor(() => expect(screen.getByText("Đã kết nối")).toBeInTheDocument());
    await userEvent.click(screen.getByRole("button", { name: "Ngắt kết nối" }));
    await waitFor(() => expect(screen.getByText("Chưa kết nối")).toBeInTheDocument());
    expect(api.retryGoogleSetup).toHaveBeenCalledOnce();
    expect(api.disconnectGoogle).toHaveBeenCalledOnce();
    expect(document.body.textContent).not.toContain("synthetic-refresh");
  });
});
