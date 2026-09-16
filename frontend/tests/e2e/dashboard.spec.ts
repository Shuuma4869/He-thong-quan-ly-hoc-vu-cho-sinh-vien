import { expect, test } from "@playwright/test";

test("hiển thị dashboard AMS", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Chào mừng đến với AMS" })).toBeVisible();
  await expect(page.getByText("Chưa kết nối Phenikaa")).toBeVisible();
});
