import { expect, test, type Page } from '@playwright/test'
import { mkdir } from 'node:fs/promises'
import { resolve } from 'node:path'

const clinic = { id: '11111111-1111-4111-8111-111111111111', slug: 'synthetic-clinic', name: 'Synthetic Demo Clinic' }
const branch = { id: '22222222-2222-4222-8222-222222222222', name: 'Synthetic Main Branch', address: null, phone: null, active: true }
const owner = { id: '33333333-3333-4333-8333-333333333333', fullName: 'Synthetic Owner', email: 'owner@test.invalid', role: 'OWNER', active: true }
const screenshotDir = resolve(process.cwd(), '../docs/evidence/m1_4_1')
const routeLabels: Record<string, string> = {
  '/workspace/command-center': 'Your practice at a glance',
  '/workspace/patient-360': 'Patient 360°',
  '/workspace/journey': 'Patient Journey',
  '/workspace/calendar': 'Appointment Calendar',
  '/workspace/module-status': 'Module readiness',
  '/workspace/settings': 'Clinic Settings',
  '/workspace/branches': 'Branches & operating hours',
  '/workspace/chairs': 'Dental chairs',
  '/workspace/staff': 'Staff assignments',
}

// Matches backend calls only. A '**/api/**' glob would also swallow Vite's own source modules under
// /src/api/* and serve them JSON, which kills the module script and blanks the whole app.
const backendOnly = (url: URL) => url.pathname.startsWith('/api/')

// Mirrors the backend contract for the current synthetic session. A page reload drops the in-memory
// access token by design (frontend/src/api/client.ts), so client-side recovery happens through
// /api/auth/refresh — the fixture answers it with the same session a real refresh cookie would earn.
let currentSession: Record<string, unknown> | null = null

async function installApiFixtures(page: Page, options: { rejectSignIn?: boolean } = {}) {
  currentSession = null
  await page.route(backendOnly, async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/auth/refresh') {
      if (currentSession) {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(currentSession) })
      } else {
        await route.fulfill({ status: 401, contentType: 'application/json', body: '{}' })
      }
    } else if (path === '/api/auth/google' || path === '/api/auth/platform-admin/google') {
      if (options.rejectSignIn) {
        await route.fulfill({ status: 403, contentType: 'application/json', body: JSON.stringify({ message: 'Not an active staff member of that clinic.' }) })
        return
      }
      const platformAdmin = path.endsWith('/platform-admin/google')
      const body = route.request().postDataJSON()
      const requestedRole = typeof body?.idToken === 'string' ? body.idToken.split(':')[1] : undefined
      const role = ['OWNER', 'ADMIN', 'DENTIST', 'FRONT_DESK'].includes(requestedRole) ? requestedRole : 'OWNER'
      const session = { accessToken: 'synthetic-playwright-access-token', user: { ...owner, role: platformAdmin ? 'PLATFORM_ADMIN' : role, platformAdmin }, clinic: platformAdmin ? null : clinic }
      currentSession = session
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(session) })
    } else if (path === '/api/auth/logout') {
      // A real logout clears the refresh cookie server-side, so a later refresh must fail.
      currentSession = null
      await route.fulfill({ status: 204, body: '' })
    } else if (path === '/api/platform-admin/clinics') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([
        { id: clinic.id, slug: clinic.slug, name: clinic.name, planCode: 'STARTER', subscriptionStatus: 'TRIAL', active: true, createdAt: '2026-01-01T00:00:00Z', accessScope: 'DIRECTORY_ONLY' },
      ]) })
    } else if (path === '/api/clinic-management/clinic') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ ...clinic, timezone: 'Asia/Manila', currency: 'PHP', email: null, phone: null, address: null }) })
    } else if (path === '/api/clinic-management/branches') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([branch]) })
    } else if (path === '/api/clinic-management/staff') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([owner]) })
    } else if (path === '/api/clinic-management/branches/' + branch.id + '/chairs') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([{ id: 'chair-1', name: 'Synthetic Chair 1', active: true }]) })
    } else if (path === '/api/clinic-management/branches/' + branch.id + '/hours') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' })
    } else if (path === '/api/clinic-management/branches/' + branch.id + '/staff') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([owner]) })
    } else {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' })
    }
  })
}

async function installGoogleScriptDouble(page: Page) {
  await page.addInitScript(() => {
    const     testWindow = window as unknown as { google?: { accounts: { id: { initialize: (config: { callback: (value: { credential?: string }) => void }) => void; renderButton: (element: HTMLElement, options: unknown) => void; disableAutoSelect: () => void } } }; __gsiInitCount?: number; __gsiCallback?: (value: { credential?: string }) => void; __gsiCredential?: string }

    testWindow.google = { accounts: { id: {
      initialize: (config) => {
        testWindow.__gsiInitCount = (testWindow.__gsiInitCount ?? 0) + 1
        testWindow.__gsiCallback = config.callback
      },
      renderButton: (element) => {
        const button = document.createElement('button')
        button.type = 'button'
        button.textContent = 'Sign in with Google'
        // The credential is read at click time, so a test sets it before clicking and exactly one
        // sign-in request is made. Firing the callback twice would race two overlapping sessions.
        button.addEventListener('click', () => testWindow.__gsiCallback?.({ credential: testWindow.__gsiCredential ?? 'synthetic-id-token' }))
        element.replaceChildren(button)
      },
      disableAutoSelect: () => {},
    } } }
  })
  await page.route('https://accounts.google.com/gsi/client**', (route) => route.fulfill({ status: 200, contentType: 'application/javascript', body: '/* script load stub */' }))
}

