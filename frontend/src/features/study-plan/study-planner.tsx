"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { ApiError } from "@/features/auth/api";
import { getCurriculumCourses, type CurriculumCourse } from "@/features/curriculum/api";
import { getStudyPlan, planCourse, removePlannedCourse, studyPlanKeys, type PlannedCourse } from "./api";

const panel = "min-w-0 rounded-2xl border bg-card p-5 sm:p-6";
const field = "w-full min-w-0 rounded-xl border bg-background px-3 py-2 text-sm text-foreground";

function termValue(value: string): number | null {
  if (!/^[1-9]\d?$/.test(value)) return null;
  const term = Number(value);
  return Number.isInteger(term) && term <= 99 ? term : null;
}

function errorMessage(error: Error | null): string {
  if (error instanceof ApiError) {
    if (error.status === 401 || error.status === 403) return "Phiên đăng nhập không còn hợp lệ. Vui lòng đăng nhập lại.";
    if (error.status === 404) return "Môn không còn trong chương trình đang theo dõi. Hãy tải lại kế hoạch.";
    if (error.status === 409) return "Kế hoạch hoặc chương trình theo dõi đã thay đổi. Hãy tải lại.";
    if (error.status === 400) return "Kỳ kế hoạch phải là số nguyên từ 1 đến 99.";
  }
  return "Chưa thể cập nhật kế hoạch. Vui lòng thử lại.";
}

function CourseChoice({ course, currentTerm, busy, onPlan }: {
  course: CurriculumCourse; currentTerm: number | undefined; busy: boolean;
  onPlan: (courseId: string, term: number) => void;
}) {
  const [term, setTerm] = useState(String(currentTerm ?? 1));
  const value = termValue(term);
  return <li className="min-w-0 space-y-3 border-t py-4 first:border-t-0">
    <div className="min-w-0"><p className="break-all text-sm font-medium text-primary">{course.code}</p>
      <p className="break-words font-medium">{course.name}</p>
      <p className="text-sm text-muted">{course.credits} tín chỉ · {course.requirement === "REQUIRED" ? "Bắt buộc" : "Tự chọn"}</p>
      {currentTerm && <p className="text-sm">Đang ở Kỳ kế hoạch {currentTerm}</p>}</div>
    <div className="flex flex-wrap items-end gap-2">
      <label className="w-32 max-w-full text-sm">Kỳ kế hoạch cho {course.code}
        <input className={field} type="number" min={1} max={99} step={1} value={term}
          onChange={(event) => setTerm(event.target.value)} /></label>
      <Button disabled={busy || value === null || value === currentTerm} onClick={() => value && onPlan(course.courseId, value)}>
        {currentTerm ? `Chuyển ${course.code} sang kỳ khác` : `Xếp ${course.code} vào kế hoạch`}
      </Button>
    </div>
    {value === null && <p role="alert" className="text-sm text-destructive">Kỳ kế hoạch phải là số nguyên từ 1 đến 99.</p>}
  </li>;
}

