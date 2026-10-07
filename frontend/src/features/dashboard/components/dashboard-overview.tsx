"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { ApiStatus } from "./api-status";
import { curriculumKeys, getCurriculumSelection } from "@/features/curriculum/api";
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
  const selection = useQuery({ queryKey: curriculumKeys.selection(userId), queryFn: ({ signal }) => getCurriculumSelection(signal), retry: false, staleTime: 0 });
  const sourceDisabled = phenikaa.isSuccess && phenikaa.data === null;

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
          : sourceDisabled ? <p>Đồng bộ không khả dụng khi tích hợp Phenikaa chưa được bật.</p>
          : !current.data && phenikaa.isPending ? <p>Đang kiểm tra nguồn học vụ…</p>
          : !current.data && phenikaa.isError ? <p>Chưa thể xác định lượt đồng bộ khi chưa đọc được trạng thái nguồn.</p>
          : !current.data ? <p>Chưa có lượt đồng bộ được ghi nhận.</p>
          : <><p>{runStatus[current.data.status]}</p><p className="text-sm text-muted">Yêu cầu lúc {localTime(current.data.requestedAt)}</p></>}
        <Link href="/sync" className="inline-block text-sm font-medium text-primary underline">Xem lịch sử</Link>
      </article>
      <article className={card}><h2 className="font-semibold">Chương trình theo dõi</h2>
        {selection.isPending ? <p>Đang đọc chương trình theo dõi…</p> : selection.isError ? <><p role="alert">{readError(selection.error, "Chưa thể đọc chương trình theo dõi.")}</p><Retry retry={() => void selection.refetch()} /></>
          : selection.data.curriculum ? <><p className="break-words">{selection.data.curriculum.code} — {selection.data.curriculum.name}</p>
            <p className="text-sm text-muted">Tín chỉ tối thiểu theo chương trình: {selection.data.curriculum.minimumCredits}</p></>
          : <p>Chưa chọn chương trình theo dõi.</p>}
        <Link href="/curriculum" className="inline-block text-sm font-medium text-primary underline">Mở Chương trình</Link>
        {selection.isSuccess && selection.data.curriculum && <Link href="/planner" className="ml-4 inline-block text-sm font-medium text-primary underline">Mở kế hoạch</Link>}
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
