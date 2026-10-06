import { expect, test } from "@playwright/test";
import { createAccountAndLogin } from "./auth-helpers";

test("xác minh email và bật cảnh báo đồng bộ với provider giả", async ({ page }) => {
  await createAccountAndLogin(page);
  const status = { featureEnabled: true, configured: true, notificationEmail: null as string | null,
    verified: false, verifiedAt: null as string | null, syncAlertsEnabled: false };
  await page.route("**/api/me/notifications/email", (route) => route.fulfill({ json: status }));
  await page.route("**/api/me/notifications/email/verification", (route) => route.fulfill({ status: 202 }));
  await page.route("**/api/me/notifications/email/verification/confirm", async (route) => {
    expect(route.request().postDataJSON()).toEqual({ code: "23456789AB" });
    status.verified = true; status.verifiedAt = "2026-10-06T00:00:00Z";
    await route.fulfill({ json: status });
  });
  await page.route("**/api/me/settings", async (route) => {
    const body = route.request().postDataJSON();
    if (status.notificationEmail && body.notificationEmail !== status.notificationEmail) {
      expect(body.syncEmailAlertsEnabled).toBe(true);
      status.notificationEmail = body.notificationEmail;
      status.verified = false; status.verifiedAt = null; status.syncAlertsEnabled = false;
      await route.fulfill({ json: { ...body, notificationEmailVerifiedAt: null, syncEmailAlertsEnabled: false } });
    } else if (body.syncEmailAlertsEnabled) {
      status.syncAlertsEnabled = true;
      await route.fulfill({ json: { ...body, notificationEmailVerifiedAt: status.verifiedAt } });
    } else {
      const response = await route.fetch();
      const saved = await response.json();
      status.notificationEmail = saved.notificationEmail;
      await route.fulfill({ response });
    }
  });

  await page.goto("/settings");
  await page.getByLabel("Email nhận thông báo").fill("notify@example.test");
  await page.getByRole("button", { name: "Lưu cài đặt" }).click();
  await expect(page.getByRole("status").filter({ hasText: "Chưa xác minh." })).toBeVisible();
  await expect(page.getByRole("checkbox", { name: "Nhận email khi đồng bộ cần chú ý" })).toBeDisabled();
  await page.getByRole("button", { name: "Gửi mã xác minh" }).click();
  await page.getByLabel("Mã xác minh").fill("23456789AB");
  await page.getByRole("button", { name: "Xác minh", exact: true }).click();
  await expect(page.getByRole("status").filter({ hasText: "Đã xác minh." })).toBeVisible();
  await page.getByRole("checkbox", { name: "Nhận email khi đồng bộ cần chú ý" }).click();
  await expect(page.getByText("Đã bật cảnh báo đồng bộ.")).toBeVisible();
  await page.getByLabel("Email nhận thông báo").fill("changed@example.test");
  await page.getByRole("button", { name: "Lưu cài đặt" }).click();
  await expect(page.getByRole("status").filter({ hasText: "Đã lưu cài đặt." })).toBeVisible();
  await expect(page.getByRole("status").filter({ hasText: "Chưa xác minh." })).toBeVisible();
  await expect(page.getByText("Email nhận thông báo: changed@example.test")).toBeVisible();
  await expect(page.getByRole("checkbox", { name: "Nhận email khi đồng bộ cần chú ý" })).toBeDisabled();
  await expect(page.getByRole("checkbox", { name: "Nhận email khi đồng bộ cần chú ý" })).not.toBeChecked();
  await expect(page.getByRole("button", { name: "Gửi mã xác minh" })).toBeVisible();
  for (const width of [320, 375]) {
    await page.setViewportSize({ width, height: 800 });
    const card = page.getByRole("region", { name: "Thông báo email" });
    await expect(card).toBeVisible();
    expect(await card.evaluate((node) => node.getBoundingClientRect().right)).toBeLessThanOrEqual(width);
  }
});
