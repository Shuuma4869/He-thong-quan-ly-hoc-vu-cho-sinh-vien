"use client";

import { useEffect, useState } from "react";
import { useInfiniteQuery } from "@tanstack/react-query";
import { BookOpenCheck, Library, RefreshCw, Search } from "lucide-react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { ApiError } from "@/features/auth/api";
import * as api from "./api";

const queryOptions = { staleTime: 0, retry: false as const, refetchOnMount: "always" as const };
const panel = "rounded-2xl border bg-card p-5";
const field = "min-w-0 rounded-xl border bg-card px-3 py-2 text-sm text-foreground";

function ReadError({ error, retry }: { error: Error; retry: () => void }) {
  if (error instanceof ApiError && (error.status === 401 || error.status === 403))
    return <p role="alert">Không thể truy cập dữ liệu bằng phiên hiện tại. <Link className="text-primary underline" href="/login">Đăng nhập lại</Link></p>;
  return <div role="alert" className="space-y-3"><p>Chưa thể đọc dữ liệu đã lưu. Vui lòng thử lại.</p><Button variant="outline" onClick={retry}>Thử lại</Button></div>;
}

function Requirement({ value }: { value: "REQUIRED" | "ELECTIVE" }) {
  return <span className={`inline-block rounded-lg px-2 py-1 text-xs ${value === "REQUIRED" ? "bg-primary-soft text-primary" : "border text-muted"}`}>
    {value === "REQUIRED" ? "Bắt buộc" : "Tự chọn"}
  </span>;
}

function More({ available, busy, load, label }: { available: boolean; busy: boolean; load: () => void; label: string }) {
  return available ? <Button variant="outline" disabled={busy} onClick={load}>{busy ? "Đang tải thêm…" : label}</Button> : null;
}

function Loading({ children }: { children: React.ReactNode }) {
  return <div className={`${panel} min-h-64 space-y-5`} role="status">
    <p className="text-sm text-muted">{children}</p>
    <div aria-hidden="true" className="space-y-4 motion-safe:animate-pulse"><div className="h-6 w-2/3 rounded bg-primary-soft" /><div className="h-16 rounded-xl bg-primary-soft" /><div className="h-8 w-1/2 rounded bg-primary-soft" /></div>
  </div>;
}

export function CurriculumBrowser({ userId }: { userId: string }) {
  const [view, setView] = useState<"curriculum" | "catalog">("curriculum");
  return <div className="space-y-6">
    <header className="space-y-2"><p className="text-sm font-medium text-primary">Học vụ / Chương trình</p>
      <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">Chương trình đào tạo</h1>
      <p className="max-w-3xl text-sm leading-6 text-muted">Chương trình và môn học đã lưu trong AMS. Trang này không đọc trực tiếp cổng trường và không tự đồng bộ dữ liệu.</p>
    </header>
    <div className="flex flex-wrap gap-2" role="group" aria-label="Chọn nội dung">
      <Button variant={view === "curriculum" ? "default" : "outline"} className={view === "curriculum" ? "dark:text-background" : undefined} aria-pressed={view === "curriculum"} onClick={() => setView("curriculum")}><BookOpenCheck className="size-4" aria-hidden="true" />Chương trình</Button>
      <Button variant={view === "catalog" ? "default" : "outline"} className={view === "catalog" ? "dark:text-background" : undefined} aria-pressed={view === "catalog"} onClick={() => setView("catalog")}><Library className="size-4" aria-hidden="true" />Danh mục môn</Button>
    </div>
    {view === "curriculum" ? <Curricula userId={userId} /> : <section className="space-y-4" aria-label="Danh mục môn">
      <p className="text-sm text-muted">Tất cả môn đã lưu của tài khoản, kể cả môn chưa có liên kết chương trình đã lưu. Việc chưa có liên kết không có nghĩa môn nằm ngoài chương trình.</p>
      <CourseList userId={userId} />
    </section>}
  </div>;
}

