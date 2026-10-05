import { DashboardOverview } from "@/features/dashboard/components/dashboard-overview";
import { requireCurrentUser } from "@/features/auth/server";

export default async function Home() {
  const user = await requireCurrentUser();
  return <DashboardOverview userId={user.id} />;
}
