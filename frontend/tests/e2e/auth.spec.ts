import { expect, test } from "@playwright/test";
import { createAccountAndLogin, fixtureEmail, fixturePassword } from "./auth-helpers";

test("chặn dashboard và settings khi chưa đăng nhập", async ({ page }) => {
  for (const path of ["/", "/settings"]) {
    await page.goto(path);
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByRole("heading", { name: "Đăng nhập AMS" })).toBeVisible();
  }
  expect((await page.request.get("/api/me")).status()).toBe(401);
});

test("đăng ký, đăng nhập, lưu settings, giữ session khi tải lại và đăng xuất", async ({ page, context }) => {
  const email = fixtureEmail();
  await page.goto("/register");
  await page.getByLabel("Tên hiển thị").fill("Sinh viên kiểm thử");
  await page.getByLabel("Email", { exact: true }).fill(email);
  await page.getByLabel("Mật khẩu", { exact: true }).fill(fixturePassword);
  await page.getByRole("button", { name: "Đăng ký", exact: true }).click();
  await expect(page.getByRole("status")).toHaveText("Đã tạo tài khoản. Bạn có thể đăng nhập ngay.");
  await page.getByRole("link", { name: "Đến trang đăng nhập" }).click();
  await page.getByLabel("Email", { exact: true }).fill(email);
  await page.getByLabel("Mật khẩu", { exact: true }).fill(fixturePassword);
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Chào mừng đến với AMS" })).toBeVisible();
  await page.reload();
  await expect(page.getByRole("heading", { name: "Chào mừng đến với AMS" })).toBeVisible();
  const cookies = await context.cookies();
  expect(cookies.find((cookie) => cookie.name === "AMS_SESSION")).toMatchObject({ httpOnly: true, sameSite: "Lax" });
  expect(await page.evaluate(() => document.cookie)).not.toContain("AMS_SESSION");
  expect(await page.evaluate(() => JSON.stringify(localStorage))).not.toContain(fixturePassword);
  const me = await (await page.request.get("/api/me")).json();
  expect(me.email).toBe(email);
  expect(me.role).toBe("STUDENT");
  expect(me).not.toHaveProperty("passwordHash");
  await page.goto("/settings");
  await page.getByLabel("Email nhận thông báo").fill("notifications@example.test");
  await page.getByLabel("Múi giờ").fill("Asia/Tokyo");
  await page.getByLabel("Giao diện mặc định").selectOption("DARK");
  await page.getByRole("button", { name: "Lưu cài đặt" }).click();
  await expect(page.getByRole("status")).toHaveText("Đã lưu cài đặt.");
  await page.reload();
  await expect(page.getByLabel("Múi giờ")).toHaveValue("Asia/Tokyo");
  await expect(page.locator("html")).toHaveClass(/dark/);
  await page.getByRole("button", { name: "Đăng xuất", exact: true }).click();
  await expect(page).toHaveURL(/\/login$/);
  expect((await page.request.get("/api/me")).status()).toBe(401);
  await page.goto("/settings");
  await expect(page).toHaveURL(/\/login$/);
});

test("đăng nhập sai không mở dashboard và không giữ mật khẩu trong form", async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Email", { exact: true }).fill(fixtureEmail());
  await page.getByLabel("Mật khẩu", { exact: true }).fill(fixturePassword);
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await expect(page.getByRole("alert").filter({ hasText: "Email hoặc mật khẩu không đúng." })).toBeVisible();
  await expect(page.getByLabel("Mật khẩu", { exact: true })).toHaveValue("");
  expect((await page.request.get("/api/me")).status()).toBe(401);
});

test("CSRF vẫn chặn đăng xuất nếu không gửi token", async ({ page }) => {
  await createAccountAndLogin(page);
  expect((await page.request.post("/api/auth/logout")).status()).toBe(403);
  expect((await page.request.get("/api/me")).status()).toBe(200);
});
