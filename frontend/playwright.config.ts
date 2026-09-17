import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./tests/e2e",
  fullyParallel: true,
  reporter: [["list"], ["html", { open: "never" }]],
  use: { baseURL: "http://localhost:3000", trace: "on-first-retry" },
  webServer: [
    {
      command: `${process.platform === "win32" ? "mvnw.cmd" : "./mvnw"} --batch-mode test-compile spring-boot:test-run "-Dspring-boot.run.arguments=--spring.data.redis.password="`,
      cwd: "../backend",
      url: "http://localhost:8080/api/health",
      timeout: 180_000,
      reuseExistingServer: false,
    },
    { command: "pnpm start", url: "http://localhost:3000/login", reuseExistingServer: false },
  ],
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});
