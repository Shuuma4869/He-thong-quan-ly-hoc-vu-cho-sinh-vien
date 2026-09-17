import { SettingsForm } from "@/features/auth/components/settings-form";
import { requireCurrentUser } from "@/features/auth/server";

export default async function SettingsPage() {
  const user = await requireCurrentUser();
  return <SettingsForm user={user} />;
}
