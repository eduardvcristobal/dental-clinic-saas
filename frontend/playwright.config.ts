import { defineConfig } from '@playwright/test'

/**
 * Browser suite. The webServer is the single source of truth for server startup: Playwright starts a
 * fresh Vite dev server on a fixed port with a synthetic (never real) Google client ID, waits until the
 * port answers, and tears it down afterwards. Nothing is reused from a previous run, so the tests can
 * never silently run against a stale server built with different environment variables.
 *
 * The loopback IP is used rather than `localhost` so the URL cannot resolve to ::1 and miss an IPv4-only
 * server. Every network interaction is intercepted; no request reaches a real backend or Google.
 */
export default defineConfig({
  testDir: './e2e',
  testMatch: /(m1_4_1|m3_10).spec.ts/,
  fullyParallel: false,
  workers: 1,
  timeout: 90_000,
  expect: { timeout: 10_000 },
  reporter: [['list']],
  outputDir: './test-results',
  projects: process.env.PLAYWRIGHT_ALL_BROWSERS === 'true' ? [
    { name: 'chromium', use: { browserName: 'chromium' } },
    { name: 'firefox', use: { browserName: 'firefox' } },
    { name: 'webkit', use: { browserName: 'webkit' } },
  ] : [{ name: 'chromium', use: { browserName: 'chromium' } }],
  use: {
    baseURL: 'http://127.0.0.1:5174',
    headless: true,
    // Browser projects pin Playwright-managed engines so local runs and CI exercise the same binaries.
    trace: 'on',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  webServer: {
    command:
      'VITE_GOOGLE_CLIENT_ID=playwright-synthetic-client VITE_API_URL= npm run dev -- --host 127.0.0.1 --port 5174 --strictPort',
    url: 'http://127.0.0.1:5174',
    reuseExistingServer: false,
    timeout: 60_000,
    stdout: 'pipe',
    stderr: 'pipe',
    env: { VITE_GOOGLE_CLIENT_ID: 'playwright-synthetic-client', VITE_API_URL: '' },
  },
})
