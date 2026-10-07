import { expect, test } from "@playwright/test";
import { createAccountAndLogin } from "./auth-helpers";

test("Planner requires an AMS login", async ({ page }) => {
  await page.goto("/planner");
  await expect(page).toHaveURL(/\/login$/);
});

test("Planner requires a tracked curriculum without contacting the source", async ({ page }) => {
  await createAccountAndLogin(page);
  let sourceCalls = 0;
  await page.route("**/api/me/academic/source/**", (route) => { sourceCalls++; return route.abort(); });
  await page.goto("/planner");
  await expect(page.getByText("Bạn cần chọn chương trình theo dõi trước khi lập kế hoạch.")).toBeVisible();
  await expect(page.getByRole("link", { name: "Mở Chương trình" })).toHaveAttribute("href", "/curriculum");
  expect(sourceCalls).toBe(0);
});

for (const width of [1280, 375, 320]) {
  test(`Local study-plan flow at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 844 });
    await createAccountAndLogin(page);
    let sourceCalls = 0;
    await page.route("**/api/me/academic/source/**", (route) => { sourceCalls++; return route.abort(); });
    const csrf = await (await page.request.get("/api/auth/csrf")).json();
    const seeded = await page.request.post("/api/me/test-fixtures/curricula", { headers: { [csrf.headerName]: csrf.token } });
    expect(seeded.status()).toBe(204);
    await page.goto("/curriculum");
    await page.getByLabel("Chọn chương trình để xem").selectOption({ label: "1. CURR-A — Chương trình kiểm thử A" });
    await page.getByRole("button", { name: "Đặt làm chương trình theo dõi" }).click();
    await page.getByRole("link", { name: "Mở kế hoạch học kỳ" }).click();
    await expect(page).toHaveURL(/\/planner$/);
    await expect(page.getByText("Bạn chưa xếp môn nào vào kế hoạch.")).toBeVisible();
    await expect(page.getByText(/không đăng ký học phần với nhà trường/)).toBeVisible();
    await page.getByRole("button", { name: "Xếp TEST101 vào kế hoạch" }).click();
    await expect(page.getByRole("heading", { name: "Kỳ kế hoạch 1" })).toBeVisible();
    await page.getByRole("button", { name: "Xếp TEST102 vào kế hoạch" }).click();
    const first = page.getByRole("article").filter({ has: page.getByRole("heading", { name: "Kỳ kế hoạch 1" }) });
    await expect(first).toContainText("Tín chỉ dự kiến trong kỳ: 7");
    await expect(first).toContainText("Số môn: 2");
    await first.getByRole("spinbutton", { name: "Kỳ mới cho TEST102" }).fill("2");
    await first.getByRole("button", { name: "Chuyển kỳ TEST102" }).click();
    await expect(first).toContainText("Tín chỉ dự kiến trong kỳ: 3");
    const second = page.getByRole("article").filter({ has: page.getByRole("heading", { name: "Kỳ kế hoạch 2" }) });
    await expect(second).toContainText("Tín chỉ dự kiến trong kỳ: 4");
    await first.getByRole("button", { name: "Bỏ TEST101 khỏi kế hoạch" }).click();
    await expect(page.getByRole("heading", { name: "Kỳ kế hoạch 1" })).toHaveCount(0);
    await page.reload();
    await expect(page.getByRole("heading", { name: "Kỳ kế hoạch 2" })).toBeVisible();
    await expect(second).toContainText("TEST102");
    await expect(second).toContainText("Tín chỉ dự kiến trong kỳ: 4");
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    if (width < 1024) await expect(page.getByRole("navigation", { name: "Điều hướng di động" }).getByRole("link")).toHaveCount(5);
    expect(sourceCalls).toBe(0);
  });

  test(`Independent study-plan scenarios at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 844 });
    await createAccountAndLogin(page);
    let sourceCalls = 0;
    await page.route("**/api/me/academic/source/**", (route) => { sourceCalls++; return route.abort(); });
    const csrf = await (await page.request.get("/api/auth/csrf")).json();
    const seeded = await page.request.post("/api/me/test-fixtures/curricula", { headers: { [csrf.headerName]: csrf.token } });
    expect(seeded.status()).toBe(204);
    await page.goto("/curriculum");
    await page.getByLabel("Chọn chương trình để xem").selectOption({ label: "1. CURR-A — Chương trình kiểm thử A" });
    await page.getByRole("button", { name: "Đặt làm chương trình theo dõi" }).click();
    await page.goto("/planner");
    await expect(page.getByRole("combobox", { name: "Chọn phương án kế hoạch" })).toHaveValue("1");
    await page.getByRole("button", { name: "Xếp TEST101 vào kế hoạch" }).click();
    await page.getByRole("button", { name: "Xếp TEST102 vào kế hoạch" }).click();
    const first = page.getByRole("article").filter({ has: page.getByRole("heading", { name: "Kỳ kế hoạch 1" }) });
    await expect(first).toContainText("Số môn: 2");
    await first.getByRole("spinbutton", { name: "Kỳ mới cho TEST102" }).fill("2");
    await first.getByRole("button", { name: "Chuyển kỳ TEST102" }).click();
    await expect(page.getByRole("heading", { name: "Kỳ kế hoạch 2" })).toBeVisible();
    await page.getByRole("button", { name: "Sao chép phương án" }).click();
    await expect(page.getByRole("combobox", { name: "Chọn phương án kế hoạch" })).toHaveValue("2");
    const second = page.getByRole("article").filter({ has: page.getByRole("heading", { name: "Kỳ kế hoạch 2" }) });
    await second.getByRole("spinbutton", { name: "Kỳ mới cho TEST102" }).fill("3");
    await second.getByRole("button", { name: "Chuyển kỳ TEST102" }).click();
    await expect(page.getByRole("article").filter({ has: page.getByRole("heading", { name: "Kỳ kế hoạch 3" }) })).toContainText("TEST102");
    await page.getByRole("combobox", { name: "Chọn phương án kế hoạch" }).selectOption("1");
    await expect(page.getByRole("article").filter({ has: page.getByRole("heading", { name: "Kỳ kế hoạch 2" }) })).toContainText("TEST102");
    await page.getByRole("combobox", { name: "Chọn phương án kế hoạch" }).selectOption("2");
    await page.getByRole("button", { name: "Xóa các môn trong Phương án 2" }).click();
    await page.getByRole("button", { name: "Xác nhận xóa các môn trong Phương án 2" }).click();
    await expect(page.getByText("Bạn chưa xếp môn nào vào kế hoạch.")).toBeVisible();
    await page.getByRole("combobox", { name: "Chọn phương án kế hoạch" }).selectOption("1");
    await expect(page.getByRole("article").filter({ has: page.getByRole("heading", { name: "Kỳ kế hoạch 2" }) })).toContainText("TEST102");
    await page.reload();
    await expect(page.getByRole("article").filter({ has: page.getByRole("heading", { name: "Kỳ kế hoạch 2" }) })).toContainText("TEST102");
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    if (width < 1024) await expect(page.getByRole("navigation", { name: "Điều hướng di động" }).getByRole("link")).toHaveCount(5);
    expect(sourceCalls).toBe(0);
  });
}
