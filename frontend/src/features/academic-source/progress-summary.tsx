"use client";

import Link from "next/link";
import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { curriculumKeys, getCurriculumSelection } from "@/features/curriculum/api";
import { academicKeys, getProgressSummary, hasLiveCapability, sourceErrorMessage, type SourceStatus } from "./api";

function SummaryRead({ userId, curriculumId }: { userId: string; curriculumId: string }) {
  const [hasRead, setHasRead] = useState(false);
  const summary = useQuery({
    queryKey: academicKeys.progressSummary(userId, curriculumId),
    queryFn: ({ signal }) => getProgressSummary(signal),
    enabled: false, retry: false, refetchOnWindowFocus: false, refetchOnReconnect: false,
    refetchInterval: false, gcTime: 0,
  });
  async function read() {
    setHasRead(false);
    await summary.refetch();
    setHasRead(true);
  }
  const data = hasRead && !summary.isFetching && !summary.isError && summary.data?.curriculum.id === curriculumId ? summary.data : null;
  return <div className="space-y-4">
    <Button onClick={() => void read()} disabled={summary.isFetching}>Đọc tổng hợp tích lũy từ nguồn</Button>
    {summary.isFetching && <p role="status">Đang đọc tổng hợp tích lũy…</p>}
    {hasRead && summary.isError && <p role="alert">{sourceErrorMessage(summary.error)}</p>}
    {hasRead && summary.data && summary.data.curriculum.id !== curriculumId &&
      <p role="alert">Chương trình theo dõi đã thay đổi. Hãy tải lại trang trước khi đọc tiếp.</p>}
    {data && <div className="space-y-3 rounded-xl border bg-background/60 p-4">
      <p className="text-sm font-medium">Dữ liệu trực tiếp · Chỉ đọc</p>
      <p className="text-sm text-muted">AMS không tự tính các giá trị dưới đây. Độ đầy đủ của dữ liệu nguồn chưa được xác nhận.</p>
      <h3 className="font-medium">Điểm trung bình tích lũy — nguồn báo</h3>
      <dl className="grid gap-3 text-sm sm:grid-cols-3">
        <div><dt className="text-muted">Thang 4</dt><dd>{data.summary.cumulativeAverageScale4}</dd></div>
        <div><dt className="text-muted">Thang 10</dt><dd>{data.summary.cumulativeAverageScale10}</dd></div>
        <div><dt className="text-muted">Giá trị tín chỉ tích lũy — nguồn báo</dt><dd>{data.summary.sourceAccumulatedCredits}</dd></div>
      </dl>
    </div>}
  </div>;
}

export function ProgressSummaryPanel({ userId, connected, status }: {
  userId: string; connected: boolean; status: SourceStatus | undefined;
}) {
  const selection = useQuery({
    queryKey: curriculumKeys.selection(userId),
    queryFn: ({ signal }) => getCurriculumSelection(signal),
    retry: false, refetchOnWindowFocus: true,
  });
  const curriculum = selection.data?.curriculum;
  const available = connected && !!status && hasLiveCapability(status, "ACADEMIC_PROGRESS_SUMMARY");
  return <section className="min-w-0 space-y-4 rounded-2xl border bg-card p-5 sm:p-6" aria-labelledby="progress-summary-title">
    <h2 id="progress-summary-title" className="text-lg font-semibold">Tổng hợp tích lũy do nguồn báo</h2>
    {selection.isPending ? <p role="status">Đang kiểm tra chương trình theo dõi…</p>
      : selection.isError ? <p role="alert">Chưa thể đọc chương trình theo dõi trong AMS.</p>
      : !curriculum ? <p>Hãy chọn chương trình theo dõi trước. <Link className="underline" href="/curriculum">Mở Chương trình</Link></p>
      : <><p className="break-words text-sm">Chương trình theo dõi trong AMS: <span className="font-medium">{curriculum.name}</span></p>
        {!available ? <p className="text-sm text-muted">Tổng hợp tích lũy trực tiếp hiện không khả dụng từ nguồn.</p>
          : <SummaryRead key={curriculum.id} userId={userId} curriculumId={curriculum.id} />}</>}
  </section>;
}
