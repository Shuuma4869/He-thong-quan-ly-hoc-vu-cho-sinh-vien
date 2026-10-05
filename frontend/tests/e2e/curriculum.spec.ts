import { expect, test } from "@playwright/test";
import { createAccountAndLogin } from "./auth-helpers";

const first = "10000000-0000-4000-8000-000000000001";
const second = "10000000-0000-4000-8000-000000000002";
const curricula = [first, second].map((id, index) => ({ id, code: `TEST${index}`, name: `Chương trình tổng hợp ${index + 1}`, cohort: null, revision: null, minimumCredits: 120, courseCount: 1, groupCount: 1 }));

test("Curriculum requires login", async ({ page }) => {
  await page.goto("/curriculum");
  await expect(page).toHaveURL(/\/login$/);
});

for (const mobile of [false, true]) {
  test(`Read persisted curricula and catalog ${mobile ? "mobile dark" : "desktop"}`, async ({ page }, testInfo) => {
    if (mobile) await page.setViewportSize({ width: 320, height: 844 });
    await createAccountAndLogin(page);
    const unexpected: string[] = [];
    await page.route(/https:\/\/.*(?:phenikaa|googleapis|resend)/, async (route) => {
      unexpected.push(route.request().url()); await route.abort();
    });
    await page.route("**/api/me/academic/**", async (route) => {
      const url = new URL(route.request().url());
      expect(route.request().method()).toBe("GET");
      const pageData = (items: unknown[]) => ({ items, nextCursor: null });
      if (url.pathname.endsWith("/curricula")) return route.fulfill({ json: pageData(curricula) });
      if (url.pathname.endsWith("/catalog/courses")) return route.fulfill({ json: pageData([
        { id: first, code: "TEST999", name: "Môn danh mục tổng hợp", credits: 1.5, curriculumLinked: false },
      ]) });
      if (url.pathname.endsWith("/courses")) return route.fulfill({ json: pageData(url.searchParams.get("search") === "missing" ? [] : [
        { id: first, courseId: first, code: "TEST101", name: "Môn nhóm tổng hợp", credits: 3, requirement: "REQUIRED", groupId: first, groupName: "Nhóm tổng hợp", recommendedTerm: null },
      ]) });
      const curriculum = curricula.find((c) => url.pathname.endsWith(c.id));
      expect(curriculum).toBeTruthy();
      await route.fulfill({ json: { curriculum, groups: pageData([
        { id: first, code: "R", name: "Nhóm tổng hợp", requirement: "REQUIRED", minimumCredits: null, minimumCourseCount: null },
      ]) } });
    });
    await page.goto("/");
    await page.getByRole("navigation", { name: mobile ? "Điều hướng di động" : "Điều hướng chính" })
      .getByRole("link", { name: "Chương trình", exact: true }).click();
    await expect(page).toHaveURL(/\/curriculum$/);
    await page.getByLabel("Chọn chương trình để xem").selectOption(second);
    await expect(page.getByRole("heading", { name: "Chương trình tổng hợp 2" })).toBeVisible();
    await expect(page.getByRole("heading", { name: "Nhóm tổng hợp", exact: true })).toBeVisible();
    if (mobile) await page.getByLabel("Chọn giao diện").selectOption("dark");
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await page.screenshot({ path: testInfo.outputPath("curriculum.png"), fullPage: true, animations: "disabled" });
    await page.getByRole("searchbox").fill("missing");
    await expect(page.getByText(/Không tìm thấy môn phù hợp/)).toBeVisible();
    await page.getByRole("button", { name: "Xóa tìm kiếm" }).click();
    await expect(page.getByText("Môn nhóm tổng hợp")).toBeVisible();
    await page.getByRole("button", { name: "Danh mục môn", exact: true }).click();
    await expect(page.getByText("Chưa có liên kết đã lưu", { exact: true })).toBeVisible();
    await expect(page.getByText("1.5", { exact: true })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await page.getByRole("searchbox").focus();
    await expect(page.getByRole("searchbox")).toBeFocused();
    expect(unexpected).toEqual([]);
    for (const phrase of ["Chương trình hiện tại", "Đã hoàn thành", "Còn thiếu", "Đủ điều kiện", "GPA"])
      await expect(page.locator("main")).not.toContainText(phrase);
  });
}
