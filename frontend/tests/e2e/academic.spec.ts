import { expect, test } from "@playwright/test";
import { createAccountAndLogin } from "./auth-helpers";

const programOne = `pr_${"a".repeat(64)}`;
const programTwo = `pr_${"b".repeat(64)}`;
const detail = `dt_${"c".repeat(64)}`;
const backendCapabilities = [
  { capability: "PROFILE", mode: "PERSISTED", completeness: "SOURCE_VERIFIED" },
  { capability: "CURRICULUM", mode: "PERSISTED_PARTIAL", completeness: "UNKNOWN" },
  { capability: "COURSE_CATALOG", mode: "PERSISTED_PARTIAL", completeness: "UNKNOWN" },
  { capability: "ACADEMIC_RECORDS", mode: "LIVE_READ_ONLY", completeness: "UNKNOWN" },
  { capability: "ACADEMIC_RESULT_DETAIL", mode: "LIVE_READ_ONLY", completeness: "UNKNOWN" },
  { capability: "ACADEMIC_PROGRESS_SUMMARY", mode: "LIVE_READ_ONLY", completeness: "UNKNOWN" },
  { capability: "SCHEDULE", mode: "LIVE_READ_ONLY", completeness: "UNKNOWN" },
  { capability: "EXAMS", mode: "LIVE_READ_ONLY", completeness: "UNKNOWN" },
  { capability: "STUDENT_COURSE", mode: "BLOCKED_SOURCE_LIMIT", completeness: "UNKNOWN" },
  { capability: "ACADEMIC_RESULT", mode: "BLOCKED_SOURCE_LIMIT", completeness: "UNKNOWN" },
  { capability: "CLASS_SESSION", mode: "BLOCKED_SOURCE_LIMIT", completeness: "UNKNOWN" },
  { capability: "EXAM_PERSISTENCE", mode: "BLOCKED_SOURCE_LIMIT", completeness: "UNKNOWN" },
  { capability: "PREREQUISITE", mode: "BLOCKED_PARTIAL", completeness: "UNKNOWN" },
];

