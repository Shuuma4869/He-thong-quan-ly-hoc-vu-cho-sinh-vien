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
    const plannerLink = page.getByRole("link", { name: "Mở kế hoạch học kỳ" });
    await expect(plannerLink).toBeVisible();
    await plannerLink.click();
    await expect(page).toHaveURL(/\/planner$/);
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

  test(`Manual scenario comparison at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 844 });
    await createAccountAndLogin(page);
    let sourceCalls = 0, comparisonCalls = 0;
    await page.route("**/api/me/academic/source/**", (route) => { sourceCalls++; return route.abort(); });
    page.on("request", (request) => {
      if (request.url().includes("/api/me/academic/study-plan/compare?")) comparisonCalls++;
    });
    const csrf = await (await page.request.get("/api/auth/csrf")).json();
    const seeded = await page.request.post("/api/me/test-fixtures/curricula", { headers: { [csrf.headerName]: csrf.token } });
    expect(seeded.status()).toBe(204);
    await page.goto("/curriculum");
    await page.getByLabel("Chọn chương trình để xem").selectOption({ label: "1. CURR-A — Chương trình kiểm thử A" });
    await page.getByRole("button", { name: "Đặt làm chương trình theo dõi" }).click();
    const plannerLink = page.getByRole("link", { name: "Mở kế hoạch học kỳ" });
    await expect(plannerLink).toBeVisible();
    await plannerLink.click();
    await expect(page).toHaveURL(/\/planner$/);
    await expect(page.getByText("Bạn chưa xếp môn nào vào kế hoạch.")).toBeVisible();
    expect(comparisonCalls).toBe(0);
    await page.getByRole("button", { name: "Xếp TEST101 vào kế hoạch" }).click();
    await page.getByRole("button", { name: "Xếp TEST102 vào kế hoạch" }).click();
    const firstTerm = page.getByRole("article").filter({ has: page.getByRole("heading", { name: "Kỳ kế hoạch 1" }) });
    await expect(firstTerm).toContainText("Số môn: 2");
    await firstTerm.getByRole("spinbutton", { name: "Kỳ mới cho TEST102" }).fill("2");
    await firstTerm.getByRole("button", { name: "Chuyển kỳ TEST102" }).click();
    await expect(page.getByRole("heading", { name: "Kỳ kế hoạch 2" })).toBeVisible();
    await page.getByRole("button", { name: "Sao chép phương án" }).click();
    await expect(page.getByRole("combobox", { name: "Chọn phương án kế hoạch" })).toHaveValue("2");
    const secondTerm = page.getByRole("article").filter({ has: page.getByRole("heading", { name: "Kỳ kế hoạch 2" }) });
    await secondTerm.getByRole("spinbutton", { name: "Kỳ mới cho TEST102" }).fill("3");
    await secondTerm.getByRole("button", { name: "Chuyển kỳ TEST102" }).click();
    await expect(page.getByRole("heading", { name: "Kỳ kế hoạch 3" })).toBeVisible();
    await page.getByRole("button", { name: "Bỏ TEST101 khỏi kế hoạch" }).click();
    await page.getByRole("button", { name: "Xếp TEST103 vào kế hoạch" }).click();
    const termOne = page.getByRole("article").filter({ has: page.getByRole("heading", { name: "Kỳ kế hoạch 1" }) });
    await termOne.getByRole("spinbutton", { name: "Kỳ mới cho TEST103" }).fill("2");
    await termOne.getByRole("button", { name: "Chuyển kỳ TEST103" }).click();
    await expect(page.getByRole("article").filter({ has: page.getByRole("heading", { name: "Kỳ kế hoạch 2" }) })).toContainText("TEST103");
    expect(comparisonCalls).toBe(0);
    await page.getByRole("combobox", { name: "Phương án bên phải" }).selectOption("1");
    await page.getByRole("button", { name: "So sánh Phương án 2 với Phương án 1" }).click();
    const compared = page.getByRole("region", { name: "So sánh phương án" });
    await expect(compared.getByRole("heading", { name: "Khác biệt cách xếp môn" })).toBeVisible();
    expect(comparisonCalls).toBe(1);
    const moved = compared.getByRole("listitem").filter({ hasText: "TEST102" });
    await expect(moved).toContainText("Phương án 2: Kỳ kế hoạch 3");
    await expect(moved).toContainText("Phương án 1: Kỳ kế hoạch 2");
    await expect(compared.getByRole("listitem").filter({ hasText: "TEST101" }))
      .toContainText("Chỉ được xếp trong Phương án 1");
    await expect(compared.getByRole("listitem").filter({ hasText: "TEST103" }))
      .toContainText("Chỉ được xếp trong Phương án 2");
    const termTwo = page.getByRole("region", { name: "Các kỳ kế hoạch" }).getByRole("article")
      .filter({ has: page.getByRole("heading", { name: "Kỳ kế hoạch 2" }) });
    await termTwo.getByRole("spinbutton", { name: "Kỳ mới cho TEST103" }).fill("4");
    await termTwo.getByRole("button", { name: "Chuyển kỳ TEST103" }).click();
    await expect(compared.getByRole("heading", { name: "Khác biệt cách xếp môn" })).toHaveCount(0);
    expect(comparisonCalls).toBe(1);
    await page.getByRole("combobox", { name: "Chọn phương án kế hoạch" }).selectOption("1");
    await expect(page.getByRole("article").filter({ has: page.getByRole("heading", { name: "Kỳ kế hoạch 2" }) })).toContainText("TEST102");
    await page.reload();
    await expect(page.getByRole("article").filter({ has: page.getByRole("heading", { name: "Kỳ kế hoạch 2" }) })).toContainText("TEST102");
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    if (width < 1024) await expect(page.getByRole("navigation", { name: "Điều hướng di động" }).getByRole("link")).toHaveCount(5);
    expect(sourceCalls).toBe(0);
  });
}
