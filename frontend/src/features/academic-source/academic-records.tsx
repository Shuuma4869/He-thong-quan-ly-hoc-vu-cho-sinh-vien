"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { getPhenikaaConnection, syncKeys } from "@/features/sync/api";
import {
  AcademicSourceError, academicKeys, getPrograms, getRecords, getResultDetail, getSourceStatus,
  hasLiveCapability, sourceErrorMessage, type AcademicRecord,
} from "./api";

const panel = "min-w-0 space-y-4 rounded-2xl border bg-card p-5 sm:p-6";
const liveQuery = { retry: false as const, staleTime: 60_000, refetchOnWindowFocus: false,
  refetchOnReconnect: false, refetchOnMount: false as const };
const outcomeLabel = {
  PASSED: "Nguồn báo đạt", FAILED: "Nguồn báo chưa đạt", RETAKE_REQUIRED: "Nguồn báo cần thực hiện lại",
} as const;

function ReadError({ error, retry, retryLabel = "Thử lại" }: { error: Error; retry?: () => void; retryLabel?: string }) {
  return <div className="space-y-3"><p role="alert" className="text-sm">{sourceErrorMessage(error)}</p>
    {retry && <Button variant="outline" onClick={retry}>{retryLabel}</Button>}</div>;
}

function UnknownNotice() {
  return <aside className="rounded-xl border bg-background/60 p-4 text-sm leading-6" aria-label="Giới hạn dữ liệu nguồn">
    <p className="font-medium">Độ đầy đủ của dữ liệu nguồn chưa được xác nhận.</p>
    <p className="mt-1 text-muted">AMS chưa xác định tín chỉ đạt của từng lần học, bản ghi có được tính GPA hay không, hoặc tổng kết nào là kết quả hiện hành nếu nguồn có nhiều phiên bản.</p>
  </aside>;
}

function ScoreComponents({ components, title }: { components: AcademicRecord["components"]; title: string }) {
  return <div className="space-y-2"><h4 className="text-sm font-medium">{title}</h4>
    {!components.length ? <p className="text-sm text-muted">Không có thành phần nào được nguồn trả về trong lượt đọc này.</p>
      : <ul className="grid gap-2">{components.map((component, index) => <li key={index} className="min-w-0 rounded-lg border bg-background/50 p-3 text-sm">
        <p className="break-words font-medium">{component.code} · {component.name}</p>
        <p className="mt-1 text-muted">Lần thi nguồn báo: {component.examAttempt} · Điểm: {component.score}</p>
      </li>)}</ul>}
  </div>;
}

function ResultDetail({ userId, programRef, detailRef, onStale }: {
  userId: string; programRef: string; detailRef: string; onStale: () => void;
}) {
  const detail = useQuery({ ...liveQuery, queryKey: academicKeys.detail(userId, programRef, detailRef),
    queryFn: ({ signal }) => getResultDetail(programRef, detailRef, signal) });
  return <div className="mt-3 rounded-xl border bg-background/60 p-4">
    {detail.isPending ? <p role="status" className="text-sm">Đang đọc chi tiết tổng kết…</p>
      : detail.isError ? <ReadError error={detail.error} retry={detail.error instanceof AcademicSourceError && detail.error.code === "INVALID_SOURCE_REFERENCE"
        ? onStale : () => void detail.refetch()} retryLabel={detail.error instanceof AcademicSourceError && detail.error.code === "INVALID_SOURCE_REFERENCE"
        ? "Đọc lại danh sách kết quả" : "Thử lại"} />
      : <><p className="mb-3 text-xs text-muted">Độ đầy đủ của dữ liệu nguồn chưa được xác nhận. Các thành phần bên dưới không phải công thức tính điểm.</p>
        <ScoreComponents components={detail.data.components} title="Thành phần được nguồn liên kết với tổng kết" /></>}
  </div>;
}

