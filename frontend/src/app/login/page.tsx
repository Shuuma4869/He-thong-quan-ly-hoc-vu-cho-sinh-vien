import { redirect } from "next/navigation";
import { AuthForm } from "@/features/auth/components/auth-form";
import { getServerUser } from "@/features/auth/server";

export default async function LoginPage() {
  if (await getServerUser()) redirect("/");
  return <AuthForm mode="login" />;
}
