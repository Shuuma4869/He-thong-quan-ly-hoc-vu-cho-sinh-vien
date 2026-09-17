import type { ReactNode } from "react";
import { AppShell } from "@/components/layout/app-shell";
import { AuthBoundary } from "@/features/auth/components/auth-boundary";
import { requireCurrentUser } from "@/features/auth/server";

export default async function ProtectedLayout({ children }: { children: ReactNode }) {
  const user = await requireCurrentUser();
  return <AuthBoundary initialUser={user}><AppShell>{children}</AppShell></AuthBoundary>;
}
