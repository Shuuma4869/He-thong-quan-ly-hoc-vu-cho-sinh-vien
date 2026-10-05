import { expect, test } from "@playwright/test";
import { createAccountAndLogin } from "./auth-helpers";

test.beforeEach(async ({ page }) => { await createAccountAndLogin(page); });

test("hiển thị dashboard AMS", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Chào mừng đến với AMS" })).toBeVisible();
  await expect(page.getByText("Không khả dụng: tích hợp Phenikaa chưa được bật.")).toBeVisible();
  await expect(page.getByText("Chưa có chương trình được lưu.")).toBeVisible();
  await expect(page.getByRole("link", { name: "Mở Đồng bộ" })).toBeVisible();
  for (const placeholder of ["GPA tích lũy", "Tín chỉ hoàn thành", "Môn bắt buộc còn thiếu", "Kỳ thi sắp tới", "Chưa có sự kiện"])
    await expect(page.getByText(placeholder)).toHaveCount(0);
});

test("một API lỗi không che các trạng thái còn lại", async ({ page }) => {
  await page.route("**/api/me/connections/google-calendar", (route) => route.abort());
  await page.goto("/");
  await expect(page.getByText("Chưa thể kiểm tra Google Calendar.")).toBeVisible();
  await expect(page.getByText("Chưa có chương trình được lưu.")).toBeVisible();
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