function CoursePicker({ userId, curriculumId, planned, busy, onPlan }: {
  userId: string; curriculumId: string; planned: Map<string, number>; busy: boolean;
  onPlan: (courseId: string, term: number) => void;
}) {
  const [input, setInput] = useState("");
  const [search, setSearch] = useState("");
  useEffect(() => { const timer = setTimeout(() => setSearch(input.trim()), 300); return () => clearTimeout(timer); }, [input]);
  const query = useInfiniteQuery({
    queryKey: ["study-plan-courses", userId, curriculumId, search],
    initialPageParam: undefined as string | undefined, retry: false, refetchOnWindowFocus: false,
    queryFn: ({ pageParam, signal }) => getCurriculumCourses(curriculumId, search, pageParam, signal),
    getNextPageParam: (last) => last.nextCursor ?? undefined,
  });
  const courses = query.data?.pages.flatMap((page) => page.items) ?? [];
  return <section className={`${panel} space-y-4`} aria-labelledby="course-picker-title">
    <h2 id="course-picker-title" className="text-lg font-semibold">Thêm môn vào kế hoạch</h2>
    <label className="block max-w-lg text-sm">Tìm theo mã hoặc tên môn
      <input className={field} type="search" maxLength={100} value={input} onChange={(event) => setInput(event.target.value)} />
    </label>
    {input && <Button variant="outline" onClick={() => { setInput(""); setSearch(""); }}>Xóa tìm kiếm</Button>}
    {query.isPending || input.trim() !== search ? <p role="status">Đang tìm môn trong chương trình…</p>
      : query.isError ? <p role="alert">Chưa thể đọc môn trong chương trình. <Button variant="outline" onClick={() => void query.refetch()}>Thử lại</Button></p>
      : <><p role="status" className="text-sm text-muted">Đã hiển thị {courses.length} môn{query.hasNextPage ? "; còn trang tiếp theo" : ""}.</p>
        {!courses.length && <p>Không tìm thấy môn phù hợp trong chương trình theo dõi.</p>}
        <ul>{courses.map((course) => <CourseChoice key={`${course.courseId}:${planned.get(course.courseId) ?? "new"}`}
          course={course} currentTerm={planned.get(course.courseId)} busy={busy} onPlan={onPlan} />)}</ul>
        {query.hasNextPage && <Button variant="outline" disabled={query.isFetchingNextPage}
          onClick={() => void query.fetchNextPage()}>Tải thêm môn</Button>}</>}
  </section>;
}

function PlannedCourseRow({ course, term, busy, onMove, onRemove }: {
  course: PlannedCourse; term: number; busy: boolean;
  onMove: (courseId: string, term: number) => void; onRemove: (courseId: string) => void;
}) {
  const [nextTerm, setNextTerm] = useState(String(term));
  const value = termValue(nextTerm);
  return <li className="min-w-0 space-y-3 border-t py-4 first:border-t-0">
    <div className="min-w-0"><p className="break-all text-sm font-medium text-primary">{course.code}</p>
      <p className="break-words font-medium">{course.name}</p>
      <p className="text-sm text-muted">{course.credits} tín chỉ · {course.requirement === "REQUIRED" ? "Bắt buộc" : "Tự chọn"}</p></div>
    <div className="flex flex-wrap items-end gap-2">
      <label className="w-32 max-w-full text-sm">Kỳ mới cho {course.code}
        <input className={field} type="number" min={1} max={99} step={1} value={nextTerm}
          onChange={(event) => setNextTerm(event.target.value)} /></label>
      <Button variant="outline" disabled={busy || value === null || value === term}
        onClick={() => value && onMove(course.courseId, value)}>Chuyển kỳ {course.code}</Button>
      <Button variant="outline" disabled={busy} onClick={() => onRemove(course.courseId)}>Bỏ {course.code} khỏi kế hoạch</Button>
    </div>
    {value === null && <p role="alert" className="text-sm text-destructive">Kỳ kế hoạch phải là số nguyên từ 1 đến 99.</p>}
  </li>;
}

