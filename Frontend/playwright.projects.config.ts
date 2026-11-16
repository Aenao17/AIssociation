import { defineConfig, devices } from '@playwright/test';

// Explicit opt-in suite: use only the disposable database described in Raul's handoff.
export default defineConfig({
  testDir: './e2e',
  testMatch: 'project-workflow.spec.ts',
  fullyParallel: false,
  workers: 1,
  reporter: 'list',
  timeout: 90_000,
  use: {
    actionTimeout: 15_000,
    baseURL: process.env.PROJECT_E2E_FRONTEND_URL ?? 'http://localhost:5176',
    channel: process.platform === 'win32' ? 'msedge' : undefined,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'Desktop', use: { ...devices['Desktop Chrome'] } },
    { name: 'Mobile', use: { ...devices['Pixel 7'] } },
  ],
});