for (const width of [1280, 375, 320]) {
  test(`Source-reported progress is a manual read at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 844 });
    await createAccountAndLogin(page);
    let progressRequests = 0;
    const curriculumId = "00000000-0000-4000-8000-000000000001";
    await page.route("**/api/me/academic/curriculum-selection", (route) => route.fulfill({ json: {
      selectionMode: "USER_SELECTED_AMS", curriculum: { id: curriculumId, code: "TEST", name: "Chương trình kiểm thử",
        cohort: null, revision: null, minimumCredits: 100 },
    } }));
    await page.route("**/api/me/connections/phenikaa", (route) => route.fulfill({ json: {
      status: "CONNECTED", lastAuthenticatedAt: "2026-01-01T00:00:00Z", lastSuccessfulAccessAt: null,
      lastFailedAccessAt: null, lastFailureCode: null, reconnectionRequired: false,
    } }));
    await page.route("**/api/me/academic/source/**", (route) => {
      const url = new URL(route.request().url());
      if (url.pathname.endsWith("/status")) return route.fulfill({ json: {
        connectionState: "CONNECTED", lastSuccessfulAccessAt: null, capabilities: backendCapabilities,
      } });
      if (url.pathname.endsWith("/programs")) return route.fulfill({ json: { completeness: "UNKNOWN", programs: [] } });
      if (url.pathname.endsWith("/progress-summary")) {
        progressRequests++;
        expect(url.search).toBe("");
        return route.fulfill({ json: {
          mode: "SOURCE_REPORTED_LIVE_READ_ONLY", completeness: "UNKNOWN", selectionMode: "USER_SELECTED_AMS",
          curriculum: { id: curriculumId, code: "TEST", name: "Chương trình kiểm thử" },
          summary: { cumulativeAverageScale4: 3.25, cumulativeAverageScale10: 8.1, sourceAccumulatedCredits: 72 },
        } });
      }
      return route.abort();
    });
    await page.goto("/academic");
    const button = page.getByRole("button", { name: "Đọc tổng hợp tích lũy từ nguồn" });
    await expect(button).toBeEnabled();
    expect(progressRequests).toBe(0);
    await button.click();
    await expect(page.getByText("Giá trị tín chỉ tích lũy — nguồn báo")).toBeVisible();
    await expect(page.getByText("3.25")).toBeVisible();
    expect(progressRequests).toBe(1);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await page.reload();
    await expect(button).toBeEnabled();
    expect(progressRequests).toBe(1);
    await expect(page.getByText("Giá trị tín chỉ tích lũy — nguồn báo")).toHaveCount(0);
  });
}
const record = (ref: string) => ({ registrationRef: ref,
  course: { code: "TEST101", name: "Môn tổng hợp", credits: 1.5 },
  semester: { academicYearStart: 2025, termCode: "1", code: "2025-1" }, reportedLearningAttempt: 2,
  components: [{ code: "QT", name: "Quá trình", examAttempt: 1, score: 6.25 }],
  finalResult: { detailRef: detail, examAttempt: 2, outcome: "PASSED", numericScore: null, letterGrade: null, gradePoints: null },
});

test("Academic page requires an AMS login", async ({ page }) => {
  await page.goto("/academic");
  await expect(page).toHaveURL(/\/login$/);
});

test("Disabled integration never requests academic source endpoints", async ({ page }) => {
  await createAccountAndLogin(page);
  let sourceRequests = 0;
  await page.route("**/api/me/connections/phenikaa", (route) => route.fulfill({ status: 404 }));
  await page.route("**/api/me/academic/source/**", (route) => { sourceRequests++; return route.abort(); });
  await page.goto("/academic");
  await expect(page.getByText("Không khả dụng: tích hợp Phenikaa chưa được bật.")).toBeVisible();
  expect(sourceRequests).toBe(0);
});

for (const state of ["RECONNECTION_REQUIRED", "DISCONNECTED"] as const) {
  test(`Source state ${state} stops before programs and records`, async ({ page }) => {
    await createAccountAndLogin(page);
    const requests: string[] = [];
    await page.route("**/api/me/connections/phenikaa", (route) => route.fulfill({ json: {
      status: "CONNECTED", lastAuthenticatedAt: "2026-01-01T00:00:00Z", lastSuccessfulAccessAt: null,
      lastFailedAccessAt: null, lastFailureCode: null, reconnectionRequired: false,
    } }));
    await page.route("**/api/me/academic/source/**", (route) => {
      const path = new URL(route.request().url()).pathname;
      requests.push(path);
      if (path.endsWith("/status")) return route.fulfill({ json: { connectionState: state,
        lastSuccessfulAccessAt: null, capabilities: backendCapabilities } });
      return route.abort();
    });
    await page.goto("/academic");
    await expect(page.getByText(state === "RECONNECTION_REQUIRED" ? /Cần kết nối lại nguồn học vụ/ : /Chưa có kết nối học vụ có thể sử dụng/)).toBeVisible();
    expect(requests).toHaveLength(1);
    expect(requests[0]).toMatch(/\/status$/);
  });
}

for (const width of [1280, 375, 320]) {
  test(`Live read-only records ${width}px`, async ({ page }, testInfo) => {
    const mobile = width < 1024;
    await page.setViewportSize({ width, height: 844 });
    await createAccountAndLogin(page);
    let detailRequests = 0;
    const unexpected: string[] = [];
    await page.route(/https:\/\/.*(?:phenikaa|googleapis|resend)/, (route) => {
      unexpected.push(route.request().url()); return route.abort();
    });
    await page.route("**/api/me/connections/phenikaa", (route) => route.fulfill({ json: {
      status: "CONNECTED", lastAuthenticatedAt: "2026-01-01T00:00:00Z", lastSuccessfulAccessAt: null,
      lastFailedAccessAt: null, lastFailureCode: null, reconnectionRequired: false,
    } }));
    await page.route("**/api/me/academic/source/**", async (route) => {
      const url = new URL(route.request().url());
      expect(route.request().method()).toBe("GET");
      if (url.pathname.endsWith("/status")) return route.fulfill({ json: { connectionState: "CONNECTED",
        lastSuccessfulAccessAt: null, capabilities: backendCapabilities } });
      if (url.pathname.endsWith("/programs")) return route.fulfill({ json: { completeness: "UNKNOWN", programs: [
        { programRef: programOne, label: "Chương trình một" }, { programRef: programTwo, label: "Chương trình hai" },
      ] } });
      if (url.pathname.endsWith("/detail")) { detailRequests++; return route.fulfill({ json: { detailRef: detail,
        completeness: "UNKNOWN", components: [{ registrationRef: `rg_${"d".repeat(64)}`,
          code: "THI", name: "Thi nguồn", examAttempt: 2, score: 7.5 }] } }); }
      expect(url.searchParams.get("programRef")).toMatch(/^pr_[0-9a-f]{64}$/);
      return route.fulfill({ json: { completeness: "UNKNOWN", unknownSemantics: {
        creditsEarned: "UNKNOWN", includedInGpa: "UNKNOWN", currentResult: "UNKNOWN",
      }, records: [record(`rg_${"d".repeat(64)}`), record(`rg_${"e".repeat(64)}`)] } });
    });
    await page.goto("/");
    await page.getByRole("navigation", { name: mobile ? "Điều hướng di động" : "Điều hướng chính" })
      .getByRole("link", { name: "Học vụ" }).click();
    await expect(page).toHaveURL(/\/academic$/);
    await expect(page.getByRole("heading", { name: "Môn tổng hợp" })).toHaveCount(2);
    expect(detailRequests).toBe(0);
    await page.getByRole("button", { name: "Xem chi tiết tổng kết" }).first().click();
    await expect(page.getByText(/THI · Thi nguồn/)).toBeVisible();
    expect(detailRequests).toBe(1);
    await page.getByRole("button", { name: "Đóng chi tiết tổng kết" }).click();
    await page.getByRole("button", { name: "Xem chi tiết tổng kết" }).first().click();
    await expect(page.getByText(/THI · Thi nguồn/)).toBeVisible();
    expect(detailRequests).toBe(1);
    await page.getByLabel("Chương trình đang xem").selectOption("1");
    await expect(page.getByRole("heading", { name: "Môn tổng hợp" })).toHaveCount(2);
    await expect(page.getByText(/THI · Thi nguồn/)).toHaveCount(0);
    if (mobile) await page.getByLabel("Chọn giao diện").selectOption("dark");
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    const visibleText = await page.locator("main").innerText();
    for (const ref of [programOne, programTwo, detail]) expect(visibleText).not.toContain(ref);
    expect(page.url()).not.toContain(programOne);
    expect(unexpected).toEqual([]);
    await page.screenshot({ path: testInfo.outputPath("academic.png"), fullPage: true, animations: "disabled" });
  });
}
