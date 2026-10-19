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
  fullyParallel: false,
  workers: 1,
  timeout: 45_000,
  expect: { timeout: 10_000 },
  reporter: [['list']],
  outputDir: './test-results',
  use: {
    baseURL: 'http://127.0.0.1:5174',
    headless: true,
    // The bundled Chromium, not a system Chrome, so local runs and CI exercise the same binary.
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
