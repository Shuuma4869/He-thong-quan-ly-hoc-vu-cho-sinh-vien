"use client";

import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { saveSettings } from "@/features/auth/api";
import { currentUserKey } from "@/features/auth/components/auth-boundary";
import type { CurrentUser, Settings } from "@/features/auth/schema";
import { confirmEmailVerification, getEmailStatus, requestEmailVerification } from "./api";

export const emailStatusKey = ["email-notification-status"] as const;

export function EmailNotificationCard({ userId, settings, onSettingsSaved }:
  { userId: string; settings: Settings; onSettingsSaved: (settings: Settings) => void }) {
  const client = useQueryClient();
  const status = useQuery({ queryKey: [...emailStatusKey, userId], queryFn: ({ signal }) => getEmailStatus(signal), retry: false });
  const [code, setCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const current = status.data;

  async function act(action: () => Promise<void>, success: string) {
    setBusy(true); setError(""); setMessage("");
    try {
      await action();
      await status.refetch();
      setMessage(success);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không thể cập nhật thông báo email.");
    } finally { setBusy(false); }
  }

  async function toggle() {
    await act(async () => {
      const updated = await saveSettings({ notificationEmail: settings.notificationEmail,
        timezone: settings.timezone, locale: settings.locale, theme: settings.theme,
        syncEmailAlertsEnabled: !settings.syncEmailAlertsEnabled });
      onSettingsSaved(updated);
      client.setQueryData<CurrentUser>(currentUserKey, (user) => user ? { ...user, settings: updated } : user);
    }, settings.syncEmailAlertsEnabled ? "Đã tắt cảnh báo đồng bộ." : "Đã bật cảnh báo đồng bộ.");
  }

  return <section className="min-w-0 space-y-4 rounded-2xl border bg-card p-4 sm:p-6" aria-labelledby="email-notification-heading">
    <div><h2 id="email-notification-heading" className="text-lg font-semibold">Thông báo email</h2>
      <p className="mt-1 text-sm text-muted">Chỉ gửi khi lượt đồng bộ thất bại hoặc hoàn tất một phần. Chưa có thông báo điểm hay lịch.</p></div>
    {status.isPending ? <p role="status" className="text-sm text-muted">Đang kiểm tra trạng thái email…</p>
      : status.isError ? <div><p role="alert" className="text-sm text-red-600">Không thể kiểm tra thông báo email.</p>
        <Button type="button" variant="outline" onClick={() => void status.refetch()}>Thử lại</Button></div>
      : <>
        <p className="break-all text-sm">Email nhận thông báo: {current?.notificationEmail ?? "Chưa đặt email nhận thông báo."}</p>
        <p role="status" className="text-sm">{!current?.featureEnabled ? "Tính năng email chưa khả dụng."
          : current.verified ? "Đã xác minh." : "Chưa xác minh."}</p>
        {current?.featureEnabled && current.notificationEmail && !current.verified && <div className="space-y-3">
          <Button type="button" variant="outline" disabled={busy} onClick={() => void act(requestEmailVerification, "Đã gửi mã xác minh.")}>Gửi mã xác minh</Button>
          <div className="flex flex-col gap-2 sm:flex-row sm:items-end">
            <div className="min-w-0 flex-1"><label htmlFor="verification-code" className="text-sm font-medium">Mã xác minh</label>
              <input id="verification-code" autoComplete="one-time-code" maxLength={10} value={code}
                onChange={(event) => setCode(event.target.value.toUpperCase())}
                className="mt-1.5 h-11 w-full rounded-lg border bg-background px-3 uppercase" /></div>
            <Button type="button" disabled={busy || code.trim().length !== 10}
              onClick={() => void act(async () => {
                await confirmEmailVerification(code); setCode("");
                await client.invalidateQueries({ queryKey: currentUserKey });
              }, "Email đã được xác minh.")}>Xác minh</Button>
          </div></div>}
        <div className="space-y-1"><label className="flex min-w-0 items-start gap-2 text-sm">
          <input type="checkbox" className="mt-1" checked={!!current?.syncAlertsEnabled}
            disabled={busy || !current?.featureEnabled || !current.verified}
            onChange={() => void toggle()} aria-label="Nhận email khi đồng bộ cần chú ý" />
          <span>Nhận email khi đồng bộ cần chú ý</span></label>
          <p className="text-xs text-muted">{!current?.featureEnabled ? "Cần người vận hành bật tính năng email."
            : !current.notificationEmail ? "Hãy lưu email nhận thông báo trước."
              : !current.verified ? "Hãy xác minh email trước khi bật cảnh báo." : "Gửi khi đồng bộ thất bại hoặc hoàn tất một phần."}</p></div>
        {message && <p role="status" className="text-sm text-primary">{message}</p>}
        {error && <p role="alert" className="text-sm text-red-600">{error}</p>}
      </>}
  </section>;
}
