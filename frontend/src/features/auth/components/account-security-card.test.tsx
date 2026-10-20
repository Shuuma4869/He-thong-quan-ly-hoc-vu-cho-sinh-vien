import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AccountSecurityCard } from "./account-security-card";
import * as api from "../api";

vi.mock("../api", () => ({
  getAccountSessions: vi.fn(), changeAccountPassword: vi.fn(),
  revokeAccountSession: vi.fn(), revokeOtherAccountSessions: vi.fn(),
}));

const sessions = [
  { sessionRef: "a".repeat(64), current: true, createdAt: "2026-10-20T00:00:00Z",
    lastAccessedAt: "2026-10-20T00:05:00Z", expiresAt: "2026-10-20T00:35:00Z" },
  { sessionRef: "b".repeat(64), current: false, createdAt: "2026-10-19T00:00:00Z",
    lastAccessedAt: "2026-10-20T00:02:00Z", expiresAt: "2026-10-20T00:32:00Z" },
];

function show() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}><AccountSecurityCard userId="synthetic-user" /></QueryClientProvider>);
}

beforeEach(() => {
  vi.mocked(api.getAccountSessions).mockResolvedValue(sessions);
  HTMLDialogElement.prototype.showModal = function () { this.open = true; };
  HTMLDialogElement.prototype.close = function () { this.open = false; };
});
afterEach(() => vi.clearAllMocks());

describe("Account security settings", () => {
  it("marks the current session without displaying its opaque reference", async () => {
    show();
    expect(await screen.findByText("Phiên hiện tại")).toBeInTheDocument();
    expect(screen.getByText("Phiên đăng nhập khác")).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "Đăng xuất phiên" })).toHaveLength(1);
    expect(document.body.textContent).not.toContain(sessions[0].sessionRef);
    expect(document.body.textContent).not.toContain(sessions[1].sessionRef);
  });

  it("validates confirmation and clears password fields after a confirmed change", async () => {
    vi.mocked(api.changeAccountPassword).mockResolvedValue();
    show();
    const user = userEvent.setup();
    await user.type(screen.getByLabelText("Mật khẩu hiện tại"), "Phase18-current-password");
    await user.type(screen.getByLabelText("Mật khẩu mới"), "Phase18-next-password");
    await user.type(screen.getByLabelText("Xác nhận mật khẩu mới"), "not-matching-password");
    await user.click(screen.getByRole("button", { name: "Đổi mật khẩu" }));
    expect(await screen.findByText("Hai mật khẩu mới không khớp.")).toBeInTheDocument();
    expect(api.changeAccountPassword).not.toHaveBeenCalled();

    await user.clear(screen.getByLabelText("Xác nhận mật khẩu mới"));
    await user.type(screen.getByLabelText("Xác nhận mật khẩu mới"), "Phase18-next-password");
    await user.click(screen.getByRole("button", { name: "Đổi mật khẩu" }));
    await waitFor(() => expect(api.changeAccountPassword).toHaveBeenCalledWith(
      "Phase18-current-password", "Phase18-next-password"));
    expect(await screen.findByText("Đã đổi mật khẩu. Các phiên khác đã được đăng xuất.")).toBeInTheDocument();
    expect(screen.getByLabelText("Mật khẩu hiện tại")).toHaveValue("");
    expect(screen.getByLabelText("Mật khẩu mới")).toHaveValue("");
    expect(screen.getByLabelText("Xác nhận mật khẩu mới")).toHaveValue("");
  });

  it("confirms revoking all other sessions and refreshes the list", async () => {
    vi.mocked(api.revokeOtherAccountSessions).mockResolvedValue();
    show();
    const user = userEvent.setup();
    await screen.findByText("Phiên đăng nhập khác");
    await user.click(screen.getByRole("button", { name: "Đăng xuất tất cả phiên khác" }));
    const dialog = screen.getByRole("dialog");
    expect(within(dialog).getByText(/Phiên hiện tại được giữ lại/)).toBeInTheDocument();
    expect(api.revokeOtherAccountSessions).not.toHaveBeenCalled();
    await user.click(within(dialog).getByRole("button", { name: "Đăng xuất các phiên khác" }));
    await waitFor(() => expect(api.revokeOtherAccountSessions).toHaveBeenCalledOnce());
    expect(await screen.findByText("Đã đăng xuất các phiên khác.")).toBeInTheDocument();
    await waitFor(() => expect(api.getAccountSessions).toHaveBeenCalledTimes(2));
  });

  it("does not claim success when session loading fails", async () => {
    vi.mocked(api.getAccountSessions).mockRejectedValue(new Error("Network unavailable"));
    show();
    expect(await screen.findByRole("alert")).toHaveTextContent("Không thể đọc danh sách phiên.");
    expect(screen.getByRole("button", { name: "Đăng xuất tất cả phiên khác" })).toBeDisabled();
  });
});