export function StudyPlanner({ userId }: { userId: string }) {
  const client = useQueryClient();
  const queryKey = studyPlanKeys.current(userId);
  const plan = useQuery({ queryKey, queryFn: ({ signal }) => getStudyPlan(signal),
    staleTime: 0, retry: false, refetchOnWindowFocus: true, refetchOnMount: "always" });
  const assign = useMutation({ mutationFn: ({ curriculumId, courseId, term }: { curriculumId: string; courseId: string; term: number }) =>
    planCourse(curriculumId, courseId, term), onSettled: () => client.invalidateQueries({ queryKey }) });
  const remove = useMutation({ mutationFn: ({ curriculumId, courseId }: { curriculumId: string; courseId: string }) =>
    removePlannedCourse(curriculumId, courseId), onSettled: () => client.invalidateQueries({ queryKey }) });
  const busy = assign.isPending || remove.isPending;
  const current = !plan.isFetching && plan.isSuccess ? plan.data : null;
  const curriculum = current?.curriculum;
  const planned = new Map(current?.terms.flatMap((term) => term.courses.map((course) => [course.courseId, term.plannedTerm] as const)) ?? []);
  return <div className="space-y-6">
    <header className="space-y-2"><p className="text-sm font-medium text-primary">Học vụ / Kế hoạch cá nhân</p>
      <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">Kế hoạch học kỳ</h1>
      <p className="max-w-3xl text-sm leading-6 text-muted">Tự sắp xếp các môn trong chương trình theo dõi vào từng kỳ kế hoạch.</p></header>
    <p className="text-sm text-muted">Kế hoạch này chỉ được lưu trong AMS và không đăng ký học phần với nhà trường. AMS chưa kiểm tra điều kiện tiên quyết hoặc xung đột lịch cho kế hoạch này.</p>
    {plan.isPending || plan.isFetching ? <p role="status">Đang đọc kế hoạch học kỳ…</p>
      : plan.isError ? <div role="alert" className={panel}>Chưa thể đọc kế hoạch đã lưu. <Button variant="outline" onClick={() => void plan.refetch()}>Thử lại</Button></div>
      : !curriculum ? <section className={`${panel} space-y-3`}><h2 className="font-semibold">Chưa có chương trình theo dõi</h2>
        <p>Bạn cần chọn chương trình theo dõi trước khi lập kế hoạch.</p>
        <Link className="text-primary underline" href="/curriculum">Mở Chương trình</Link></section>
      : <div key={curriculum.id} className="space-y-6">
        <section className={`${panel} space-y-2`}><h2 className="font-semibold">Chương trình theo dõi trong AMS</h2>
          <p className="break-words">{curriculum.code} — {curriculum.name}</p>
          <p className="text-sm text-muted">Các số tín chỉ dưới đây chỉ là khối lượng môn bạn tự xếp, không phải tín chỉ đã học hay đã đạt.</p></section>
        {(assign.isError || remove.isError) && <p role="alert" className="text-sm text-destructive">{errorMessage(assign.error ?? remove.error)}</p>}
        <section className="space-y-3" aria-labelledby="planned-terms-title"><h2 id="planned-terms-title" className="text-lg font-semibold">Các kỳ kế hoạch</h2>
          {!current.terms.length && <div className={`${panel} space-y-2`}><p>Bạn chưa xếp môn nào vào kế hoạch.</p>
            <p className="text-sm text-muted">Chọn môn trong chương trình theo dõi và tự xếp vào kỳ dự kiến.</p></div>}
          <div className="grid gap-4 lg:grid-cols-2">{current.terms.map((term) => <article key={term.plannedTerm} className={panel}>
            <h3 className="text-lg font-semibold">Kỳ kế hoạch {term.plannedTerm}</h3>
            <p className="mt-2 text-sm">Tín chỉ dự kiến trong kỳ: <strong>{term.plannedCredits}</strong></p>
            <p className="text-sm">Số môn: {term.courseCount}</p>
            <ul className="mt-3">{term.courses.map((course) => <PlannedCourseRow key={course.courseId}
              course={course} term={term.plannedTerm} busy={busy}
              onMove={(courseId, nextTerm) => assign.mutate({ curriculumId: curriculum.id, courseId, term: nextTerm })}
              onRemove={(courseId) => remove.mutate({ curriculumId: curriculum.id, courseId })} />)}</ul>
          </article>)}</div></section>
        <CoursePicker key={curriculum.id} userId={userId} curriculumId={curriculum.id} planned={planned} busy={busy}
          onPlan={(courseId, term) => assign.mutate({ curriculumId: curriculum.id, courseId, term })} />
      </div>}
  </div>;
}
