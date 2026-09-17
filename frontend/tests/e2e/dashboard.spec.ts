import { expect, test } from "@playwright/test";

test("hiển thị dashboard AMS", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Chào mừng đến với AMS" })).toBeVisible();
  await expect(page.getByText("Chưa kết nối Phenikaa")).toBeVisible();
});

test("chuyển giao diện sáng, tối và hệ thống", async ({ page }) => {
  await page.goto("/");
  const theme = page.getByLabel("Chọn giao diện");
  await theme.selectOption("dark");
  await expect(page.locator("html")).toHaveClass(/dark/);
  await theme.selectOption("light");
  await expect(page.locator("html")).toHaveClass(/light/);
  await page.emulateMedia({ colorScheme: "dark" });
  await theme.selectOption("system");
  await expect(page.locator("html")).toHaveClass(/dark/);
});

test("giao diện di động không tràn ngang", async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.goto("/");
  await expect(page.getByRole("navigation", { name: "Điều hướng di động" })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});

test("hiển thị trạng thái khi máy chủ không phản hồi", async ({ page }) => {
  await page.route("**/api/health", (route) => route.abort());
  await page.goto("/");
  await expect(page.getByRole("status")).toHaveText("Chưa kết nối được máy chủ AMS", { timeout: 15000 });
});
