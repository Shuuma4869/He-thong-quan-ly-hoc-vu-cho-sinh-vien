"use client";

import { Button } from "@/components/ui/button";

export default function ErrorPage({ reset }: { reset: () => void }) {
  return <main className="mx-auto max-w-lg space-y-4 p-8"><h1 className="text-xl font-semibold">Chưa thể tải trang</h1><p role="alert">Không thể kết nối hoặc kiểm tra phiên đăng nhập. Vui lòng thử lại sau.</p><Button onClick={reset}>Thử lại</Button></main>;
}
