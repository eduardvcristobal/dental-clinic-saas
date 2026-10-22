import { expect, test, type Page } from '@playwright/test'
import { pickDropdown } from './dropdown'
import { mkdir } from 'node:fs/promises'
import { resolve } from 'node:path'

const clinic = { id: '11111111-1111-4111-8111-111111111111', slug: 'synthetic-clinic', name: 'Synthetic Demo Clinic' }
const branch = { id: '22222222-2222-4222-8222-222222222222', name: 'Synthetic Main Branch', address: null, phone: null, active: true }
const owner = { id: '33333333-3333-4333-8333-333333333333', fullName: 'Synthetic Owner', email: 'owner@test.invalid', role: 'OWNER', active: true }
const screenshotDir = resolve(process.cwd(), '../docs/evidence/m1_4_1')

// Whole-day board fixture: two chairs and one provider on the Manila clinic date.
const dayBoard = {
  date: '2026-10-21',
  timezone: 'Asia/Manila',
  branches: [{ id: branch.id, name: branch.name, opens_at: '08:00:00', closes_at: '17:00:00' }],
  lanes: [
    { id: 'chair-1', kind: 'CHAIR', name: 'Synthetic Chair 1', branch_id: branch.id, color: null },
    { id: 'chair-2', kind: 'CHAIR', name: 'Synthetic Chair 2', branch_id: branch.id, color: null },
    { id: 'dentist-1', kind: 'DENTIST', name: 'Synthetic Dentist', branch_id: null, color: '#7c3aed' },
  ],
  blocks: [
    { id: 'appointment-1', starts_at: '2026-10-21T01:00:00Z', ends_at: '2026-10-21T01:30:00Z', status: 'CONFIRMED', branch_id: branch.id, patient_name: 'Ana Reyes', activity: 'Checkup', type_color: '#0e7c86', dentist_id: 'dentist-1', dentist_name: 'Synthetic Dentist', dentist_color: '#7c3aed', chair_id: 'chair-1', chair_name: 'Synthetic Chair 1' },
    { id: 'appointment-2', starts_at: '2026-10-21T02:00:00Z', ends_at: '2026-10-21T03:00:00Z', status: 'BOOKED', branch_id: branch.id, patient_name: 'Ben Santos', activity: 'Scaling', type_color: null, dentist_id: 'dentist-1', dentist_name: 'Synthetic Dentist', dentist_color: '#7c3aed', chair_id: 'chair-1', chair_name: 'Synthetic Chair 1' },
    { id: 'appointment-3', starts_at: '2026-10-21T01:30:00Z', ends_at: '2026-10-21T02:00:00Z', status: 'ARRIVED', branch_id: branch.id, patient_name: 'Cara Dela Cruz', activity: 'Filling', type_color: null, dentist_id: 'dentist-1', dentist_name: 'Synthetic Dentist', dentist_color: '#7c3aed', chair_id: 'chair-2', chair_name: 'Synthetic Chair 2' },
  ],
}
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
    } else if (path === '/api/workspace/day-board') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(dayBoard) })
    } else if (path.startsWith('/api/scheduling/calendar')) {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' })
    } else if (path.startsWith('/api/scheduling/queue')) {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' })
    } else if (path.startsWith('/api/scheduling/resources')) {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ dentists: [{ id: 'dentist-1', full_name: 'Synthetic Dentist' }], chairs: [{ id: 'chair-1', name: 'Synthetic Chair 1' }] }) })
    } else if (path.startsWith('/api/scheduling/types')) {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' })
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
      await page.getByRole('button', { name: 'Open account menu' }).click()
      await expect(page.getByText('My Profile').first()).toBeVisible()
      await page.getByRole('button', { name: 'Sign out' }).click()
      await expect(page).toHaveURL(/\/login$/)
    }
    await page.goto('/register-clinic')
    await expect(page.getByText('Clinic registration is currently unavailable.')).toBeVisible()
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
  await page.getByLabel('Clinic code').fill('synthetic-clinic')
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
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([
        { id: '44444444-4444-4444-8444-444444444444', email: 'staff@test.invalid', role: 'FRONT_DESK', branch_id: branch.id, created_at: '2026-10-21T02:00:00Z', expires_at: '2026-10-28T02:00:00Z', accepted_at: null, revoked_at: null },
        { id: '55555555-5555-4555-8555-555555555555', email: 'recent@test.invalid', role: 'DENTIST', branch_id: branch.id, created_at: '2026-10-20T09:15:00Z', expires_at: '2026-10-27T09:15:00Z', accepted_at: null, revoked_at: null },
      ]) })
    }
  })

  await page.goto('/login')
  await page.getByLabel('Clinic code').fill(clinic.slug)
  await page.evaluate(() => { (window as unknown as { __gsiCredential?: string }).__gsiCredential = 'synthetic-id-token:OWNER' })
  await page.getByRole('button', { name: 'Sign in with Google' }).click()
  await expect(page).toHaveURL(/\/workspace\//)
  await page.goto('/workspace/staff')
  const recentMeta = page.locator('.ws-staff-invitation-meta').first()
  await expect(recentMeta).toContainText('Invited')
  await expect(recentMeta).toContainText('Expires')
  await expect(recentMeta).toContainText('2026')
  await page.getByLabel('Email').fill('staff@test.invalid')
  await pickDropdown(page, 'Role', 'Front Desk')
  await expect(page.getByRole('combobox', { name: 'Role', exact: true })).toHaveText('Front Desk')
  await page.getByRole('button', { name: 'Create invitation' }).click()
  await expect(page.getByText('One-time invitation created')).toBeVisible()
  // The one-time link lands in an invitation action bar; buttons are now plain labels.
  await expect(page.locator('.ws-staff-link-ready')).toContainText('Link ready')
  await expect(page.getByRole('button', { name: 'Copy link' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Open', exact: true })).toBeVisible()
  await expect(page.getByLabel('One-time invitation token')).toHaveCount(0)
  expect(submitted).toEqual({ email: 'staff@test.invalid', role: 'FRONT_DESK', branchId: branch.id })
})

test('command center day board lays the clinic day out as resource lanes', async ({ page }) => {
  await installApiFixtures(page)
  await installGoogleScriptDouble(page)
  // Freeze the browser clock inside the fixture's clinic date so the now line, the up next strip
  // and the update stamp are deterministic no matter when the suite runs.
  await page.clock.setFixedTime(new Date('2026-10-21T08:30:00+08:00'))
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto('/login')
  await page.getByLabel('Clinic code').fill(clinic.slug)
  await page.evaluate(() => { (window as unknown as { __gsiCredential?: string }).__gsiCredential = 'synthetic-id-token:OWNER' })
  await page.getByRole('button', { name: 'Sign in with Google' }).click()
  await expect(page).toHaveURL(/\/workspace\//)
  await page.goto('/workspace/command-center')

  const board = page.getByRole('region', { name: 'Clinic day board' })
  await expect(board.getByRole('heading', { name: 'Clinic day timeline' })).toBeVisible()
  const laneLabel = (name: string) => board.locator('.ws-dayboard-lane-label').filter({ hasText: name })
  await expect(laneLabel('Synthetic Chair 1')).toBeVisible()
  await expect(laneLabel('Synthetic Chair 2')).toBeVisible()
  await expect(board.locator('.ws-dayboard-group-head strong').filter({ hasText: 'Synthetic Main Branch' })).toBeVisible()
  await expect(board.locator('.ws-chips button').filter({ hasText: 'Synthetic Main Branch' })).toBeVisible()

  // Blocks carry the clinic-local time, the visit and the provider in their accessible name.
  const checkup = board.getByRole('button', { name: /09:00 to 09:30, Checkup, Ana Reyes/ })
  await expect(checkup).toBeVisible()
  await expect(board.getByRole('button', { name: /10:00 to 11:00, Scaling, Ben Santos/ })).toBeVisible()
  await expect(board.getByRole('button', { name: /09:30 to 10:00, Filling, Cara Dela Cruz/ })).toBeVisible()

  // A longer visit is drawn wider than a shorter one on the same lane.
  const width = async (name: RegExp) => Number((await board.getByRole('link', { name }).evaluate(node => node.style.width)).replace('%', ''))
  expect(await width(/09:00 to 09:30, Checkup/)).toBeGreaterThan(0)
  expect(await width(/10:00 to 11:00, Scaling/)).toBeGreaterThan(await width(/09:00 to 09:30, Checkup/))

  // The board is the fixture's current clinic date, so the now line and up next strip are live.
  await expect(board.locator('.ws-dayboard-now').first()).toBeVisible()
  await expect(board.locator('.ws-dayboard-next li')).toHaveCount(3)
  await expect(board.locator('.ws-dayboard-next li').first()).toContainText('Ana Reyes')
  await expect(board.locator('.ws-dayboard-foot')).toContainText('Updated just now')

  // Provider lanes replace chair lanes on request.
  await board.getByRole('button', { name: 'Providers' }).click()
  await expect(laneLabel('Synthetic Dentist')).toBeVisible()
  await expect(laneLabel('Synthetic Chair 1')).toHaveCount(0)
  await board.getByRole('button', { name: 'Chairs' }).click()
  await expect(laneLabel('Synthetic Chair 1')).toBeVisible()

  // The list view keeps the same information without the grid.
  await board.getByRole('button', { name: 'List' }).click()
  await expect(board.getByRole('cell', { name: 'Ana Reyes' })).toBeVisible()
  await board.getByRole('button', { name: 'Timeline' }).click()
  await expect(board.getByRole('button', { name: /09:00 to 09:30, Checkup/ })).toBeVisible()

  // Appointment blocks open a details dialog without navigating away from the day board.
  await page.screenshot({ path: resolve(screenshotDir, 'desktop-command-center-day-board.png'), fullPage: true })
  await checkup.click()
  const appointmentDetails = page.getByRole('dialog', { name: 'Ana Reyes' })
  await expect(appointmentDetails).toBeVisible()
  await expect(appointmentDetails).toContainText('Checkup')
  await expect(page).toHaveURL(/\/workspace\/command-center$/)
  await page.getByRole('button', { name: 'Close appointment details' }).click()

  const dimensions = await page.evaluate(() => ({ viewport: window.innerWidth, document: document.documentElement.scrollWidth }))
  expect(dimensions.document).toBeLessThanOrEqual(dimensions.viewport + 1)
})

test('calendar day board tab reviews other dates through the scheduling filters', async ({ page }) => {
  await installApiFixtures(page)
  await installGoogleScriptDouble(page)
  await page.clock.setFixedTime(new Date('2026-10-21T08:30:00+08:00'))
  const boardRequests: string[] = []
  page.on('request', request => {
    const url = new URL(request.url())
    if (url.pathname === '/api/workspace/day-board') boardRequests.push(url.search)
  })
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto('/login')
  await page.getByLabel('Clinic code').fill(clinic.slug)
  await page.evaluate(() => { (window as unknown as { __gsiCredential?: string }).__gsiCredential = 'synthetic-id-token:OWNER' })
  await page.getByRole('button', { name: 'Sign in with Google' }).click()
  await expect(page).toHaveURL(/\/workspace\//)
  await page.goto('/workspace/calendar')
  await page.getByRole('tab', { name: 'Day board' }).click()

  const board = page.getByRole('region', { name: 'Clinic day board' })
  await expect(board.locator('.ws-dayboard-heading p').filter({ hasText: 'Wednesday, October 21' })).toBeVisible()
  await expect(board.locator('.ws-dayboard-lane-label').filter({ hasText: 'Synthetic Chair 1' })).toBeVisible()
  // Branch selection can be displayed as an interactive chip or a locked label.
  await expect(board.getByRole('group', { name: 'Branch filter' }).getByText('Synthetic Main Branch')).toBeVisible()

  // The scheduling filters drive the board: stepping a day refetches that clinic-local date.
  await expect.poll(() => boardRequests.some(query => query.includes('date=2026-10-21'))).toBe(true)
  await page.getByRole('button', { name: 'Next day' }).click()
  await expect.poll(() => boardRequests.some(query => query.includes('date=2026-10-22'))).toBe(true)
  await page.getByLabel('Date').fill('2026-10-24')
  await expect.poll(() => boardRequests.some(query => query.includes('date=2026-10-24'))).toBe(true)

  // Dentist and chair filters narrow the drawn blocks without a refetch.
  const blocksBefore = await board.locator('.ws-dayboard-block').count()
  await page.getByRole('combobox', { name: 'Chair filter' }).click()
  await page.getByRole('option', { name: 'Synthetic Chair 1' }).click()
  await expect(board.getByText(/2 of 3 appointments/)).toBeVisible()
  await expect(board.locator('.ws-dayboard-block')).toHaveCount(2)
  expect(blocksBefore).toBe(3)
  // Resetting the filter restores the full board.
  await page.getByRole('combobox', { name: 'Chair filter' }).click()
  await page.getByRole('option', { name: 'All chairs' }).click()
  await expect(board.locator('.ws-dayboard-block')).toHaveCount(3)
})
