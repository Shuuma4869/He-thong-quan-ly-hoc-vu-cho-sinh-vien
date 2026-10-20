import { expect, test } from "@playwright/test";
import { createAccountAndLogin, fixturePassword } from "./auth-helpers";

test("quản lý phiên đăng nhập giữ lại phiên hiện tại", async ({ page, browser }) => {
  const email = await createAccountAndLogin(page);
  const otherContext = await browser.newContext();
  try {
    const otherPage = await otherContext.newPage();
    await otherPage.goto("/login");
    await otherPage.getByLabel("Email", { exact: true }).fill(email);
    await otherPage.getByLabel("Mật khẩu", { exact: true }).fill(fixturePassword);
    await otherPage.getByRole("button", { name: "Đăng nhập", exact: true }).click();
    await expect(otherPage.getByRole("heading", { name: "Chào mừng đến với AMS" })).toBeVisible();

    await page.goto("/settings");
    await expect(page.getByRole("heading", { name: "Bảo mật tài khoản" })).toBeVisible();
    await expect(page.getByText("Phiên hiện tại", { exact: true })).toBeVisible();
    await expect(page.getByText("Phiên đăng nhập khác", { exact: true })).toBeVisible();
    await page.getByRole("button", { name: "Đăng xuất tất cả phiên khác" }).click();
    const confirmation = page.getByRole("dialog");
    await expect(confirmation.getByText(/Phiên hiện tại được giữ lại/)).toBeVisible();
    await confirmation.getByRole("button", { name: "Đăng xuất các phiên khác" }).click();
    await expect(page.getByRole("status").filter({ hasText: "Đã đăng xuất các phiên khác." })).toBeVisible();
    expect((await otherPage.request.get("/api/me")).status()).toBe(401);
    expect((await page.request.get("/api/me")).status()).toBe(200);
  } finally {
    await otherContext.close();
  }
});

test("đổi mật khẩu chặn phiên cũ và hiển thị tốt ở màn hình hẹp", async ({ page, browser }) => {
  const email = await createAccountAndLogin(page);
  const otherContext = await browser.newContext();
  try {
    const otherPage = await otherContext.newPage();
    await otherPage.goto("/login");
    await otherPage.getByLabel("Email", { exact: true }).fill(email);
    await otherPage.getByLabel("Mật khẩu", { exact: true }).fill(fixturePassword);
    await otherPage.getByRole("button", { name: "Đăng nhập", exact: true }).click();
    await expect(otherPage.getByRole("heading", { name: "Chào mừng đến với AMS" })).toBeVisible();

    await page.goto("/settings");
    await page.getByLabel("Mật khẩu hiện tại").fill(fixturePassword);
    await page.getByLabel("Mật khẩu mới", { exact: true }).fill("Phase18-updated-password");
    await page.getByLabel("Xác nhận mật khẩu mới").fill("Phase18-updated-password");
    await page.getByRole("button", { name: "Đổi mật khẩu" }).click();
    await expect(page.getByRole("status").filter({ hasText: "Đã đổi mật khẩu." })).toBeVisible();
    await expect(page.getByLabel("Mật khẩu hiện tại")).toHaveValue("");
    expect((await otherPage.request.get("/api/me")).status()).toBe(401);
    expect((await page.request.get("/api/me")).status()).toBe(200);

    for (const width of [1280, 375, 320]) {
      await page.setViewportSize({ width, height: 800 });
      await expect(page.getByRole("heading", { name: "Bảo mật tài khoản" })).toBeVisible();
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    }

    const freshContext = await browser.newContext();
    try {
      const freshPage = await freshContext.newPage();
      await freshPage.goto("/login");
      await freshPage.getByLabel("Email", { exact: true }).fill(email);
      await freshPage.getByLabel("Mật khẩu", { exact: true }).fill(fixturePassword);
      await freshPage.getByRole("button", { name: "Đăng nhập", exact: true }).click();
      await expect(freshPage.getByRole("alert").filter({ hasText: "Email hoặc mật khẩu không đúng." })).toBeVisible();
      await freshPage.getByLabel("Mật khẩu", { exact: true }).fill("Phase18-updated-password");
      await freshPage.getByRole("button", { name: "Đăng nhập", exact: true }).click();
      await expect(freshPage.getByRole("heading", { name: "Chào mừng đến với AMS" })).toBeVisible();
    } finally {
      await freshContext.close();
    }
  } finally {
    await otherContext.close();
  }
});
