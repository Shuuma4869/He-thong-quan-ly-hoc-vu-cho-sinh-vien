"use client";

import { BookOpenCheck, CalendarDays, GraduationCap, LayoutDashboard, Menu, Moon, RefreshCw, Settings, Sun } from "lucide-react";
import { useTheme } from "next-themes";

const navigation = [
  { label: "Tổng quan", icon: LayoutDashboard, active: true },
  { label: "Học vụ", icon: GraduationCap },
  { label: "Lịch", icon: CalendarDays },
  { label: "Chương trình", icon: BookOpenCheck },
  { label: "Đồng bộ", icon: RefreshCw },
];

export function AppShell({ children }: Readonly<{ children: React.ReactNode }>) {
  const { resolvedTheme, setTheme } = useTheme();

  return (
    <div className="min-h-screen lg:grid lg:grid-cols-[260px_1fr]">
      <aside className="hidden border-r bg-card lg:flex lg:flex-col">
        <div className="flex h-20 items-center gap-3 px-6">
          <div className="grid size-10 place-items-center rounded-2xl bg-primary text-white shadow-lg shadow-primary/20"><GraduationCap className="size-5" /></div>
          <div><p className="font-semibold tracking-tight">AMS</p><p className="text-xs text-muted">Quản lý học vụ</p></div>
        </div>
        <nav className="flex-1 space-y-1 px-3 py-5" aria-label="Điều hướng chính">
          {navigation.map(({ label, icon: Icon, active }) => (
            <a key={label} href="#" className={`flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition ${active ? "bg-primary-soft text-primary" : "text-muted hover:bg-primary-soft/60 hover:text-foreground"}`}>
              <Icon className="size-[18px]" />{label}
            </a>
          ))}
        </nav>
        <div className="border-t p-3"><a href="#" className="flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm text-muted hover:bg-primary-soft/60"><Settings className="size-[18px]" /> Cài đặt</a></div>
      </aside>

      <div className="min-w-0">
        <header className="sticky top-0 z-20 flex h-16 items-center justify-between border-b bg-background/90 px-4 backdrop-blur lg:px-8">
          <button className="grid size-10 place-items-center rounded-xl border bg-card lg:hidden" aria-label="Mở menu"><Menu className="size-5" /></button>
          <p className="hidden text-sm text-muted lg:block">Năm học 2026–2027</p>
          <button className="grid size-10 place-items-center rounded-xl border bg-card text-muted transition hover:text-foreground" aria-label="Đổi giao diện sáng tối" onClick={() => setTheme(resolvedTheme === "dark" ? "light" : "dark")}>
            <Moon className="size-4 dark:hidden" />
            <Sun className="hidden size-4 dark:block" />
          </button>
        </header>
        <main className="mx-auto max-w-[1500px] px-4 py-7 pb-24 lg:px-8 lg:py-10">{children}</main>
        <nav className="fixed inset-x-3 bottom-3 z-30 flex justify-around rounded-2xl border bg-card/95 p-2 shadow-xl backdrop-blur lg:hidden" aria-label="Điều hướng di động">
          {navigation.slice(0, 4).map(({ label, icon: Icon, active }) => (
            <a key={label} href="#" className={`flex min-w-16 flex-col items-center gap-1 rounded-xl py-1.5 text-[11px] ${active ? "text-primary" : "text-muted"}`}><Icon className="size-5" />{label}</a>
          ))}
        </nav>
      </div>
    </div>
  );
}
