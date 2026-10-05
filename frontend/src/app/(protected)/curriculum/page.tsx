import { requireCurrentUser } from "@/features/auth/server";
import { CurriculumBrowser } from "@/features/curriculum/curriculum-browser";

export default async function CurriculumPage() {
  const user = await requireCurrentUser();
  return <CurriculumBrowser userId={user.id} />;
}
