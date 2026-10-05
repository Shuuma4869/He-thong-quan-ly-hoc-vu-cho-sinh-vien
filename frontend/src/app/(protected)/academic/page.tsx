import { requireCurrentUser } from "@/features/auth/server";
import { AcademicRecords } from "@/features/academic-source/academic-records";

export default async function AcademicPage() {
  const user = await requireCurrentUser();
  return <AcademicRecords userId={user.id} />;
}
