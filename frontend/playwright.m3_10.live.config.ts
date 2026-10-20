import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  testMatch: 'm3_10_live.spec.ts',
  fullyParallel: false,
  workers: 1,
  timeout: 240_000,
  expect: { timeout: 20_000 },
  reporter: [['list']],
  outputDir: '/private/tmp/m310-live-playwright-results',
  use: {
    baseURL: 'http://127.0.0.1:' + (process.env.M3_10_FRONTEND_PORT ?? '5176'),
    browserName: 'chromium',
    headless: true,
    trace: 'off',
    screenshot: 'only-on-failure',
    video: 'off',
  },
  webServer: {
    command: 'npm run dev -- --host 127.0.0.1 --port ' + (process.env.M3_10_FRONTEND_PORT ?? '5176') + ' --strictPort',
    url: 'http://127.0.0.1:' + (process.env.M3_10_FRONTEND_PORT ?? '5176'),
    reuseExistingServer: false,
    timeout: 60_000,
    stdout: 'pipe',
    stderr: 'pipe',
    env: { VITE_GOOGLE_CLIENT_ID: 'm310-live-test-client', VITE_API_URL: process.env.VITE_API_URL ?? '' },
  },
})
