import { expect, type Page } from "@playwright/test";

export const fixturePassword = "Phase1-test-password";
export const fixtureEmail = () => `phase1-${crypto.randomUUID()}@example.test`;

export async function createAccountAndLogin(page: Page) {
  const email = fixtureEmail();
  const csrf = await (await page.request.get("/api/auth/csrf")).json();
  const registration = await page.request.post("/api/auth/register", {
    headers: { [csrf.headerName]: csrf.token },
    data: { email, password: fixturePassword, displayName: "Test student" },
  });
  expect(registration.status()).toBe(201);
  const loginCsrf = await (await page.request.get("/api/auth/csrf")).json();
  const login = await page.request.post("/api/auth/login", {
    headers: { [loginCsrf.headerName]: loginCsrf.token },
    form: { email, password: fixturePassword },
  });
  expect(login.status()).toBe(204);
  return email;
}
