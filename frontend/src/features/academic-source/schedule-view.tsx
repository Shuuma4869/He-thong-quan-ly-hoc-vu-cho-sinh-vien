"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { getPhenikaaConnection, syncKeys } from "@/features/sync/api";
import { AcademicSourceError, academicKeys, getExamPeriods, getExams, getSchedule, getSourceStatus,
  hasLiveCapability, sourceErrorMessage, type Exams, type Schedule } from "./api";

const panel = "min-w-0 space-y-4 rounded-2xl border bg-card p-4 sm:p-6";
const liveQuery = { retry: false as const, refetchOnWindowFocus: false,
  refetchOnReconnect: false, refetchOnMount: false as const };
const sourceZone = "Asia/Ho_Chi_Minh";
const unknown = "Độ đầy đủ của dữ liệu nguồn chưa được xác nhận.";
const kindLabel = { CLASS: "Lớp học", EXAM: "Thi trong lịch cá nhân", UNKNOWN: "Nguồn chưa phân loại" } as const;

function sourceToday() {
  const parts = new Intl.DateTimeFormat("en-US", { timeZone: sourceZone, year: "numeric",
    month: "2-digit", day: "2-digit" }).formatToParts(new Date());
  const part = (type: string) => parts.find((item) => item.type === type)?.value ?? "";
  return `${part("year")}-${part("month")}-${part("day")}`;
}

