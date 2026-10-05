"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { ApiStatus } from "./api-status";
import { getCurricula } from "@/features/curriculum/api";
import { getGoogleConnection } from "@/features/google-calendar/api";
import { getCurrentRun, getPhenikaaConnection, readError, syncKeys } from "@/features/sync/api";
import { localTime, runStatus } from "@/features/sync/presentation";

const card = "min-w-0 space-y-3 rounded-2xl border bg-card p-5 shadow-sm";

function Retry({ retry }: { retry: () => void }) {
  return <Button variant="outline" size="sm" onClick={retry}>Thử lại</Button>;
}

export function DashboardOverview({ userId }: { userId: string }) {
  const phenikaa = useQuery({ queryKey: syncKeys.phenikaa(userId), queryFn: ({ signal }) => getPhenikaaConnection(signal), retry: false });
  const google = useQuery({ queryKey: ["google-calendar-connection", userId], queryFn: ({ signal }) => getGoogleConnection(signal), retry: false });
  const current = useQuery({ queryKey: syncKeys.current(userId), queryFn: ({ signal }) => getCurrentRun(signal), retry: false });
  const curricula = useQuery({ queryKey: ["curricula", userId, "availability"], queryFn: ({ signal }) => getCurricula(undefined, signal), retry: false, staleTime: 0 });

  return <div className="space-y-8">
    <header className="space-y-2"><p className="text-sm font-medium text-primary">Tổng quan học vụ</p>
      <h1 className="text-3xl font-semibold tracking-tight">Chào mừng đến với AMS</h1>
      <p className="max-w-2xl text-sm leading-6 text-muted">Trạng thái bên dưới được đọc từ tài khoản và dữ liệu AMS đã lưu. Trang này không truy cập trực tiếp cổng trường hoặc Google.</p>
    </header>
    <section className="grid gap-4 sm:grid-cols-2" aria-label="Trạng thái tài khoản và dữ liệu">
      <article className={card}><h2 className="font-semibold">Nguồn học vụ Phenikaa</h2>
        {phenikaa.isPending ? <p>Đang kiểm tra…</p> : phenikaa.isError ? <><p role="alert">{readError(phenikaa.error, "Chưa thể kiểm tra nguồn học vụ.")}</p><Retry retry={() => void phenikaa.refetch()} /></>
          : !phenikaa.data ? <p>Không khả dụng: tích hợp Phenikaa chưa được bật.</p>
          : <><p>{({ CONNECTED: "Đã kết nối", RECONNECTION_REQUIRED: "Cần kết nối lại", DISCONNECTED: "Chưa kết nối" })[phenikaa.data.status]}</p>
            {phenikaa.data.lastSuccessfulAccessAt && <p className="text-sm text-muted">Truy cập thành công gần nhất: {localTime(phenikaa.data.lastSuccessfulAccessAt)}</p>}</>}
        <Link href="/sync" className="inline-block text-sm font-medium text-primary underline">Mở Đồng bộ</Link>
      </article>
      <article className={card}><h2 className="font-semibold">Lượt đồng bộ gần nhất</h2>
        {current.isPending ? <p>Đang đọc lượt đồng bộ…</p> : current.isError ? <><p role="alert">{readError(current.error, "Chưa thể đọc lượt đồng bộ.")}</p><Retry retry={() => void current.refetch()} /></>
          : !current.data ? <p>Chưa có lượt đồng bộ được ghi nhận.</p>
          : <><p>{runStatus[current.data.status]}</p><p className="text-sm text-muted">Yêu cầu lúc {localTime(current.data.requestedAt)}</p></>}
        <Link href="/sync" className="inline-block text-sm font-medium text-primary underline">Xem lịch sử</Link>
      </article>
      <article className={card}><h2 className="font-semibold">Dữ liệu chương trình</h2>
        {curricula.isPending ? <p>Đang đọc dữ liệu đã lưu…</p> : curricula.isError ? <><p role="alert">{readError(curricula.error, "Chưa thể đọc chương trình đã lưu.")}</p><Retry retry={() => void curricula.refetch()} /></>
          : <p>{curricula.data.items.length ? "Dữ liệu chương trình đã có trong AMS." : "Chưa có chương trình được lưu."}</p>}
        <Link href="/curriculum" className="inline-block text-sm font-medium text-primary underline">Xem Chương trình</Link>
      </article>
      <article className={card}><h2 className="font-semibold">Google Calendar</h2>
        {google.isPending ? <p>Đang kiểm tra…</p> : google.isError ? <><p role="alert">{readError(google.error, "Chưa thể kiểm tra Google Calendar.")}</p><Retry retry={() => void google.refetch()} /></>
          : !google.data.available ? <p>Chưa được cấu hình.</p>
          : <><p>{({ CONNECTED: "Đã kết nối", SETUP_REQUIRED: "Cần hoàn tất thiết lập", RECONNECTION_REQUIRED: "Cần kết nối lại", DISCONNECTED: "Chưa kết nối" })[google.data.status]}</p>
            <p className="text-sm text-muted">Lịch riêng AMS: {google.data.calendarReady ? "đã sẵn sàng" : "chưa sẵn sàng"}</p>
            {google.data.lastSuccessfulAccessAt && <p className="text-sm text-muted">Truy cập thành công gần nhất: {localTime(google.data.lastSuccessfulAccessAt)}</p>}</>}
        <Link href="/settings" className="inline-block text-sm font-medium text-primary underline">Mở Cài đặt</Link>
      </article>
    </section>
    <ApiStatus />
  </div>;
}
