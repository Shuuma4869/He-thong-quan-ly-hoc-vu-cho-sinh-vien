"use client";

import { useSyncExternalStore } from "react";
import Link from "next/link";
import { BookOpenCheck, CalendarDays, GraduationCap, LayoutDashboard, RefreshCw } from "lucide-react";
import { useTheme } from "next-themes";

const navigation = [
  { label: "Tổng quan", icon: LayoutDashboard, active: true },
  { label: "Học vụ", icon: GraduationCap, active: false },
  { label: "Lịch", icon: CalendarDays, active: false },
  { label: "Chương trình", icon: BookOpenCheck, active: false },
  { label: "Đồng bộ", icon: RefreshCw, active: false },
];

const subscribe = () => () => {};

export function AppShell({ children }: Readonly<{ children: React.ReactNode }>) {
  const { theme, setTheme } = useTheme();
  const mounted = useSyncExternalStore(subscribe, () => true, () => false);

  return (
    <div className="min-h-screen lg:grid lg:grid-cols-[260px_1fr]">
      <a href="#main-content" className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-50 focus:rounded-lg focus:bg-card focus:p-3">Đến nội dung chính</a>
      <aside className="hidden border-r bg-card lg:flex lg:flex-col">
        <div className="flex h-20 items-center gap-3 px-6">
          <div className="grid size-10 place-items-center rounded-2xl bg-primary text-white shadow-lg shadow-primary/20"><GraduationCap className="size-5" /></div>
          <div><p className="font-semibold tracking-tight">AMS</p><p className="text-xs text-muted">Quản lý học vụ</p></div>
        </div>
        <nav className="flex-1 space-y-1 px-3 py-5" aria-label="Điều hướng chính">
          {navigation.map(({ label, icon: Icon, active }) => active ? (
            <Link key={label} href="/" aria-current="page" className="flex items-center gap-3 rounded-xl bg-primary-soft px-3 py-2.5 text-sm font-medium text-primary"><Icon className="size-[18px]" />{label}</Link>
          ) : (
            <span key={label} aria-disabled="true" className="flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm text-muted"><Icon className="size-[18px]" />{label}<span className="ml-auto text-[10px]">Sắp có</span></span>
          ))}
        </nav>
        <p className="border-t p-5 text-xs text-muted">AMS · Bản khởi đầu</p>
      </aside>
      <div className="min-w-0">
        <header className="sticky top-0 z-20 flex h-16 items-center justify-between border-b bg-background/90 px-4 backdrop-blur lg:px-8">
          <p className="text-sm font-medium lg:text-muted">Không gian học tập của bạn</p>
          <label className="flex items-center gap-2 text-xs text-muted">
            <span className="hidden sm:inline">Giao diện</span>
            <select aria-label="Chọn giao diện" value={mounted ? theme : "system"} disabled={!mounted} onChange={(event) => setTheme(event.target.value)} className="h-9 rounded-lg border bg-card px-2 text-foreground">
              <option value="system">Hệ thống</option><option value="light">Sáng</option><option value="dark">Tối</option>
            </select>
          </label>
        </header>
        <main id="main-content" className="mx-auto max-w-[1500px] px-4 py-7 pb-24 lg:px-8 lg:py-10">{children}</main>
        <nav className="fixed inset-x-3 bottom-3 z-30 flex justify-around rounded-2xl border bg-card/95 p-2 shadow-xl backdrop-blur lg:hidden" aria-label="Điều hướng di động">
          {navigation.slice(0, 4).map(({ label, icon: Icon, active }) => active ? (
            <Link key={label} href="/" aria-current="page" className="flex min-w-16 flex-col items-center gap-1 rounded-xl py-1.5 text-[11px] text-primary"><Icon className="size-5" />{label}</Link>
          ) : (
            <span key={label} aria-disabled="true" className="flex min-w-16 flex-col items-center gap-1 rounded-xl py-1.5 text-[11px] text-muted" title="Chưa khả dụng"><Icon className="size-5" />{label}<span className="sr-only"> · Chưa khả dụng</span></span>
          ))}
        </nav>
      </div>
    </div>
  );
}
