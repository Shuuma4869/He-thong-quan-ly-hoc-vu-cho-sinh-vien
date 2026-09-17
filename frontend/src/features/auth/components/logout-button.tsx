"use client";

import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { logout } from "../api";
import { currentUserKey } from "./auth-boundary";

export function LogoutButton() {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const queryClient = useQueryClient();
  const router = useRouter();
  async function signOut() {
    setBusy(true);
    setError("");
    try {
      await logout();
      await queryClient.cancelQueries();
      queryClient.clear();
      queryClient.setQueryData(currentUserKey, null);
      router.replace("/login");
      router.refresh();
    } catch {
      setError("Chưa đăng xuất được. Vui lòng thử lại.");
    } finally { setBusy(false); }
  }
  return <div><Button variant="outline" size="sm" disabled={busy} onClick={() => void signOut()}>{busy ? "Đang đăng xuất…" : "Đăng xuất"}</Button>{error && <p role="alert" className="max-w-48 text-xs text-red-600">{error}</p>}</div>;
}
