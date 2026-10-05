import { expect, test } from "@playwright/test";
import { createAccountAndLogin } from "./auth-helpers";

const id = "10000000-0000-4000-8000-000000000001";
const queued = { runId: id, status: "QUEUED", trigger: "MANUAL", requestedAt: "2026-01-01T00:00:00Z",
  startedAt: null, finishedAt: null, nextAttemptAt: null, currentStep: null,
  profileStepStatus: "PENDING", curriculumStepStatus: "PENDING", failureCode: null, attemptCount: 0 };
const connection = { status: "CONNECTED", lastAuthenticatedAt: "2026-01-01T00:00:00Z",
  lastSuccessfulAccessAt: null, lastFailedAccessAt: null, lastFailureCode: null, reconnectionRequired: false };

test.beforeEach(async ({ page }) => { await createAccountAndLogin(page); });

test("không gửi yêu cầu khi nguồn chưa khả dụng", async ({ page }) => {
  await page.goto("/sync");
  await expect(page.getByText("Không khả dụng: tích hợp Phenikaa chưa được bật trên máy chủ.")).toBeVisible();
  await expect(page.getByRole("button", { name: "Đồng bộ ngay" })).toBeDisabled();
  await expect(page.getByText("Lịch sử không khả dụng khi tích hợp Phenikaa chưa được bật.")).toBeVisible();
});

test("gửi một lượt bằng CSRF, theo dõi đến PARTIAL và phân trang lịch sử", async ({ page }) => {
  await page.route("**/api/me/connections/phenikaa", (route) => route.fulfill({ json: connection }));
  await page.route("**/api/me/sync/current", (route) => route.fulfill({ status: 404 }));
  let checks = 0;
  await page.route(`**/api/me/sync/runs/${id}`, (route) => {
    checks++;
    return route.fulfill({ json: checks === 1 ? queued : { ...queued, status: "PARTIAL", finishedAt: "2026-01-01T00:01:00Z",
      profileStepStatus: "SUCCEEDED", curriculumStepStatus: "FAILED", failureCode: "CURRICULUM_REFRESH_FAILED" } });
  });
  await page.route("**/api/me/sync/runs?**", (route) => route.fulfill({ json: route.request().url().includes("cursor=")
    ? { items: [{ ...queued, runId: "10000000-0000-4000-8000-000000000002" }], nextCursor: null }
    : { items: [queued], nextCursor: "next" } }));
  let posted = 0;
  await page.route("**/api/me/sync", (route) => {
    if (route.request().method() !== "POST") return route.continue();
    posted++;
    expect(route.request().headers()["x-csrf-token"]).toBeTruthy();
    return route.fulfill({ status: 202, json: queued });
  });
  await page.goto("/sync");
  const button = page.getByRole("button", { name: "Đồng bộ ngay" });
  await expect(button).toBeEnabled();
  await button.click();
  await expect(button).toBeDisabled();
  await expect(page.getByText("Đồng bộ hoàn tất một phần", { exact: true }).first()).toBeVisible({ timeout: 10000 });
  expect(posted).toBe(1);
  await page.getByRole("button", { name: "Tải thêm" }).click();
  await expect(page.getByRole("button", { name: "Tải thêm" })).toHaveCount(0);
});

test("đọc lại run đang hoạt động sau reload trên màn hình 320px", async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 640 });
  await page.route("**/api/me/connections/phenikaa", (route) => route.fulfill({ json: connection }));
  await page.route("**/api/me/sync/current", (route) => route.fulfill({ json: queued }));
  await page.route(`**/api/me/sync/runs/${id}`, (route) => route.fulfill({ json: queued }));
  await page.route("**/api/me/sync/runs?**", (route) => route.fulfill({ json: { items: [], nextCursor: null } }));
  await page.goto("/sync");
  await expect(page.getByText("Đã có lượt đồng bộ đang hoạt động; không gửi thêm yêu cầu trùng.")).toBeVisible();
  await page.reload();
  await expect(page.getByRole("button", { name: "Đồng bộ ngay" })).toBeDisabled();
  await expect(page.getByRole("navigation", { name: "Điều hướng di động" }).getByRole("link", { name: "Đồng bộ" })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});
