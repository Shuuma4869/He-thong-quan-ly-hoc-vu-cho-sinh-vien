"use client";

import { useEffect, useRef } from "react";
import Link from "next/link";
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { ApiError } from "@/features/auth/api";
import { getCurrentRun, getHistory, getPhenikaaConnection, getRun, isActive, readError, requestSync, syncKeys, type SyncRun } from "./api";
import { failureMessage, localTime, runStatus, stepStatus } from "./presentation";

const panel = "min-w-0 space-y-4 rounded-2xl border bg-card p-5 sm:p-6";

function QueryError({ error, retry, message }: { error: Error; retry: () => void; message: string }) {
  return <div className="space-y-3"><p role="alert" className="text-sm">{readError(error, message)}</p>
    <Button variant="outline" onClick={retry}>Thử lại</Button></div>;
}

function RunDetails({ run }: { run: SyncRun }) {
  const failure = failureMessage(run.failureCode);
  return <div className="space-y-4">
    <div className="flex flex-wrap items-center gap-2"><p className="font-medium">{runStatus[run.status]}</p>
      <span className="text-xs text-muted">{run.trigger === "MANUAL" ? "Yêu cầu thủ công" : "Tự xếp hàng"}</span></div>
    <dl className="grid gap-2 text-sm sm:grid-cols-2">
      <div><dt className="text-muted">Yêu cầu lúc</dt><dd>{localTime(run.requestedAt)}</dd></div>
      <div><dt className="text-muted">Hoàn tất lúc</dt><dd>{localTime(run.finishedAt)}</dd></div>
      {run.nextAttemptAt && isActive(run) && <div><dt className="text-muted">Dự kiến thử lại</dt><dd>{localTime(run.nextAttemptAt)}</dd></div>}
      {run.attemptCount > 1 && <div><dt className="text-muted">Số lần xử lý</dt><dd>{run.attemptCount}</dd></div>}
    </dl>
    {run.status === "PARTIAL" && <p className="text-sm text-muted">Một bước đã thành công, nhưng lượt đồng bộ chưa hoàn tất toàn bộ. Xem trạng thái từng bước bên dưới.</p>}
    <ul className="grid gap-2 sm:grid-cols-2" aria-label="Các bước đồng bộ">
      <li className="rounded-xl border p-3 text-sm"><span className="font-medium">Hồ sơ</span><span className="ml-2">{stepStatus[run.profileStepStatus]}</span></li>
      <li className="rounded-xl border p-3 text-sm"><span className="font-medium">Chương trình và danh mục</span><span className="ml-2">{stepStatus[run.curriculumStepStatus]}</span></li>
    </ul>
    {failure && <p role="alert" className="text-sm">{failure}</p>}
    {isActive(run) && <p className="text-xs text-muted">Trạng thái được kiểm tra lại trong lúc lượt đồng bộ đang hoạt động.</p>}
  </div>;
}

function SyncHistory({ userId, available }: { userId: string; available: boolean }) {
  const history = useInfiniteQuery({ queryKey: syncKeys.history(userId), initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam, signal }) => getHistory(pageParam, signal),
    getNextPageParam: (last) => last.nextCursor ?? undefined, retry: false, enabled: available });
  const items = history.data?.pages.flatMap((page) => page.items) ?? [];
  return <section className={panel} aria-labelledby="history-title"><h2 id="history-title" className="text-lg font-semibold">Lịch sử đồng bộ</h2>
    {!available ? <p>Lịch sử không khả dụng khi tích hợp Phenikaa chưa được bật.</p> : history.isPending ? <p role="status">Đang tải lịch sử…</p> : history.isError && !history.data
      ? <QueryError error={history.error} retry={() => void history.refetch()} message="Chưa thể đọc lịch sử đồng bộ." />
      : <><ol className="space-y-2">{items.map((run) => <li key={run.runId} className="min-w-0 rounded-xl border p-3 text-sm">
        <p className="font-medium">{runStatus[run.status]}</p><p className="text-muted">{localTime(run.requestedAt)} · {run.trigger === "MANUAL" ? "Thủ công" : "Tự xếp hàng"}</p>
        {run.failureCode && <p>{failureMessage(run.failureCode)}</p>}
      </li>)}</ol>
      {!items.length && <p>Chưa có lượt đồng bộ nào.</p>}
      {history.isFetchNextPageError && <p role="alert">Chưa tải được trang tiếp theo. Bạn có thể thử lại.</p>}
      {history.hasNextPage && <Button variant="outline" disabled={history.isFetchingNextPage} onClick={() => void history.fetchNextPage()}>
        {history.isFetchingNextPage ? "Đang tải…" : "Tải thêm"}</Button>}</>}
  </section>;
}

