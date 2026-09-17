"use client";

import { useQuery } from "@tanstack/react-query";
import { RefreshCw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { getApiHealth } from "@/services/health";

export function ApiStatus() {
  const health = useQuery({
    queryKey: ["api", "health"],
    queryFn: ({ signal }) => getApiHealth(signal),
    refetchInterval: 60_000,
  });

  return (
    <div className="flex items-center justify-between gap-3 rounded-xl border bg-card px-4 py-3">
      <p role="status" className="flex items-center gap-2 text-xs text-muted">
        <span aria-hidden="true" className={`size-2 shrink-0 rounded-full ${health.isError ? "bg-amber-500" : health.isSuccess ? "bg-emerald-500" : "bg-muted"}`} />
        {health.isPending ? "Đang kiểm tra máy chủ…" : health.isError ? "Chưa kết nối được máy chủ AMS" : "Đã kết nối máy chủ AMS"}
      </p>
      <Button variant="ghost" size="sm" disabled={health.isFetching} onClick={() => void health.refetch()} aria-label="Kiểm tra lại kết nối">
        <RefreshCw className={health.isFetching ? "animate-spin" : ""} />
        <span className="hidden sm:inline">Kiểm tra lại</span>
      </Button>
    </div>
  );
}
