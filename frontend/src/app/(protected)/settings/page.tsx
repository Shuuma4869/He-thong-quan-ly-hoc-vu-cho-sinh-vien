import { SettingsForm } from "@/features/auth/components/settings-form";
import { requireCurrentUser } from "@/features/auth/server";

export default async function SettingsPage({ searchParams }: { searchParams: Promise<{ google?: string }> }) {
  const user = await requireCurrentUser();
  const { google } = await searchParams;
  return <SettingsForm user={user} googleResult={google} />;
}