function RecordCard({ record, index, userId, programRef, detailEnabled, open, onToggle, onStale }: {
  record: AcademicRecord; index: number; userId: string; programRef: string; detailEnabled: boolean;
  open: boolean; onToggle: () => void; onStale: () => void;
}) {
  const result = record.finalResult;
  const detailId = `academic-detail-${index}`;
  return <li className={`${panel} break-words`}>
    <div><p className="text-xs font-medium text-primary">{record.course.code}</p>
      <h3 className="mt-1 text-lg font-semibold">{record.course.name}</h3></div>
    <dl className="grid gap-3 text-sm sm:grid-cols-3">
      <div><dt className="text-muted">Tín chỉ môn</dt><dd>{record.course.credits}</dd></div>
      <div><dt className="text-muted">Học kỳ của bản ghi điểm</dt><dd>{record.semester.academicYearStart}–{record.semester.academicYearStart + 1} · {record.semester.termCode}</dd></div>
      <div><dt className="text-muted">Lần học nguồn báo</dt><dd>{record.reportedLearningAttempt}</dd></div>
    </dl>
    <ScoreComponents components={record.components} title="Điểm thành phần nguồn trả về" />
    {result && <section className="space-y-3 rounded-xl border bg-background/50 p-4" aria-label="Kết quả tổng kết nguồn quan sát được">
      <h4 className="font-medium">Kết quả tổng kết nguồn quan sát được</h4>
      <p className="text-sm">{outcomeLabel[result.outcome]} · Lần thi nguồn báo: {result.examAttempt}</p>
      <dl className="grid gap-2 text-sm sm:grid-cols-3">
        <div><dt className="text-muted">Điểm số nguồn trả về</dt><dd>{result.numericScore ?? "Chưa có"}</dd></div>
        <div><dt className="text-muted">Điểm chữ nguồn trả về</dt><dd>{result.letterGrade ?? "Chưa có"}</dd></div>
        <div><dt className="text-muted">Điểm quy đổi nguồn trả về</dt><dd>{result.gradePoints ?? "Chưa có"}</dd></div>
      </dl>
      {detailEnabled && <><Button variant="outline" aria-expanded={open} aria-controls={detailId} onClick={onToggle}>
        {open ? "Đóng chi tiết tổng kết" : "Xem chi tiết tổng kết"}</Button>
        <div id={detailId} hidden={!open}>{open && <ResultDetail userId={userId} programRef={programRef} detailRef={result.detailRef} onStale={onStale} />}</div></>}
    </section>}
  </li>;
}

function RecordsPanel({ userId, programRef, detailEnabled, onInvalidProgram }: {
  userId: string; programRef: string; detailEnabled: boolean; onInvalidProgram: () => void;
}) {
  const [openRegistration, setOpenRegistration] = useState<string | null>(null);
  const records = useQuery({ ...liveQuery, queryKey: academicKeys.records(userId, programRef),
    queryFn: ({ signal }) => getRecords(programRef, signal) });
  return <section className="space-y-4" aria-labelledby="records-title">
    <div className="flex flex-wrap items-center justify-between gap-3"><h2 id="records-title" className="text-lg font-semibold">Bản ghi kết quả nguồn trả về</h2>
      <Button variant="outline" disabled={records.isFetching} onClick={() => { setOpenRegistration(null); void records.refetch(); }}>Đọc lại kết quả</Button></div>
    {records.isPending ? <p role="status">Đang đọc kết quả học tập từ nguồn…</p>
      : records.isError ? <ReadError error={records.error} retry={records.error instanceof AcademicSourceError && records.error.code === "INVALID_SOURCE_REFERENCE"
        ? onInvalidProgram : () => void records.refetch()} retryLabel={records.error instanceof AcademicSourceError && records.error.code === "INVALID_SOURCE_REFERENCE"
        ? "Đọc lại chương trình" : "Thử lại"} />
      : <><UnknownNotice />
        {!records.data.records.length && <p className={panel}>Không có bản ghi kết quả nào được nguồn trả về trong lượt đọc này.</p>}
        <ol className="grid gap-4">{records.data.records.map((record, index) => <RecordCard key={`${record.registrationRef}-${index}`}
          record={record} index={index} userId={userId} programRef={programRef} detailEnabled={detailEnabled}
          open={openRegistration === `${record.registrationRef}-${index}`}
          onToggle={() => setOpenRegistration((previous) => previous === `${record.registrationRef}-${index}` ? null : `${record.registrationRef}-${index}`)}
          onStale={() => { setOpenRegistration(null); void records.refetch(); }} />)}</ol></>}
  </section>;
}

