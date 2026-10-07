import { requireCurrentUser } from "@/features/auth/server";
import { StudyPlanner } from "@/features/study-plan/study-planner";

export default async function PlannerPage() {
  const user = await requireCurrentUser();
  return <StudyPlanner userId={user.id} />;
}
