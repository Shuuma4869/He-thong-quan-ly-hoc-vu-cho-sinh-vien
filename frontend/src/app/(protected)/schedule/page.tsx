import { requireCurrentUser } from "@/features/auth/server";
import { ScheduleView } from "@/features/academic-source/schedule-view";

export default async function SchedulePage() {
  const user = await requireCurrentUser();
  return <ScheduleView userId={user.id} />;
}