function Curricula({ userId }: { userId: string }) {
  const [selectedId, setSelectedId] = useState<string>();
  const query = useInfiniteQuery({ ...queryOptions, queryKey: ["curricula", userId], initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam, signal }) => api.getCurricula(pageParam, signal), getNextPageParam: (last) => last.nextCursor ?? undefined });
  if (query.isPending) return <Loading>Đang tải chương trình…</Loading>;
  if (query.isError) return <ReadError error={query.error} retry={() => void query.refetch()} />;
  const curricula = query.data.pages.flatMap((p) => p.items);
  const selected = curricula.find((c) => c.id === selectedId) ?? curricula[0];
  return <div className="space-y-6">
    <div className="flex flex-wrap items-end gap-3">
      {curricula.length > 1 && <label className="flex min-w-0 flex-1 basis-full flex-col gap-2 text-sm font-medium sm:basis-0">Chọn chương trình để xem
        <select className={`${field} w-full`} value={selected.id} onChange={(e) => setSelectedId(e.target.value)}>
          {curricula.map((c, index) => <option key={c.id} value={c.id}>{index + 1}. {c.code} — {c.name}{c.revision ? ` · ${c.revision}` : ""}{c.cohort ? ` · ${c.cohort}` : ""}</option>)}
        </select>
      </label>}
      <More available={query.hasNextPage} busy={query.isFetchingNextPage} load={() => void query.fetchNextPage()} label="Tải thêm chương trình" />
      <Button variant="outline" disabled={query.isFetching} onClick={() => void query.refetch()}><RefreshCw className="size-4" aria-hidden="true" />Đọc lại danh sách</Button>
    </div>
    {!selected ? <p className={panel}>Chưa có chương trình được lưu trong AMS. Bạn vẫn có thể xem tab Danh mục môn.</p>
      : <CurriculumDetail key={selected.id} userId={userId} curriculumId={selected.id} />}
  </div>;
}

function CurriculumDetail({ userId, curriculumId }: { userId: string; curriculumId: string }) {
  const query = useInfiniteQuery({ ...queryOptions, queryKey: ["curriculum", userId, curriculumId], initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam, signal }) => api.getCurriculum(curriculumId, pageParam, signal), getNextPageParam: (last) => last.groups.nextCursor ?? undefined });
  if (query.isPending) return <Loading>Đang tải chi tiết chương trình…</Loading>;
  if (query.isError) return <ReadError error={query.error} retry={() => void query.refetch()} />;
  const c = query.data.pages[0].curriculum;
  const groups = query.data.pages.flatMap((p) => p.groups.items);
  return <div className="space-y-6">
    <section className={panel} aria-labelledby="curriculum-name">
      <p className="break-words text-sm text-primary">{c.code}</p><h2 id="curriculum-name" className="mt-1 break-words text-xl font-semibold">{c.name}</h2>
      <dl className="mt-4 flex flex-wrap gap-x-8 gap-y-3 text-sm">
        <div><dt className="text-muted">Khóa</dt><dd>{c.cohort ?? "Chưa xác định"}</dd></div>
        <div><dt className="text-muted">Phiên bản</dt><dd>{c.revision ?? "Chưa xác định"}</dd></div>
      </dl>
    </section>
    <dl className="grid gap-3 sm:grid-cols-3">
      {[ ["Môn có liên kết đã lưu", c.courseCount], ["Nhóm môn đã lưu", c.groupCount], ["Tín chỉ quy định", c.minimumCredits] ].map(([label, value]) =>
        <div key={label} className={panel}><dt className="text-sm text-muted">{label}</dt><dd className="mt-2 text-2xl font-semibold tabular-nums">{value}</dd></div>)}
    </dl>
    <section className="space-y-3" aria-labelledby="groups-heading">
      <div className="flex flex-wrap items-center justify-between gap-3"><h2 id="groups-heading" className="text-lg font-semibold">Nhóm môn</h2>
        <Button variant="outline" disabled={query.isFetching} onClick={() => void query.refetch()}>Đọc lại thông tin chương trình</Button></div>
      {!groups.length && <p className="text-sm text-muted">Chưa có nhóm môn được lưu.</p>}
      <ul className="grid gap-3 md:grid-cols-2">{groups.map((g) => <li key={g.id} className={`${panel} min-w-0 space-y-3`}>
        <div className="flex flex-wrap items-center gap-2"><span className="break-all text-xs text-muted">{g.code}</span><Requirement value={g.requirement} /></div>
        <h3 className="break-words font-medium">{g.name}</h3>
        <dl className="space-y-1 text-sm"><div><dt className="inline text-muted">Tín chỉ tối thiểu: </dt><dd className="inline">{g.minimumCredits ?? "Chưa xác định"}</dd></div>
          <div><dt className="inline text-muted">Số môn tối thiểu: </dt><dd className="inline">{g.minimumCourseCount ?? "Chưa xác định"}</dd></div></dl>
      </li>)}</ul>
      <More available={query.hasNextPage} busy={query.isFetchingNextPage} load={() => void query.fetchNextPage()} label="Tải thêm nhóm" />
    </section>
    <CourseList userId={userId} curriculumId={curriculumId} />
  </div>;
}

