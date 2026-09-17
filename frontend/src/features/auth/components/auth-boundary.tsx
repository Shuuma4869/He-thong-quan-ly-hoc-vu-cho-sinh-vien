"use client";

import { useEffect, useLayoutEffect, useRef, type ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useTheme } from "next-themes";
import { Button } from "@/components/ui/button";
import { getCurrentUser } from "../api";
import type { CurrentUser } from "../schema";

export const currentUserKey = ["current-user"] as const;

export function useCurrentUser(initialUser?: CurrentUser) {
  return useQuery({ queryKey: currentUserKey, queryFn: ({ signal }) => getCurrentUser(signal),
    initialData: initialUser, staleTime: 0, retry: false, refetchOnWindowFocus: true, refetchInterval: 60_000 });
}

export function AuthBoundary({ initialUser, children }: { initialUser: CurrentUser; children: ReactNode }) {
  const user = useCurrentUser(initialUser);
  const router = useRouter();
  const { setTheme } = useTheme();
  const userId = user.data?.id;
  const theme = user.data?.settings.theme;
  const appliedPreference = useRef<string | null>(null);
  useEffect(() => {
    if (user.data === null) { router.replace("/login"); router.refresh(); }
  }, [user.data, router]);
  useLayoutEffect(() => {
    const preference = `${userId}:${theme}`;
    if (theme && appliedPreference.current !== preference) {
      appliedPreference.current = preference;
      setTheme(theme.toLowerCase());
    }
  }, [userId, theme, setTheme]);
  if (user.isError) return <div className="mx-auto max-w-lg space-y-4 p-8"><p role="alert">Không thể kiểm tra phiên đăng nhập. Vui lòng kiểm tra kết nối.</p><Button onClick={() => void user.refetch()}>Thử lại</Button></div>;
  if (!user.data) return <p className="p-8" role="status">Đang kiểm tra phiên đăng nhập…</p>;
  return children;
}
