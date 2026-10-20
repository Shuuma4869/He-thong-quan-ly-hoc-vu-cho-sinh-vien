"use client";

import { useRef, useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { Button } from "@/components/ui/button";
import { changeAccountPassword, getAccountSessions, revokeAccountSession, revokeOtherAccountSessions } from "../api";
import { passwordChangeSchema, type PasswordChange } from "../schema";

const inputStyle = "mt-1.5 h-11 w-full rounded-lg border bg-background px-3";
const sessionKey = (userId: string) => ["account-security-sessions", userId] as const;

function formatTime(value: string) {
  return new Intl.DateTimeFormat("vi-VN", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

export function AccountSecurityCard({ userId }: { userId: string }) {
  const queryClient = useQueryClient();
  const dialog = useRef<HTMLDialogElement>(null);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [showPasswords, setShowPasswords] = useState(false);
  const sessions = useQuery({ queryKey: sessionKey(userId), queryFn: ({ signal }) => getAccountSessions(signal),
    retry: false, refetchOnWindowFocus: false });
  const form = useForm<PasswordChange>({ resolver: zodResolver(passwordChangeSchema),
    defaultValues: { currentPassword: "", newPassword: "", confirmPassword: "" } });

  async function submitPassword(values: PasswordChange) {
    setMessage(""); setError("");
    try {
      await changeAccountPassword(values.currentPassword, values.newPassword);
      form.reset();
      setMessage("Đã đổi mật khẩu. Các phiên khác đã được đăng xuất.");
      await queryClient.invalidateQueries({ queryKey: sessionKey(userId) });
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không thể đổi mật khẩu.");
      form.reset();
      await queryClient.invalidateQueries({ queryKey: sessionKey(userId) });
    }
  }

  const revokeOne = useMutation({ mutationFn: revokeAccountSession,
    onMutate: () => { setMessage(""); setError(""); },
    onSuccess: () => setMessage("Đã đăng xuất phiên đã chọn."),
    onError: (cause) => setError(cause instanceof Error ? cause.message : "Không thể đăng xuất phiên."),
    onSettled: () => queryClient.invalidateQueries({ queryKey: sessionKey(userId) }),
  });
  const revokeOthers = useMutation({ mutationFn: revokeOtherAccountSessions,
    onMutate: () => { setMessage(""); setError(""); },
    onSuccess: () => { dialog.current?.close(); setMessage("Đã đăng xuất các phiên khác."); },
    onError: (cause) => { dialog.current?.close(); setError(cause instanceof Error ? cause.message : "Không thể xác nhận các phiên đã được đăng xuất."); },
    onSettled: () => queryClient.invalidateQueries({ queryKey: sessionKey(userId) }),
  });
  const busy = revokeOne.isPending || revokeOthers.isPending;
  const otherCount = sessions.data?.filter((session) => !session.current).length ?? 0;

  return <section aria-labelledby="account-security-title" className="min-w-0 space-y-7 rounded-2xl border bg-card p-6">
    <div><h2 id="account-security-title" className="text-lg font-semibold">Bảo mật tài khoản</h2>
      <p className="mt-1 text-sm text-muted">Đổi mật khẩu và kiểm soát các phiên đăng nhập AMS.</p></div>

    <form onSubmit={form.handleSubmit(submitPassword)} noValidate className="space-y-4">
      <h3 className="font-medium">Đổi mật khẩu</h3>
      <div><label htmlFor="currentPassword" className="text-sm font-medium">Mật khẩu hiện tại</label>
        <input id="currentPassword" type={showPasswords ? "text" : "password"} autoComplete="current-password"
          className={inputStyle} aria-invalid={!!form.formState.errors.currentPassword}
          aria-describedby="currentPassword-error" {...form.register("currentPassword")} />
        <p id="currentPassword-error" role={form.formState.errors.currentPassword ? "alert" : undefined}
          className="text-sm text-red-600">{form.formState.errors.currentPassword?.message}</p></div>
      <div><label htmlFor="newPassword" className="text-sm font-medium">Mật khẩu mới</label>
        <input id="newPassword" type={showPasswords ? "text" : "password"} autoComplete="new-password"
          className={inputStyle} aria-invalid={!!form.formState.errors.newPassword}
          aria-describedby="newPassword-help newPassword-error" {...form.register("newPassword")} />
        <p id="newPassword-help" className="mt-1 text-xs text-muted">12–64 ký tự, tối đa 72 byte UTF-8.</p>
        <p id="newPassword-error" role={form.formState.errors.newPassword ? "alert" : undefined}
          className="text-sm text-red-600">{form.formState.errors.newPassword?.message}</p></div>
      <div><label htmlFor="confirmPassword" className="text-sm font-medium">Xác nhận mật khẩu mới</label>
        <input id="confirmPassword" type={showPasswords ? "text" : "password"} autoComplete="new-password"
          className={inputStyle} aria-invalid={!!form.formState.errors.confirmPassword}
          aria-describedby="confirmPassword-error" {...form.register("confirmPassword")} />
        <p id="confirmPassword-error" role={form.formState.errors.confirmPassword ? "alert" : undefined}
          className="text-sm text-red-600">{form.formState.errors.confirmPassword?.message}</p></div>
      <div className="flex flex-wrap items-center gap-3">
        <Button type="submit" disabled={form.formState.isSubmitting}>
          {form.formState.isSubmitting ? "Đang đổi mật khẩu…" : "Đổi mật khẩu"}</Button>
        <button type="button" className="text-sm underline" onClick={() => setShowPasswords((shown) => !shown)}
          aria-label={showPasswords ? "Ẩn mật khẩu" : "Hiện mật khẩu"}>
          {showPasswords ? "Ẩn mật khẩu" : "Hiện mật khẩu"}</button>
      </div>
    </form>

    <div className="space-y-4 border-t pt-6">
      <div><h3 className="font-medium">Phiên đăng nhập</h3>
        <p className="mt-1 text-sm text-muted">Thời gian hết hạn chỉ là ước tính theo lúc truy cập gần nhất.</p></div>
      {sessions.isPending && <p role="status" className="text-sm text-muted">Đang tải các phiên…</p>}
      {sessions.isError && <div role="alert" className="text-sm text-red-600">Không thể đọc danh sách phiên.
        <button type="button" className="ml-2 underline" onClick={() => sessions.refetch()}>Thử lại</button></div>}
      {sessions.data && <ul className="space-y-3">{sessions.data.map((session) =>
        <li key={session.sessionRef} className="flex min-w-0 flex-wrap items-start justify-between gap-3 rounded-xl border p-4">
          <div className="min-w-0 text-sm"><p className="font-medium">{session.current ? "Phiên hiện tại" : "Phiên đăng nhập khác"}</p>
            <p className="mt-1 text-muted">Tạo: {formatTime(session.createdAt)}</p>
            <p className="text-muted">Truy cập gần nhất: {formatTime(session.lastAccessedAt)}</p>
            <p className="text-muted">Hết hạn nếu không hoạt động (ước tính): {formatTime(session.expiresAt)}</p></div>
          {!session.current && <Button type="button" variant="outline" disabled={busy}
            onClick={() => revokeOne.mutate(session.sessionRef)}>Đăng xuất phiên</Button>}
        </li>)}</ul>}
      {sessions.data?.length === 0 && <p className="text-sm text-muted">Chưa tìm thấy phiên hợp lệ. Hãy tải lại trang.</p>}
      <Button type="button" variant="outline" disabled={busy || otherCount === 0}
        onClick={() => dialog.current?.showModal()}>Đăng xuất tất cả phiên khác</Button>
    </div>

    {message && <p role="status" aria-live="polite" className="text-sm text-primary">{message}</p>}
    {error && <p role="alert" className="text-sm text-red-600">{error}</p>}

    <dialog ref={dialog} aria-labelledby="revoke-others-title"
      className="m-auto w-[min(28rem,calc(100vw-2rem))] rounded-2xl border bg-card p-6 text-foreground shadow-xl backdrop:bg-black/50">
      <h3 id="revoke-others-title" className="text-lg font-semibold">Đăng xuất các phiên khác?</h3>
      <p className="mt-2 text-sm text-muted">Các phiên đăng nhập khác sẽ bị đăng xuất. Phiên hiện tại được giữ lại.
        Thao tác này không thể hoàn tác trực tiếp.</p>
      <div className="mt-5 flex flex-wrap justify-end gap-2">
        <Button type="button" variant="outline" disabled={revokeOthers.isPending}
          onClick={() => dialog.current?.close()}>Hủy</Button>
        <Button type="button" disabled={revokeOthers.isPending} onClick={() => revokeOthers.mutate()}>
          {revokeOthers.isPending ? "Đang đăng xuất…" : "Đăng xuất các phiên khác"}</Button>
      </div>
    </dialog>
  </section>;
}
