import { AppShell } from "@/components/layout/app-shell";
import { DashboardOverview } from "@/features/dashboard/components/dashboard-overview";

export default function Home() {
  return (
    <AppShell>
      <DashboardOverview />
    </AppShell>
  );
}
