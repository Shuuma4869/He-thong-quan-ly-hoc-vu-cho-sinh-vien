"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { saveSettings } from "../api";
import { settingsSchema, type CurrentUser, type Settings } from "../schema";
import { currentUserKey } from "./auth-boundary";

const inputStyle = "mt-1.5 h-11 w-full rounded-lg border bg-background px-3";

export function SettingsForm({ user }: { user: CurrentUser }) {
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const queryClient = useQueryClient();
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<Settings>({
    resolver: zodResolver(settingsSchema), defaultValues: { ...user.settings, notificationEmail: user.settings.notificationEmail ?? "" },
  });
  async function submit(values: Settings) {
    setMessage(""); setError("");
    try {
      const settings = await saveSettings(values);
      queryClient.setQueryData<CurrentUser>(currentUserKey, (current) => ({ ...(current ?? user), settings }));
      setMessage("Đã lưu cài đặt.");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không thể lưu cài đặt.");
    }
  }
  return <section className="max-w-2xl space-y-6">
    <div><h1 className="text-2xl font-semibold">Cài đặt tài khoản</h1><p className="mt-2 break-words text-sm text-muted">{user.displayName ?? user.email} · {user.email}</p></div>
    <form onSubmit={handleSubmit(submit)} noValidate className="space-y-5 rounded-2xl border bg-card p-6">
      <div><label htmlFor="notificationEmail" className="text-sm font-medium">Email nhận thông báo</label><input id="notificationEmail" type="email" autoComplete="email" className={inputStyle} aria-invalid={!!errors.notificationEmail} aria-describedby="notificationEmail-help notificationEmail-error" {...register("notificationEmail")} /><p id="notificationEmail-help" className="mt-1 text-xs text-muted">Tùy chọn. Email này chưa được xác minh; AMS chưa gửi thông báo.</p><p id="notificationEmail-error" className="text-sm text-red-600">{errors.notificationEmail?.message}</p></div>
      <div><label htmlFor="timezone" className="text-sm font-medium">Múi giờ</label><input id="timezone" list="timezones" className={inputStyle} aria-describedby="timezone-help" {...register("timezone")} /><datalist id="timezones"><option value="Asia/Ho_Chi_Minh" /><option value="Asia/Tokyo" /><option value="Europe/London" /><option value="UTC" /></datalist><p id="timezone-help" className="mt-1 text-xs text-muted">Nhập tên múi giờ IANA, ví dụ Asia/Ho_Chi_Minh.</p>{errors.timezone && <p role="alert" className="text-sm text-red-600">{errors.timezone.message}</p>}</div>
      <div><label htmlFor="locale" className="text-sm font-medium">Ngôn ngữ ưu tiên</label><select id="locale" className={inputStyle} {...register("locale")}><option value="vi-VN">Tiếng Việt</option><option value="en-US">English</option></select><p className="mt-1 text-xs text-muted">Lưu lựa chọn cho tài khoản. Giao diện hiện hỗ trợ tiếng Việt.</p></div>
      <div><label htmlFor="theme" className="text-sm font-medium">Giao diện mặc định</label><select id="theme" className={inputStyle} {...register("theme")}><option value="SYSTEM">Theo hệ thống</option><option value="LIGHT">Sáng</option><option value="DARK">Tối</option></select></div>
      {message && <p role="status" className="text-sm text-primary">{message}</p>}{error && <p role="alert" className="text-sm text-red-600">{error}</p>}
      <Button type="submit" disabled={isSubmitting}>{isSubmitting ? "Đang lưu…" : "Lưu cài đặt"}</Button>
    </form>
  </section>;
}
