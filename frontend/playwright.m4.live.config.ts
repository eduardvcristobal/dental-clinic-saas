import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir:'./e2e',
  testMatch:['m4_live.spec.ts','m5_live.spec.ts','m6_live.spec.ts'],
  fullyParallel:false,
  workers:1,
  timeout:120_000,
  expect:{timeout:15_000},
  reporter:[['list']],
  outputDir:'test-results-m4-live',
  use:{
    baseURL:'http://127.0.0.1:'+(process.env.M4_FRONTEND_PORT??'5174'),
    browserName:'chromium',
    headless:true,
    trace:'retain-on-failure',
    screenshot:'only-on-failure',
  },
  webServer:{
    command:'npm run dev -- --host 127.0.0.1 --port '+(process.env.M4_FRONTEND_PORT??'5174')+' --strictPort',
    url:'http://127.0.0.1:'+(process.env.M4_FRONTEND_PORT??'5174'),
    reuseExistingServer:false,
    timeout:60_000,
    stdout:'pipe',
    stderr:'pipe',
    env:{VITE_GOOGLE_CLIENT_ID:'m25-live-test-client',VITE_API_URL:process.env.VITE_API_URL??''},
  }
})