export function AcademicRecords({ userId }: { userId: string }) {
  const [selectedProgramRef, setSelectedProgramRef] = useState<string | null>(null);
  const connection = useQuery({ ...liveQuery, queryKey: syncKeys.phenikaa(userId),
    queryFn: ({ signal }) => getPhenikaaConnection(signal) });
  const connected = connection.data?.status === "CONNECTED";
  const status = useQuery({ ...liveQuery, queryKey: academicKeys.status(userId),
    queryFn: ({ signal }) => getSourceStatus(signal), enabled: connected });
  const recordsEnabled = !!status.data && hasLiveCapability(status.data, "ACADEMIC_RECORDS");
  const detailEnabled = !!status.data && hasLiveCapability(status.data, "ACADEMIC_RESULT_DETAIL");
  const programs = useQuery({ ...liveQuery, queryKey: academicKeys.programs(userId),
    queryFn: ({ signal }) => getPrograms(signal), enabled: connected && recordsEnabled });
  const options = programs.data?.programs ?? [];
  const selectedIndex = Math.max(0, options.findIndex((program) => program.programRef === selectedProgramRef));
  const viewed = options[selectedIndex];

  return <div className="mx-auto max-w-5xl space-y-6">
    <header className="space-y-2"><p className="text-sm font-medium text-primary">Học vụ</p>
      <h1 className="text-2xl font-semibold sm:text-3xl">Kết quả học tập</h1>
      <p className="max-w-3xl text-sm leading-6 text-muted">Dữ liệu được đọc trực tiếp từ nguồn học vụ để xem, không phải bản sao đã lưu trong AMS. Độ đầy đủ chưa được xác nhận. Mở trang này không tạo lần học hoặc kết quả học tập trong AMS.</p></header>
    <p className="w-fit rounded-full border bg-card px-3 py-1 text-xs font-medium">Dữ liệu trực tiếp · Chỉ đọc · Độ đầy đủ chưa xác nhận</p>
    <section className={panel} aria-labelledby="source-title"><h2 id="source-title" className="text-lg font-semibold">Nguồn học vụ</h2>
      {connection.isPending ? <p role="status">Đang kiểm tra kết nối…</p>
        : connection.isError ? <ReadError error={connection.error} retry={() => void connection.refetch()} />
        : !connection.data ? <p>Không khả dụng: tích hợp Phenikaa chưa được bật.</p>
        : connection.data.status === "DISCONNECTED" ? <p>Chưa có kết nối học vụ có thể sử dụng.</p>
        : connection.data.status === "RECONNECTION_REQUIRED" ? <p>Cần kết nối lại nguồn học vụ qua quy trình hiện có.</p>
        : <><p>Đã kết nối nguồn học vụ.</p>
          {status.isPending ? <p role="status">Đang kiểm tra khả năng đọc kết quả…</p>
            : status.isError ? <ReadError error={status.error} retry={() => void status.refetch()} />
            : recordsEnabled ? <p className="text-sm text-muted">Kết quả học tập: đọc trực tiếp, chỉ để xem; độ đầy đủ chưa được xác nhận.</p>
            : <p>Khả năng đọc kết quả trực tiếp hiện không khả dụng.</p>}</>}
    </section>
    {connected && recordsEnabled && <section className={panel} aria-labelledby="programs-title">
      <div className="flex flex-wrap items-center justify-between gap-3"><h2 id="programs-title" className="text-lg font-semibold">Chương trình nguồn trả về</h2>
        <Button variant="outline" disabled={programs.isFetching} onClick={() => { setSelectedProgramRef(null); void programs.refetch(); }}>Đọc lại chương trình</Button></div>
      {programs.isPending ? <p role="status">Đang đọc chương trình từ nguồn…</p>
        : programs.isError ? <ReadError error={programs.error} retry={() => void programs.refetch()} />
        : <><p className="text-sm text-muted">Độ đầy đủ của danh sách nguồn chưa được xác nhận.</p>
          {!options.length ? <p>Không có chương trình nào được nguồn trả về trong lượt đọc này.</p>
            : options.length > 1 ? <label className="flex flex-col gap-2 text-sm font-medium">Chương trình đang xem
              <select value={selectedIndex} onChange={(event) => setSelectedProgramRef(options[Number(event.target.value)].programRef)}
                className="h-11 min-w-0 max-w-full rounded-xl border bg-background px-3 text-foreground">
                {options.map((program, index) => <option key={program.programRef} value={index}>{program.label}</option>)}
              </select></label>
              : <p className="break-words text-sm">Chương trình đang xem: <span className="font-medium">{viewed.label}</span></p>}</>}
    </section>}
    {connected && recordsEnabled && viewed && <RecordsPanel key={viewed.programRef} userId={userId}
      programRef={viewed.programRef} detailEnabled={detailEnabled}
      onInvalidProgram={() => { setSelectedProgramRef(null); void programs.refetch(); }} />}
  </div>;
}