export function SyncCenter({ userId }: { userId: string }) {
  const client = useQueryClient();
  const completedRun = useRef<string | null>(null);
  const phenikaa = useQuery({ queryKey: syncKeys.phenikaa(userId), queryFn: ({ signal }) => getPhenikaaConnection(signal), retry: false });
  const current = useQuery({ queryKey: syncKeys.current(userId), queryFn: ({ signal }) => getCurrentRun(signal), retry: false });
  const mutation = useMutation({ mutationFn: requestSync, onSuccess: (run) => {
    client.setQueryData(syncKeys.current(userId), run);
    client.setQueryData(syncKeys.run(userId, run.runId), run);
    void client.invalidateQueries({ queryKey: syncKeys.history(userId) });
  } });
  const activeId = current.data && isActive(current.data) ? current.data.runId : null;
  const tracked = useQuery({ queryKey: syncKeys.run(userId, activeId ?? "none"),
    queryFn: ({ signal }) => getRun(activeId!, signal), enabled: !!activeId, retry: false,
    refetchInterval: (query) => query.state.error || !query.state.data || !isActive(query.state.data) ? false : 2500,
  });

  useEffect(() => {
    const run = tracked.data;
    if (!run) return;
    client.setQueryData(syncKeys.current(userId), run);
    if (isActive(run) || completedRun.current === run.runId) return;
    completedRun.current = run.runId;
    void client.invalidateQueries({ queryKey: syncKeys.history(userId) });
    if (run.status === "SUCCEEDED" || run.status === "PARTIAL") {
      void client.invalidateQueries({ queryKey: ["curricula", userId] });
      void client.invalidateQueries({ queryKey: ["curriculum", userId] });
      void client.invalidateQueries({ queryKey: ["curriculum-courses", userId] });
    }
  }, [tracked.data, client, userId]);

  const displayed = tracked.data ?? current.data;
  const sourceDisabled = phenikaa.isSuccess && phenikaa.data === null;
  const canRequest = phenikaa.data?.status === "CONNECTED" && !activeId && !mutation.isPending && !current.isPending && !current.isError;
  const requestError = mutation.error instanceof ApiError ? ({ 409: "Kết nối học vụ không còn sẵn sàng. Hãy kiểm tra lại trạng thái nguồn.",
    429: "Bạn vừa yêu cầu đồng bộ. Vui lòng chờ trước khi thử lại.", 401: "Phiên đăng nhập đã hết hạn.",
    403: "Tài khoản không thể đồng bộ trong phiên hiện tại.", 503: "Hàng đợi đồng bộ tạm thời không sẵn sàng." } as Record<number, string>)[mutation.error.status]
    : null;

  return <div className="mx-auto max-w-4xl space-y-6">
    <header className="space-y-2"><p className="text-sm font-medium text-primary">Dữ liệu học vụ</p><h1 className="text-2xl font-semibold sm:text-3xl">Đồng bộ</h1>
      <p className="text-sm leading-6 text-muted">AMS hiện chỉ làm mới hồ sơ và phần chương trình, danh mục đã hỗ trợ lưu. Chưa đồng bộ điểm, lịch học hay lịch thi.</p></header>
    <section className={panel} aria-labelledby="source-title"><h2 id="source-title" className="text-lg font-semibold">Nguồn Phenikaa</h2>
      {phenikaa.isPending ? <p role="status">Đang kiểm tra nguồn…</p> : phenikaa.isError
        ? <QueryError error={phenikaa.error} retry={() => void phenikaa.refetch()} message="Chưa thể kiểm tra nguồn học vụ." />
        : !phenikaa.data ? <p>Không khả dụng: tích hợp Phenikaa chưa được bật trên máy chủ.</p>
        : <><p>{({ CONNECTED: "Đã kết nối", RECONNECTION_REQUIRED: "Cần kết nối lại", DISCONNECTED: "Chưa kết nối" })[phenikaa.data.status]}</p>
          {phenikaa.data.lastSuccessfulAccessAt && <p className="text-sm text-muted">Truy cập thành công gần nhất: {localTime(phenikaa.data.lastSuccessfulAccessAt)}</p>}
          {phenikaa.data.status === "RECONNECTION_REQUIRED" && <p className="text-sm text-muted">Cần cấp lại phiên qua quy trình hiện có. AMS chưa có màn hình tự kết nối Phenikaa.</p>}</>}
      <Button disabled={!canRequest} onClick={() => void mutation.mutate()}> {mutation.isPending ? "Đang gửi yêu cầu…" : "Đồng bộ ngay"}</Button>
      {activeId && <p className="text-sm text-muted">Đã có lượt đồng bộ đang hoạt động; không gửi thêm yêu cầu trùng.</p>}
      {mutation.isError && <p role="alert" className="text-sm">{requestError ?? "Chưa gửi được yêu cầu đồng bộ. Vui lòng thử lại."}</p>}
    </section>
    <section className={panel} aria-labelledby="current-title"><h2 id="current-title" className="text-lg font-semibold">Lượt gần nhất</h2>
      {current.isPending ? <p role="status">Đang đọc lượt gần nhất…</p> : current.isError
        ? <QueryError error={current.error} retry={() => void current.refetch()} message="Chưa thể đọc lượt đồng bộ gần nhất." />
        : sourceDisabled ? <p>Đồng bộ không khả dụng khi tích hợp Phenikaa chưa được bật.</p>
        : !displayed ? <p>Chưa có lượt đồng bộ được ghi nhận.</p> : <RunDetails run={displayed} />}
      {tracked.isError && <QueryError error={tracked.error} retry={() => void tracked.refetch()} message="Mất kết nối khi theo dõi lượt đồng bộ. Trạng thái trên có thể đã cũ." />}
    </section>
    <SyncHistory userId={userId} available={!sourceDisabled} />
    <p className="text-sm text-muted">Xem dữ liệu đã lưu tại <Link href="/curriculum" className="text-primary underline">Chương trình</Link>.</p>
  </div>;
}
