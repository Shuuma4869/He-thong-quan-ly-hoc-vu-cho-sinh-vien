"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { disconnectGoogle, getGoogleConnection, openGoogleAuthorization, retryGoogleSetup, startGoogleAuthorization } from "./api";

const resultMessage: Record<string, string> = {
  cancelled: "Bạn đã hủy cấp quyền Google Calendar.",
  error: "Chưa thể kết nối Google Calendar. Vui lòng thử lại.",
  setup: "Đã nhận quyền truy cập nhưng chưa tạo được lịch riêng. Bạn có thể thử hoàn tất thiết lập.",
};

export function GoogleCalendarCard({ callbackResult, userId }: { callbackResult?: string; userId?: string }) {
  const connection = useQuery({ queryKey: ["google-calendar-connection", userId], queryFn: ({ signal }) => getGoogleConnection(signal), retry: false });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const current = connection.data;

  async function connect() {
    setBusy(true); setError(""); setMessage("");
    try { openGoogleAuthorization(await startGoogleAuthorization()); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "Không thể bắt đầu kết nối."); setBusy(false); }
  }

  async function setup() {
    setBusy(true); setError(""); setMessage("");
    try { await retryGoogleSetup(); await connection.refetch(); setMessage("Đã hoàn tất lịch riêng của AMS."); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "Không thể hoàn tất thiết lập."); }
    finally { setBusy(false); }
  }

  async function disconnect() {
    setBusy(true); setError(""); setMessage("");
    try { await disconnectGoogle(); await connection.refetch(); setMessage("Đã ngắt kết nối Google Calendar."); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "Không thể ngắt kết nối."); }
    finally { setBusy(false); }
  }

  const label = !current?.available ? "Chưa được cấu hình" : {
    CONNECTED: "Đã kết nối", SETUP_REQUIRED: "Cần hoàn tất thiết lập",
    RECONNECTION_REQUIRED: "Cần kết nối lại", DISCONNECTED: "Chưa kết nối",
  }[current.status];

  return <div className="space-y-4 rounded-2xl border bg-card p-6">
    <div><h2 className="text-lg font-semibold">Google Calendar</h2>
      <p className="mt-1 text-sm text-muted">AMS chỉ tạo và quản lý một lịch riêng. Lịch học và lịch thi chưa được chuyển sang Google.</p></div>
    {connection.isPending ? <p role="status" className="text-sm text-muted">Đang kiểm tra kết nối…</p>
      : connection.isError ? <div className="space-y-2"><p role="alert" className="text-sm text-red-600">Không thể kiểm tra kết nối Google Calendar.</p><Button type="button" variant="outline" onClick={() => void connection.refetch()}>Thử lại</Button></div>
      : <><p className="text-sm">Trạng thái: <span className="font-medium">{label}</span></p>
        {callbackResult === "connected" && current?.status === "CONNECTED" && <p role="status" className="text-sm text-primary">Đã kết nối lịch riêng của AMS.</p>}
        {callbackResult && resultMessage[callbackResult] && <p role="status" className="text-sm text-muted">{resultMessage[callbackResult]}</p>}
        {message && <p role="status" className="text-sm text-primary">{message}</p>}
        {error && <p role="alert" className="text-sm text-red-600">{error}</p>}
        {current?.available && <div className="flex flex-wrap gap-2">
          {(current.status === "DISCONNECTED" || current.status === "RECONNECTION_REQUIRED") &&
            <Button type="button" disabled={busy} onClick={() => void connect()}>{busy ? "Đang mở…" : current.status === "DISCONNECTED" ? "Kết nối" : "Kết nối lại"}</Button>}
          {current.status === "SETUP_REQUIRED" && <Button type="button" disabled={busy} onClick={() => void setup()}>{busy ? "Đang xử lý…" : "Hoàn tất thiết lập"}</Button>}
          {current.status !== "DISCONNECTED" && <Button type="button" variant="outline" disabled={busy} onClick={() => void disconnect()}>Ngắt kết nối</Button>}
        </div>}</>}
  </div>;
}
