import { BookOpenCheck, CalendarClock, GraduationCap, Trophy } from "lucide-react";
import { MetricCard } from "./metric-card";
import { ApiStatus } from "./api-status";

const metrics = [
  { label: "GPA tích lũy", value: "—", note: "Chờ dữ liệu đồng bộ", icon: Trophy },
  { label: "Tín chỉ hoàn thành", value: "— / —", note: "Chưa kết nối Phenikaa", icon: GraduationCap },
  { label: "Môn bắt buộc còn thiếu", value: "—", note: "Sẽ tính từ chương trình đào tạo", icon: BookOpenCheck },
  { label: "Kỳ thi sắp tới", value: "—", note: "Chờ dữ liệu lịch thi", icon: CalendarClock },
];

const integrations = [
  { name: "Phenikaa", description: "Nguồn dữ liệu học vụ", status: "Chưa kết nối" },
  { name: "Google Calendar", description: "Lịch học và lịch thi", status: "Chưa kết nối" },
  { name: "Email", description: "Thông báo thay đổi", status: "Chưa cấu hình" },
];

export function DashboardOverview() {
  return (
    <div className="space-y-8">
      <section className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
        <div><p className="text-sm font-medium text-primary">Tổng quan học vụ</p><h1 className="mt-1 text-3xl font-semibold tracking-tight">Chào mừng đến với AMS</h1><p className="mt-2 max-w-2xl text-sm leading-6 text-muted">Theo dõi kết quả học tập, lịch học và tiến độ chương trình tại một nơi.</p><p className="mt-1 text-xs text-muted">Bản khởi đầu · Các tích hợp học vụ chưa khả dụng.</p></div>
        <span className="w-fit rounded-full border bg-card px-3 py-1.5 text-xs text-muted">Lần đồng bộ cuối: chưa có</span>
      </section>
      <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4" aria-label="Chỉ số học vụ">{metrics.map((metric) => <MetricCard key={metric.label} {...metric} />)}</section>
      <section className="grid gap-6 xl:grid-cols-[1.5fr_1fr]">
        <article className="rounded-2xl border bg-card p-6 shadow-sm">
          <div className="flex items-center justify-between"><div><h2 className="font-semibold">Lịch gần nhất</h2><p className="mt-1 text-sm text-muted">Lịch học và lịch thi sau khi đồng bộ</p></div><span className="rounded-lg bg-primary-soft px-2.5 py-1 text-xs font-medium text-primary">Tuần này</span></div>
          <div className="mt-6 grid min-h-48 place-items-center rounded-xl border border-dashed bg-background/60 px-6 text-center"><div><CalendarClock className="mx-auto size-8 text-muted" /><p className="mt-3 text-sm font-medium">Chưa có sự kiện</p><p className="mt-1 text-xs text-muted">Lịch sẽ xuất hiện khi kết nối nguồn học vụ.</p></div></div>
        </article>
        <article className="rounded-2xl border bg-card p-6 shadow-sm">
          <h2 className="font-semibold">Trạng thái kết nối</h2><p className="mt-1 text-sm text-muted">Tích hợp bên ngoài của tài khoản</p>
          <div className="mt-5 space-y-3">{integrations.map((integration) => <div key={integration.name} className="flex items-center justify-between gap-4 rounded-xl border p-3.5"><div><p className="text-sm font-medium">{integration.name}</p><p className="mt-0.5 text-xs text-muted">{integration.description}</p></div><span className="whitespace-nowrap rounded-full bg-amber-500/10 px-2.5 py-1 text-[11px] font-medium text-amber-600 dark:text-amber-400">{integration.status}</span></div>)}</div>
        </article>
      </section>
      <ApiStatus />
    </div>
  );
}