function CourseList({ userId, curriculumId }: { userId: string; curriculumId?: string }) {
  const [input, setInput] = useState("");
  const [search, setSearch] = useState("");
  useEffect(() => { const timer = setTimeout(() => setSearch(input.trim()), 300); return () => clearTimeout(timer); }, [input]);
  const query = useInfiniteQuery({ ...queryOptions, queryKey: ["curriculum-courses", userId, curriculumId ?? "catalog", search],
    initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam, signal }): Promise<api.Page<api.CurriculumCourse | api.CatalogCourse>> => curriculumId
      ? api.getCurriculumCourses(curriculumId, search, pageParam, signal) : api.getCatalogCourses(search, pageParam, signal),
    getNextPageParam: (last) => last.nextCursor ?? undefined });
  const courses = query.data?.pages.flatMap((p) => p.items) ?? [];
  const searching = input.trim() !== search;
  return <section className={`${panel} space-y-4`} aria-labelledby="courses-heading">
    <h2 id="courses-heading" className="text-lg font-semibold">{curriculumId ? "Môn trong chương trình đã lưu" : "Danh mục môn đã lưu"}</h2>
    <div className="flex flex-wrap items-end gap-3">
      <label className="flex min-w-0 flex-1 basis-full flex-col gap-2 text-sm sm:basis-0" htmlFor="course-search">Tìm theo mã hoặc tên môn
        <span className="relative"><Search className="absolute left-3 top-3 size-4 text-muted" aria-hidden="true" /><input id="course-search" type="search" maxLength={100} value={input} onChange={(e) => setInput(e.target.value)} className={`${field} w-full pl-9`} placeholder="Nhập mã hoặc tên môn" /></span>
      </label>
      {input && <Button variant="outline" onClick={() => { setInput(""); setSearch(""); }}>Xóa tìm kiếm</Button>}
      <Button variant="outline" disabled={query.isFetching} onClick={() => void query.refetch()}>Đọc lại môn</Button>
    </div>
    {(query.isPending || searching) ? <Loading>Đang tìm môn học…</Loading>
      : query.isError ? <ReadError error={query.error} retry={() => void query.refetch()} />
      : <>
        <p role="status" className="text-sm text-muted">Đã hiển thị {courses.length} môn{query.hasNextPage ? "; còn trang tiếp theo" : ""}.</p>
        {!courses.length && <p>{search ? "Không tìm thấy môn phù hợp. Thử từ khóa khác hoặc xóa tìm kiếm." : "Chưa có môn được lưu trong danh sách này."}</p>}
        <ul className="divide-y">{courses.map((c) => <li key={c.id} className="grid min-w-0 gap-3 py-4 sm:grid-cols-[1fr_auto]">
          <div className="min-w-0 space-y-2"><p className="break-all text-xs font-medium text-primary">{c.code}</p><h3 className="break-words font-medium">{c.name}</h3>
            {"requirement" in c ? <div className="flex flex-wrap items-center gap-2 text-sm"><Requirement value={c.requirement} /><span className="break-words text-muted">{c.groupName ?? "Chưa có nhóm được lưu"}</span>
              <span className="text-muted">Kỳ kế hoạch: {c.recommendedTerm ?? "Chưa xác định"}</span></div>
              : <p className="text-sm text-muted">{c.curriculumLinked ? "Có liên kết chương trình đã lưu" : "Chưa có liên kết đã lưu"}</p>}
          </div><p className="text-sm tabular-nums"><span className="font-semibold">{c.credits}</span> tín chỉ</p>
        </li>)}</ul>
        <More available={query.hasNextPage} busy={query.isFetchingNextPage} load={() => void query.fetchNextPage()} label="Tải thêm môn" />
      </>}
  </section>;
}