test.beforeAll(async () => mkdir(screenshotDir, { recursive: true }))

test('clinic workspace, management and public onboarding routes render at desktop, tablet and mobile', async ({ page }) => {
  const mutatingRequests: string[] = []
  page.on('request', (request) => {
    const url = new URL(request.url())
    // Sign-in/sign-out/refresh traffic is session management, not a business-data mutation.
    const authEndpoint = ['/api/auth/refresh', '/api/auth/google', '/api/auth/logout'].includes(url.pathname)
    if (url.pathname.startsWith('/api/') && request.method() !== 'GET' && !authEndpoint) mutatingRequests.push(`${request.method()} ${url.pathname}`)
  })
  await installApiFixtures(page)
  await installGoogleScriptDouble(page)
  const sizes = [
    { name: 'desktop', width: 1440, height: 1000 },
    { name: 'tablet', width: 820, height: 1180 },
    { name: 'mobile', width: 390, height: 844 },
  ]
  const routes = [
    { path: '/workspace/command-center', label: routeLabels['/workspace/command-center'], role: 'OWNER' },
    { path: '/workspace/command-center', label: 'Clinic operations and configuration', role: 'ADMIN' },
    { path: '/workspace/command-center', label: 'Your clinical day, organized', role: 'DENTIST' },
    { path: '/workspace/command-center', label: 'Keep appointments and arrivals moving', role: 'FRONT_DESK' },
    { path: '/workspace/patient-360', label: routeLabels['/workspace/patient-360'], role: 'DENTIST' },
    { path: '/workspace/journey', label: routeLabels['/workspace/journey'], role: 'OWNER' },
    { path: '/workspace/calendar', label: routeLabels['/workspace/calendar'], role: 'OWNER' },
    { path: '/workspace/module-status', label: 'Module readiness', role: 'ADMIN' },
    { path: '/workspace/settings', label: routeLabels['/workspace/settings'], role: 'ADMIN' },
    { path: '/workspace/branches', label: routeLabels['/workspace/branches'], role: 'ADMIN' },
    { path: '/workspace/chairs', label: routeLabels['/workspace/chairs'], role: 'ADMIN' },
    { path: '/workspace/staff', label: routeLabels['/workspace/staff'], role: 'ADMIN' },
  ]

  for (const size of sizes) {
    await page.setViewportSize({ width: size.width, height: size.height })
    for (const item of routes) {
      // A fresh document load: the access token lives in memory only, so each role signs in again,
      // exactly as a person switching accounts would.
      await page.goto('/login')
      await page.getByLabel('Clinic code').fill('synthetic-clinic')
      await page.evaluate((requestedRole) => {
        const w = window as unknown as { __gsiCredential?: string }
        w.__gsiCredential = `synthetic-id-token:${requestedRole}`
      }, item.role)
      await page.getByRole('button', { name: 'Sign in with Google' }).click()
      await expect(page).toHaveURL(/\/workspace\//)
      await page.goto(item.path)
      await expect(page.getByText(item.label, { exact: false }).first()).toBeVisible()
      const dimensions = await page.evaluate(() => ({ viewport: window.innerWidth, document: document.documentElement.scrollWidth }))
      expect(dimensions.document, `${item.path} overflows at ${size.name}: ${JSON.stringify(dimensions)}`).toBeLessThanOrEqual(dimensions.viewport + 1)
      if (size.name === 'desktop' && item.path === '/workspace/command-center' && item.role === 'OWNER') {
        await page.screenshot({ path: resolve(screenshotDir, 'desktop-command-center.png'), fullPage: true })
      }
      // Leave through the product, not by resetting state, so the next role starts signed out.
      await page.getByRole('button', { name: 'Sign out' }).click()
      await expect(page).toHaveURL(/\/login$/)
    }
    await page.goto('/register-clinic')
    await expect(page.getByText('Registration is disabled by default.')).toBeVisible()
    await expect(page.getByRole('button', { name: /Sign in with Google/ })).toHaveCount(0)
    await expect(page.getByText('Register a clinic')).toBeVisible()
    if (size.name === 'mobile') await page.screenshot({ path: resolve(screenshotDir, 'mobile-registration-disabled.png'), fullPage: true })

    const token = 'synthetic-invitation-token-never-real'
    await page.goto(`/accept-invitation?token=${token}`)
    await expect(page.getByRole('heading', { name: 'Join your dental clinic' })).toBeVisible()
    await expect.poll(() => page.url()).not.toContain(token)
    expect(await page.evaluate(() => document.referrer)).toBe('')
    if (size.name === 'tablet') await page.screenshot({ path: resolve(screenshotDir, 'tablet-invitation-acceptance.png'), fullPage: true })
  }

  // Final pass: one signed-in OWNER session, reached through the product, to confirm the shell shows
  // the signed-in clinic and that nothing above mutated any business data.
  await page.goto('/login')
  await page.getByLabel('Clinic code').fill('synthetic-clinic')
  await page.evaluate(() => {
    const w = window as unknown as { __gsiCredential?: string }
    w.__gsiCredential = 'synthetic-id-token:OWNER'
  })
  await page.getByRole('button', { name: 'Sign in with Google' }).click()
  await expect(page).toHaveURL(/\/workspace\//)
  await page.goto('/workspace/command-center')
  await expect(page.getByText('Synthetic Demo Clinic').first()).toBeVisible()
  expect(mutatingRequests).toEqual([])
})

test('platform admin receives directory information and is routed to the separate dashboard', async ({ page }) => {
  await installApiFixtures(page)
  await installGoogleScriptDouble(page)
  await page.goto('/platform-admin/login')
  await page.evaluate(() => {
    const w = window as unknown as { __gsiCredential?: string }
    w.__gsiCredential = 'synthetic-platform-admin-token'
  })
  await page.getByRole('button', { name: 'Sign in with Google' }).click()
  await expect(page).toHaveURL(/platform-admin$/)
  await page.goto('/platform-admin')
  await expect(page.getByRole('heading', { name: 'Clinics' })).toBeVisible()
  await expect(page.getByText(/no clinic selected/)).toBeVisible()
  await page.screenshot({ path: resolve(screenshotDir, 'desktop-platform-admin-directory.png'), fullPage: true })
  await expect(page.getByText(/Maria Santos|Patient 360|clinical notes/i)).toHaveCount(0)
})

test('Google Identity initializes once while navigating between login screens', async ({ page }) => {
  // The sign-in itself is rejected, which is what keeps this test on the login screens: the count
  // under test is the Google Identity lifecycle, not a successful session.
  await installApiFixtures(page, { rejectSignIn: true })
  await installGoogleScriptDouble(page)
  await page.goto('/login')
  await page.getByLabel('Clinic code').fill('synthetic-clinic')
  await expect(page.getByRole('button', { name: 'Sign in with Google' })).toBeVisible()
  await page.getByRole('button', { name: 'Sign in with Google' }).click()
  await expect(page.getByRole('alert')).toBeVisible()
  await page.getByRole('link', { name: 'Platform administrator sign in' }).click()
  await expect(page.getByRole('heading', { name: 'Platform administration' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Sign in with Google' })).toBeVisible()
  await page.getByRole('link', { name: 'Clinic staff sign in' }).click()
  await expect(page.getByRole('button', { name: 'Sign in with Google' })).toBeVisible()
  expect(await page.evaluate(() => (window as unknown as { __gsiInitCount?: number }).__gsiInitCount)).toBe(1)
})

test('owner invitation form submits a valid selected role and active branch', async ({ page }) => {
  await installApiFixtures(page)
  await installGoogleScriptDouble(page)
  let submitted: Record<string, unknown> | null = null
  await page.route('**/api/onboarding/invitations', async (route) => {
    if (route.request().method() === 'POST') {
      submitted = route.request().postDataJSON()
      await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify({
        id: '44444444-4444-4444-8444-444444444444', token: 'synthetic-invitation-token', expiresAt: '2030-01-01T00:00:00Z',
      }) })
    } else {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' })
    }
  })

  await page.goto('/login')
  await page.getByLabel('Clinic code').fill(clinic.slug)
  await page.evaluate(() => { (window as unknown as { __gsiCredential?: string }).__gsiCredential = 'synthetic-id-token:OWNER' })
  await page.getByRole('button', { name: 'Sign in with Google' }).click()
  await expect(page).toHaveURL(/\/workspace\//)
  await page.goto('/workspace/staff')
  await page.getByLabel('Email').fill('staff@test.invalid')
  const roleSelect = page.locator('label').filter({ hasText: /^Role/ }).locator('select')
  await roleSelect.selectOption('FRONT_DESK')
  await expect(roleSelect).toHaveValue('FRONT_DESK')
  await page.getByRole('button', { name: 'Create invitation' }).click()
  await expect(page.getByText('One-time invitation created')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Copy private link' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Open acceptance page' })).toBeVisible()
  await expect(page.getByLabel('One-time invitation token')).toHaveCount(0)
  expect(submitted).toEqual({ email: 'staff@test.invalid', role: 'FRONT_DESK', branchId: branch.id })
})
