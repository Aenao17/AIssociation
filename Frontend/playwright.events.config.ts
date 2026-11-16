import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  testMatch: 'event-workflow.spec.ts',
  workers: 1,
  timeout: 90000,
  use: {
    baseURL: process.env.EVENTS_E2E_FRONTEND_URL ?? 'http://localhost:5177',
    channel: process.platform === 'win32' ? 'msedge' : undefined,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'Desktop', use: { ...devices['Desktop Chrome'] } },
    { name: 'Mobile', use: { ...devices['Pixel 7'] } },
  ],
});
