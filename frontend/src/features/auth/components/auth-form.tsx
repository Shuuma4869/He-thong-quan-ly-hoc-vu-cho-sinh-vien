"use client";

import Link from "next/link";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
import { GraduationCap } from "lucide-react";
import { Button } from "@/components/ui/button";
import { login, register } from "../api";
import { loginSchema, registerSchema } from "../schema";

type FormValues = { email: string; password: string; displayName?: string };

export function AuthForm({ mode }: { mode: "login" | "register" }) {
  const isRegister = mode === "register";
  const router = useRouter();
  const queryClient = useQueryClient();
  const [error, setError] = useState("");
  const [registered, setRegistered] = useState(false);
  const { register: field, handleSubmit, resetField, formState: { errors, isSubmitting } } = useForm<FormValues>({
    resolver: zodResolver(isRegister ? registerSchema : loginSchema),
    defaultValues: { email: "", password: "", ...(isRegister ? { displayName: "" } : {}) },
  });

  async function submit(values: FormValues) {
    setError("");
    try {
      if (isRegister) {
        await register({ ...values, displayName: values.displayName ?? "" });
        setRegistered(true);
      } else {
        await login({ email: values.email, password: values.password });
        queryClient.clear();
        router.replace("/");
        router.refresh();
      }
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không thể kết nối máy chủ.");
    } finally {
      resetField("password");
    }
  }

  return <main className="grid min-h-screen place-items-center px-4 py-12">
    <section className="w-full max-w-md rounded-2xl border bg-card p-6 shadow-sm sm:p-8">
      <div className="mb-6 flex items-center gap-3 text-primary"><GraduationCap aria-hidden className="size-8" /><span className="text-lg font-semibold">AMS</span></div>
      <h1 className="text-2xl font-semibold">{isRegister ? "Tạo tài khoản AMS" : "Đăng nhập AMS"}</h1>
      <p className="mt-2 text-sm text-muted">{isRegister ? "Tài khoản AMS độc lập với tài khoản Phenikaa." : "Tiếp tục vào không gian học tập của bạn."}</p>
      {registered ? <div className="mt-6 space-y-4"><p role="status">Đã tạo tài khoản. Bạn có thể đăng nhập ngay.</p><p className="text-sm text-muted">Email chưa được xác minh. AMS chưa gửi email thông báo.</p><Link className="font-medium text-primary underline" href="/login">Đến trang đăng nhập</Link></div> : <form className="mt-6 space-y-4" onSubmit={handleSubmit(submit)} noValidate>
        {isRegister && <div><label className="text-sm font-medium" htmlFor="displayName">Tên hiển thị</label><input id="displayName" autoComplete="name" maxLength={80} className="mt-1.5 h-11 w-full rounded-lg border bg-background px-3" aria-invalid={!!errors.displayName} aria-describedby="displayName-error" {...field("displayName")} /><p id="displayName-error" className="mt-1 text-sm text-red-600">{errors.displayName?.message}</p></div>}
        <div><label className="text-sm font-medium" htmlFor="email">Email</label><input id="email" type="email" autoComplete="username" maxLength={254} className="mt-1.5 h-11 w-full rounded-lg border bg-background px-3" aria-invalid={!!errors.email} aria-describedby="email-error" {...field("email")} /><p id="email-error" className="mt-1 text-sm text-red-600">{errors.email?.message}</p></div>
        <div><label className="text-sm font-medium" htmlFor="password">Mật khẩu</label><input id="password" type="password" autoComplete={isRegister ? "new-password" : "current-password"} maxLength={64} className="mt-1.5 h-11 w-full rounded-lg border bg-background px-3" aria-invalid={!!errors.password} aria-describedby="password-error password-help" {...field("password")} /><p id="password-help" className="mt-1 text-xs text-muted">{isRegister ? "12–64 ký tự, tối đa 72 byte UTF-8. Không dùng mật khẩu tài khoản trường." : ""}</p><p id="password-error" className="mt-1 text-sm text-red-600">{errors.password?.message}</p></div>
        {error && <p role="alert" className="text-sm text-red-600 dark:text-red-400">{error}</p>}
        <Button type="submit" disabled={isSubmitting} className="w-full">{isSubmitting ? "Đang xử lý…" : isRegister ? "Đăng ký" : "Đăng nhập"}</Button>
        <p className="text-sm text-muted">{isRegister ? "Đã có tài khoản? " : "Chưa có tài khoản? "}<Link href={isRegister ? "/login" : "/register"} className="font-medium text-primary underline">{isRegister ? "Đăng nhập" : "Đăng ký"}</Link></p>
      </form>}
    </section>
  </main>;
}
