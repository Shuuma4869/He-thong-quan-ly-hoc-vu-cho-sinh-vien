import { expect, test } from "@playwright/test";
import { createAccountAndLogin } from "./auth-helpers";

const periodRef = `ep_${"a".repeat(64)}`;
const capabilities = [
  { capability: "SCHEDULE", mode: "LIVE_READ_ONLY", completeness: "UNKNOWN" },
  { capability: "EXAMS", mode: "LIVE_READ_ONLY", completeness: "UNKNOWN" },
  { capability: "CLASS_SESSION", mode: "BLOCKED_SOURCE_LIMIT", completeness: "UNKNOWN" },
  { capability: "EXAM_PERSISTENCE", mode: "BLOCKED_SOURCE_LIMIT", completeness: "UNKNOWN" },
];
const connection = { status: "CONNECTED", lastAuthenticatedAt: "2026-10-01T00:00:00Z",
  lastSuccessfulAccessAt: null, lastFailedAccessAt: null, lastFailureCode: null, reconnectionRequired: false };

test("Schedule page requires an AMS login", async ({ page }) => {
  await page.goto("/schedule");
  await expect(page).toHaveURL(/\/login$/);
});

test("Disabled integration never requests schedule or exams", async ({ page }) => {
  await createAccountAndLogin(page);
  let sourceRequests = 0;
  await page.route("**/api/me/connections/phenikaa", (route) => route.fulfill({ status: 404 }));
  await page.route("**/api/me/academic/source/**", (route) => { sourceRequests++; return route.abort(); });
  await page.goto("/schedule");
  await expect(page.getByText("Không khả dụng: tích hợp Phenikaa chưa được bật.")).toBeVisible();
  expect(sourceRequests).toBe(0);
});

test("Source state race stops before schedule and exam requests", async ({ page }) => {
  await createAccountAndLogin(page);
  const requests: string[] = [];
  await page.route("**/api/me/connections/phenikaa", (route) => route.fulfill({ json: connection }));
  await page.route("**/api/me/academic/source/**", (route) => {
    const path = new URL(route.request().url()).pathname;
    requests.push(path);
    if (path.endsWith("/status")) return route.fulfill({ json: {
      connectionState: "RECONNECTION_REQUIRED", lastSuccessfulAccessAt: null, capabilities,
    } });
    return route.abort();
  });
  await page.goto("/schedule");
  await expect(page.getByText(/Cần kết nối lại nguồn học vụ/)).toBeVisible();
  expect(requests).toHaveLength(1);
});

for (const width of [1280, 375, 320]) {
  test(`Live read-only schedule and exams ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 844 });
    await createAccountAndLogin(page);
    const requests: string[] = [];
    await page.route(/https:\/\/.*(?:phenikaa|googleapis|resend)/, (route) => route.abort());
    await page.route("**/api/me/connections/phenikaa", (route) => route.fulfill({ json: connection }));
    await page.route("**/api/me/academic/source/**", (route) => {
      const url = new URL(route.request().url());
      requests.push(url.pathname);
      expect(route.request().method()).toBe("GET");
      if (url.pathname.endsWith("/status")) return route.fulfill({ json: {
        connectionState: "CONNECTED", lastSuccessfulAccessAt: null, capabilities,
      } });
      if (url.pathname.endsWith("/schedule")) return route.fulfill({ json: {
        completeness: "UNKNOWN", identityScope: "UNVERIFIED", zone: "Asia/Ho_Chi_Minh",
        from: url.searchParams.get("from"), through: url.searchParams.get("through"), entries: [
          { courseName: "Môn kiểm thử A", date: url.searchParams.get("from"), startsAt: "08:30:00",
            endsAt: "10:00:00", room: "P.TEST", lecturer: "Giảng viên kiểm thử", kind: "CLASS" },
          { courseName: "Môn kiểm thử A", date: url.searchParams.get("from"), startsAt: "08:30:00",
            endsAt: "10:00:00", room: "P.TEST", lecturer: "Giảng viên kiểm thử", kind: "CLASS" },
        ],
      } });
      if (url.pathname.endsWith("/exams/periods")) return route.fulfill({ json: { completeness: "UNKNOWN",
        periods: [{ periodRef, label: "Kỳ nguồn giả định" }] } });
      if (url.pathname.endsWith("/exams")) {
        expect(url.searchParams.get("periodRef")).toBe(periodRef);
        return route.fulfill({ json: { completeness: "UNKNOWN", identityScope: "UNVERIFIED",
          zone: "Asia/Ho_Chi_Minh", period: { periodRef, label: "Kỳ nguồn giả định" }, entries: [
            { courseCode: "TEST101", courseName: "Môn kiểm thử A", examAttempt: 2,
              examSession: null, date: "2026-10-01", startsAt: "08:30:00", endsAt: null, room: null },
            { courseCode: "TEST101", courseName: "Môn kiểm thử A", examAttempt: 2,
              examSession: null, date: "2026-10-01", startsAt: "08:30:00", endsAt: null, room: null },
          ] } });
      }
      return route.abort();
    });
    await page.goto("/schedule");
    await expect(page.getByRole("heading", { name: "Lịch học & lịch thi" })).toBeVisible();
    await expect(page.getByText(/Đã kết nối nguồn học vụ/)).toBeVisible();
    expect(requests).toHaveLength(1);
    const nav = page.getByRole("navigation", { name: width < 1024 ? "Điều hướng di động" : "Điều hướng chính" });
    await expect(nav.getByRole("link", { name: "Lịch" })).toHaveAttribute("aria-current", "page");
    await page.getByRole("button", { name: "Đọc lịch", exact: true }).click();
    await expect(page.getByText("P.TEST")).toHaveCount(2);
    await expect(page.getByText(/Lớp học/)).toHaveCount(2);
    await page.getByRole("button", { name: "Lịch thi", exact: true }).click();
    expect(requests.filter((path) => path.endsWith("/exams/periods"))).toHaveLength(0);
    await page.getByRole("button", { name: "Đọc danh sách kỳ thi" }).click();
    await expect(page.getByLabel("Kỳ thi đang xem")).toBeVisible();
    expect(requests.filter((path) => path.endsWith("/exams"))).toHaveLength(0);
    await page.getByRole("button", { name: "Đọc lịch thi" }).click();
    await expect(page.getByText("Lần thi nguồn báo: 2")).toHaveCount(2);
    const html = await page.locator("body").evaluate((body) => body.innerHTML);
    expect(html).not.toContain(periodRef);
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width);
  });
}
