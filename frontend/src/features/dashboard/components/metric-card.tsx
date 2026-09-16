import type { LucideIcon } from "lucide-react";

type MetricCardProps = { label: string; value: string; note: string; icon: LucideIcon };

export function MetricCard({ label, value, note, icon: Icon }: MetricCardProps) {
  return (
    <article className="rounded-2xl border bg-card p-5 shadow-sm">
      <div className="flex items-start justify-between">
        <div><p className="text-sm text-muted">{label}</p><p className="mt-3 text-3xl font-semibold tracking-tight">{value}</p></div>
        <div className="grid size-10 place-items-center rounded-xl bg-primary-soft text-primary"><Icon className="size-5" /></div>
      </div>
      <p className="mt-4 text-xs text-muted">{note}</p>
    </article>
  );
}