function addDays(day: string, count: number) {
  const date = new Date(`${day}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + count);
  return date.toISOString().slice(0, 10);
}

export function validScheduleRange(from: string, through: string) {
  const pattern = /^\d{4}-\d{2}-\d{2}$/;
  if (!pattern.test(from) || !pattern.test(through)) return false;
  const start = Date.parse(`${from}T00:00:00Z`);
  const end = Date.parse(`${through}T00:00:00Z`);
  if (!Number.isFinite(start) || !Number.isFinite(end)
      || new Date(start).toISOString().slice(0, 10) !== from
      || new Date(end).toISOString().slice(0, 10) !== through) return false;
  const days = (end - start) / 86_400_000;
  return days >= 0 && days <= 30;
}

function dateLabel(date: string) {
  const [year, month, day] = date.split("-");
  return `${day}/${month}/${year}`;
}

function timeLabel(value: string) { return value.slice(0, 5); }

function SourceNotice() {
  return <aside className="rounded-xl border bg-background/60 p-4 text-sm leading-6" aria-label="Giới hạn dữ liệu nguồn">
    <p className="font-medium">Dữ liệu trực tiếp · Chỉ đọc</p>
    <p className="mt-1 text-muted">Lịch chưa được lưu trong AMS. {unknown} Định danh của từng buổi học và lần thi chưa được xác minh; dữ liệu này chưa dùng để phát hiện thay đổi hoặc tạo sự kiện Google Calendar.</p>
  </aside>;
}

function ReadError({ error, retry, examPeriod = false }: { error: Error; retry: () => void; examPeriod?: boolean }) {
  return <div className="space-y-2"><p role="alert">{sourceErrorMessage(error, examPeriod ? "exam-period" : undefined)}</p>
    <Button type="button" variant="outline" onClick={retry}>Thử lại</Button></div>;
}

function ScheduleRows({ data }: { data: Schedule }) {
  const entries = [...data.entries].sort((a, b) => a.date.localeCompare(b.date)
    || (a.startsAt ?? "").localeCompare(b.startsAt ?? ""));
  return <div className="space-y-3">
    <p className="text-sm text-muted">Khoảng đã đọc: {dateLabel(data.from)} – {dateLabel(data.through)}. Múi giờ nguồn: {data.zone}.</p>
    <p className="text-sm text-muted">{unknown} Định danh từng buổi: chưa xác minh.</p>
    {!entries.length ? <p>Nguồn không trả bản ghi lịch nào trong khoảng đã chọn.</p>
      : <ol className="grid gap-3">{entries.map((entry, index) => <li key={`${entry.date}-${index}`}
        className="min-w-0 space-y-2 rounded-xl border bg-background/50 p-4 text-sm">
        <p className="break-words font-semibold">{entry.courseName ?? "Tên môn chưa được nguồn cung cấp"}</p>
        <p>{dateLabel(entry.date)} · {kindLabel[entry.kind]}</p>
        <p>{entry.startsAt && entry.endsAt ? `${timeLabel(entry.startsAt)}–${timeLabel(entry.endsAt)}`
          : entry.startsAt ? `Bắt đầu ${timeLabel(entry.startsAt)} · Giờ kết thúc chưa được nguồn cung cấp`
            : "Giờ chưa được nguồn cung cấp"}</p>
        <p className="break-words">{entry.room ?? "Phòng chưa được nguồn cung cấp"}</p>
        <p className="break-words">{entry.lecturer ?? "Giảng viên chưa được nguồn cung cấp"}</p>
      </li>)}</ol>}
  </div>;
}

function ExamRows({ data }: { data: Exams }) {
  return <div className="space-y-3">
    <p className="break-words text-sm text-muted">Kỳ thi đang xem: {data.period.label}. Múi giờ nguồn: {data.zone}.</p>
    <p className="text-sm text-muted">{unknown} Định danh từng lần thi: chưa xác minh.</p>
    {!data.entries.length ? <p>Nguồn không trả bản ghi lịch thi nào cho kỳ đã chọn.</p>
      : <ol className="grid gap-3">{data.entries.map((entry, index) => <li key={index}
        className="min-w-0 space-y-2 rounded-xl border bg-background/50 p-4 text-sm">
        <p className="break-words font-semibold">{entry.courseCode} · {entry.courseName}</p>
        <p>{dateLabel(entry.date)} · Bắt đầu {timeLabel(entry.startsAt)}
          {entry.endsAt ? ` · Kết thúc ${timeLabel(entry.endsAt)}` : " · Giờ kết thúc chưa được nguồn cung cấp"}</p>
        <p>Lần thi nguồn báo: {entry.examAttempt}</p>
        {entry.examSession && <p className="break-words">Ca thi nguồn báo: {entry.examSession}</p>}
        <p className="break-words">{entry.room ?? "Phòng chưa được nguồn cung cấp"}</p>
      </li>)}</ol>}
  </div>;
}

export function ScheduleView({ userId }: { userId: string }) {
  const today = sourceToday();
  const [from, setFrom] = useState(today);
  const [through, setThrough] = useState(() => addDays(today, 13));
  const [requestedRange, setRequestedRange] = useState<{ from: string; through: string } | null>(null);
  const [tab, setTab] = useState<"schedule" | "exams">("schedule");
  const [periodsRequested, setPeriodsRequested] = useState(false);
  const [selectedPeriodRef, setSelectedPeriodRef] = useState<string | null>(null);
  const [requestedPeriodRef, setRequestedPeriodRef] = useState<string | null>(null);
  const connection = useQuery({ ...liveQuery, queryKey: syncKeys.phenikaa(userId),
    queryFn: ({ signal }) => getPhenikaaConnection(signal) });
  const connected = connection.data?.status === "CONNECTED";
  const status = useQuery({ ...liveQuery, queryKey: academicKeys.status(userId),
    queryFn: ({ signal }) => getSourceStatus(signal), enabled: connected });
  const scheduleReady = !!status.data && connected && hasLiveCapability(status.data, "SCHEDULE");
  const examsReady = !!status.data && connected && hasLiveCapability(status.data, "EXAMS");
  const schedule = useQuery({ ...liveQuery,
    queryKey: academicKeys.schedule(userId, requestedRange?.from ?? "", requestedRange?.through ?? ""),
    queryFn: ({ signal }) => getSchedule(requestedRange!.from, requestedRange!.through, signal),
    enabled: scheduleReady && tab === "schedule" && !!requestedRange });
  const periods = useQuery({ ...liveQuery, queryKey: academicKeys.examPeriods(userId),
    queryFn: ({ signal }) => getExamPeriods(signal), enabled: examsReady && tab === "exams" && periodsRequested });
  const options = periods.data?.periods ?? [];
  const selected = options.find((period) => period.periodRef === selectedPeriodRef) ?? options[0];
  const exams = useQuery({ ...liveQuery, queryKey: academicKeys.exams(userId, requestedPeriodRef ?? ""),
    queryFn: ({ signal }) => getExams(requestedPeriodRef!, signal),
    enabled: examsReady && tab === "exams" && !!requestedPeriodRef
      && options.some((period) => period.periodRef === requestedPeriodRef) });

  function readSchedule() {
    if (!validScheduleRange(from, through)) return;
    if (requestedRange?.from === from && requestedRange.through === through) void schedule.refetch();
    else setRequestedRange({ from, through });
  }

  function readExams() {
    if (!selected) return;
    if (requestedPeriodRef === selected.periodRef) void exams.refetch();
    else setRequestedPeriodRef(selected.periodRef);
  }

  return <div className="mx-auto max-w-5xl space-y-6">
    <header className="space-y-2"><p className="text-sm font-medium text-primary">Lịch</p>
      <h1 className="text-2xl font-semibold sm:text-3xl">Lịch học &amp; lịch thi</h1></header>
    <SourceNotice />
    <section className={panel} aria-label="Nguồn lịch">
      {connection.isPending ? <p role="status">Đang kiểm tra kết nối…</p>
        : connection.isError ? <ReadError error={connection.error} retry={() => void connection.refetch()} />
        : !connection.data ? <p>Không khả dụng: tích hợp Phenikaa chưa được bật.</p>
        : connection.data.status === "DISCONNECTED" ? <p>Chưa có kết nối học vụ có thể sử dụng.</p>
        : connection.data.status === "RECONNECTION_REQUIRED" ? <p>Cần kết nối lại nguồn học vụ qua quy trình hiện có.</p>
        : status.isPending ? <p role="status">Đang kiểm tra khả năng đọc lịch…</p>
        : status.isError ? <ReadError error={status.error} retry={() => void status.refetch()} />
        : status.data.connectionState === "RECONNECTION_REQUIRED" ? <p>Cần kết nối lại nguồn học vụ qua quy trình hiện có.</p>
        : status.data.connectionState !== "CONNECTED" ? <p>Chưa có kết nối học vụ có thể sử dụng.</p>
        : <p>Đã kết nối nguồn học vụ. Chọn phần muốn đọc bên dưới.</p>}
    </section>
    {connected && status.data?.connectionState === "CONNECTED" && <>
      <div className="flex flex-wrap gap-2" role="group" aria-label="Loại lịch">
        <Button type="button" variant={tab === "schedule" ? "default" : "outline"}
          aria-pressed={tab === "schedule"} onClick={() => setTab("schedule")}>Lịch cá nhân</Button>
        <Button type="button" variant={tab === "exams" ? "default" : "outline"}
          aria-pressed={tab === "exams"} onClick={() => setTab("exams")}>Lịch thi</Button>
      </div>
      {tab === "schedule" ? <section className={panel} aria-label="Lịch cá nhân">
        {!scheduleReady ? <p>Khả năng đọc lịch cá nhân trực tiếp hiện không khả dụng.</p> : <>
          <p className="text-sm text-muted">Chọn 1–31 ngày, theo múi giờ nguồn {sourceZone}. Thay đổi ô ngày không tự gọi nguồn.</p>
          <div className="grid gap-3 sm:grid-cols-2">
            <label className="text-sm font-medium">Từ ngày<input type="date" value={from} onChange={(event) => setFrom(event.target.value)}
              className="mt-1 h-11 w-full min-w-0 rounded-lg border bg-background px-3" /></label>
            <label className="text-sm font-medium">Đến ngày<input type="date" value={through} onChange={(event) => setThrough(event.target.value)}
              className="mt-1 h-11 w-full min-w-0 rounded-lg border bg-background px-3" /></label>
          </div>
          {!validScheduleRange(from, through) && <p role="alert">Khoảng ngày phải hợp lệ và không vượt quá 31 ngày.</p>}
          <Button type="button" disabled={!validScheduleRange(from, through) || schedule.isFetching}
            onClick={readSchedule}>Đọc lịch</Button>
          {requestedRange && (schedule.isPending ? <p role="status">Đang đọc lịch cá nhân…</p>
            : schedule.isError ? <ReadError error={schedule.error} retry={() => void schedule.refetch()} />
              : <ScheduleRows data={schedule.data} />)}
        </>}
      </section> : <section className={panel} aria-label="Lịch thi">
        {!examsReady ? <p>Khả năng đọc lịch thi trực tiếp hiện không khả dụng.</p> : <>
          <p className="text-sm text-muted">Kỳ thi là bộ lọc từ nguồn, chưa phải học kỳ AMS đã xác minh.</p>
          <Button type="button" variant="outline" disabled={periods.isFetching} onClick={() => {
            setRequestedPeriodRef(null); setSelectedPeriodRef(null); setPeriodsRequested(true);
            if (periodsRequested) void periods.refetch();
          }}>Đọc danh sách kỳ thi</Button>
          {periodsRequested && (periods.isPending ? <p role="status">Đang đọc danh sách kỳ thi…</p>
            : periods.isError ? <ReadError error={periods.error} retry={() => void periods.refetch()} examPeriod />
              : !options.length ? <p>Nguồn không trả bộ lọc kỳ thi nào trong lượt đọc này. {unknown}</p>
                : <div className="space-y-3">
                  <label className="block text-sm font-medium">Kỳ thi đang xem
                    <select value={Math.max(0, options.findIndex((period) => period.periodRef === selectedPeriodRef))} onChange={(event) => {
                      setSelectedPeriodRef(options[Number(event.target.value)].periodRef); setRequestedPeriodRef(null);
                    }} className="mt-1 h-11 w-full min-w-0 rounded-lg border bg-background px-3">
                      {options.map((period, index) => <option key={index} value={index}>{period.label}</option>)}
                    </select></label>
                  <Button type="button" disabled={exams.isFetching} onClick={readExams}>Đọc lịch thi</Button>
                  {requestedPeriodRef && (exams.isPending ? <p role="status">Đang đọc lịch thi…</p>
                    : exams.isError ? <ReadError error={exams.error} retry={() => {
                      if (exams.error instanceof AcademicSourceError
                          && exams.error.code === "INVALID_SOURCE_REFERENCE") {
                        setRequestedPeriodRef(null); void periods.refetch();
                      } else void exams.refetch();
                    }} examPeriod /> : <ExamRows data={exams.data} />)}
                </div>)}
        </>}
      </section>}
    </>}
  </div>;
}
