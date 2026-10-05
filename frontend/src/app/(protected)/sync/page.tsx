import { requireCurrentUser } from "@/features/auth/server";
import { SyncCenter } from "@/features/sync/sync-center";

export default async function SyncPage() {
  const user = await requireCurrentUser();
  return <SyncCenter userId={user.id} />;
}
