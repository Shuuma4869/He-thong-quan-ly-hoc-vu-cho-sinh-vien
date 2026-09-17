import { DashboardOverview } from "@/features/dashboard/components/dashboard-overview";
import { requireCurrentUser } from "@/features/auth/server";

export default async function Home() {
  await requireCurrentUser();
  return <DashboardOverview />;
}
