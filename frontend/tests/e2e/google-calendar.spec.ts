import { expect, test } from "@playwright/test";
import { createAccountAndLogin } from "./auth-helpers";

test("Settings kết nối và ngắt Google Calendar qua ranh giới giả lập", async ({ page }) => {
  await createAccountAndLogin(page);
  let connected = false;
  await page.route("**/api/me/connections/google-calendar", async (route) => {
    if (route.request().method() !== "GET") return route.fallback();
    await route.fulfill({ json: { available: true, status: connected ? "CONNECTED" : "DISCONNECTED",
      calendarReady: connected, connectedAt: null, lastSuccessfulAccessAt: null } });
  });
  await page.route("**/api/me/connections/google-calendar/authorize", async (route) => {
    await route.fulfill({ json: { authorizationUrl:
      "https://accounts.google.com/o/oauth2/v2/auth?state=synthetic-e2e-state" } });
  });
  await page.route("https://accounts.google.com/**", async (route) => {
    await route.fulfill({ contentType: "text/html; charset=utf-8", body:
      `<a href="http://localhost:3000/settings?google=connected">Cho phép kết nối giả lập</a>` });
  });
  await page.route("**/api/me/connections/google-calendar/disconnect", async (route) => {
    connected = false;
    await route.fulfill({ json: { remoteRevocationConfirmed: true } });
  });

  await page.goto("/settings");
  await expect(page.getByText("Chưa kết nối", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Kết nối", exact: true }).click();
  await expect(page).toHaveURL(/accounts\.google\.com\/o\/oauth2\/v2\/auth/);
  connected = true;
  await page.getByRole("link", { name: "Cho phép kết nối giả lập" }).click();
  await expect(page).toHaveURL(/\/settings\?google=connected/);
  await expect(page.getByText("Đã kết nối", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Ngắt kết nối" }).click();
  await expect(page.getByText("Chưa kết nối", { exact: true })).toBeVisible();
});
