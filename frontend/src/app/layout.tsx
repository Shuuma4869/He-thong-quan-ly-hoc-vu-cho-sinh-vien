import type { Metadata } from "next";
import type { ReactNode } from "react";
import { AppProviders } from "@/components/providers/app-providers";
import "@fontsource-variable/geist";
import "./globals.css";

export const metadata: Metadata = {
  title: "AMS | Quản lý học vụ",
  description: "Hệ thống quản lý học vụ cho sinh viên",
};

export default function RootLayout({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <html lang="vi" suppressHydrationWarning>
      <body>
        <AppProviders>{children}</AppProviders>
      </body>
    </html>
  );
}
