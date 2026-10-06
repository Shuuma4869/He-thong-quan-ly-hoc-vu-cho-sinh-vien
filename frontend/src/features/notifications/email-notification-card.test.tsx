import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { EmailNotificationCard } from "./email-notification-card";
import * as notifications from "./api";
import * as auth from "@/features/auth/api";
import type { Settings } from "@/features/auth/schema";

vi.mock("./api", () => ({ getEmailStatus: vi.fn(), requestEmailVerification: vi.fn(), confirmEmailVerification: vi.fn() }));
vi.mock("@/features/auth/api", () => ({ saveSettings: vi.fn() }));

const settings: Settings = { notificationEmail: "notify@example.test", timezone: "Asia/Ho_Chi_Minh",
  locale: "vi-VN", theme: "SYSTEM", notificationEmailVerifiedAt: null, syncEmailAlertsEnabled: false };
const pending = { featureEnabled: true, configured: true, notificationEmail: "notify@example.test",
  verified: false, verifiedAt: null, syncAlertsEnabled: false };

function show(value: Settings = settings) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const saved = vi.fn();
  render(<QueryClientProvider client={client}>
    <EmailNotificationCard userId="00000000-0000-0000-0000-000000000001" settings={value} onSettingsSaved={saved} />
  </QueryClientProvider>);
  return saved;
}

beforeEach(() => vi.mocked(notifications.getEmailStatus).mockResolvedValue(pending));
afterEach(() => vi.clearAllMocks());

describe("email notifications settings", () => {
  it("shows unavailable and does not allow sending or opting in", async () => {
    vi.mocked(notifications.getEmailStatus).mockResolvedValue({ ...pending, featureEnabled: false });
    show();
    expect(await screen.findByText("Tính năng email chưa khả dụng.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Gửi mã xác minh" })).not.toBeInTheDocument();
    expect(screen.getByRole("checkbox", { name: "Nhận email khi đồng bộ cần chú ý" })).toBeDisabled();
  });

  it("explains missing and unverified email", async () => {
    vi.mocked(notifications.getEmailStatus).mockResolvedValue({ ...pending, notificationEmail: null });
    show();
    expect(await screen.findByText(/Chưa đặt email nhận thông báo\./)).toBeInTheDocument();
    expect(screen.getByText("Hãy lưu email nhận thông báo trước.")).toBeInTheDocument();
  });

  it("sends and confirms a code, then allows opt-in", async () => {
    const verified = { ...pending, verified: true, verifiedAt: "2026-10-06T00:00:00Z" };
    vi.mocked(notifications.getEmailStatus).mockResolvedValueOnce(pending).mockResolvedValueOnce(pending)
      .mockResolvedValue(verified);
    vi.mocked(notifications.requestEmailVerification).mockResolvedValue();
    vi.mocked(notifications.confirmEmailVerification).mockResolvedValue(verified);
    vi.mocked(auth.saveSettings).mockResolvedValue({ ...settings, notificationEmailVerifiedAt: verified.verifiedAt,
      syncEmailAlertsEnabled: true });
    const saved = show();
    await userEvent.click(await screen.findByRole("button", { name: "Gửi mã xác minh" }));
    expect(await screen.findByText("Đã gửi mã xác minh.")).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText("Mã xác minh"), "23456789ab");
    await userEvent.click(screen.getByRole("button", { name: "Xác minh" }));
    await waitFor(() => expect(screen.getByText("Đã xác minh.")).toBeInTheDocument());
    expect(notifications.confirmEmailVerification).toHaveBeenCalledWith("23456789AB");
    await userEvent.click(screen.getByRole("checkbox", { name: "Nhận email khi đồng bộ cần chú ý" }));
    await waitFor(() => expect(saved).toHaveBeenCalledWith(expect.objectContaining({ syncEmailAlertsEnabled: true })));
  });

  it("shows safe error after an invalid code", async () => {
    vi.mocked(notifications.confirmEmailVerification).mockRejectedValue(new Error("Mã xác minh không hợp lệ hoặc đã hết hạn."));
    show();
    await userEvent.type(await screen.findByLabelText("Mã xác minh"), "23456789AB");
    await userEvent.click(screen.getByRole("button", { name: "Xác minh" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Mã xác minh không hợp lệ hoặc đã hết hạn.");
  });
});
