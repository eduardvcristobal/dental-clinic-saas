import { expect, test, type Page } from '@playwright/test'
import { dropdownText, pickDropdown } from './dropdown'

/** M3.10 mocked browser regression for the scheduling calendar. Every network call is intercepted;
 * no request reaches a real backend. Complements the live-backend E2E in m3_10_live.spec.ts. */

const clinic = { id: '11111111-1111-4111-8111-111111111111', slug: 'synthetic-clinic', name: 'Synthetic Demo Clinic' }
const branchA = { id: '22222222-2222-4222-8222-222222222202', name: 'Synthetic Branch One' }
const dentistAda = { id: '33333333-3333-4333-8333-333333331111' }
const dentistAbel = { id: '33333333-3333-4333-8333-333333332222' }
const chair = { id: '44444444-4444-4444-8444-444444441111' }
const patient = { id: '55555555-5555-4555-8555-555555551111', first_name: 'Sunny', last_name: 'Synthetic', chart_no: 'M3-000001' }

const session = {
  user: { id: 'user-1', email: 'frontdesk@test.invalid', name: 'Front Desk', role: 'FRONT_DESK', platformAdmin: false },
  clinic: clinic,
  accessToken: 'synthetic-access-token',
}
const appointmentRow = {
  id: '66666666-6666-4666-8666-666666661111',
  branch_id: branchA.id, patient_id: patient.id,
  first_name: patient.first_name, last_name: patient.last_name,
  dentist_id: dentistAda.id, dentist_name: 'Dr. Ada',
  chair_id: chair.id, appointment_type_id: null,
  starts_at: '2026-10-26T02:00:00.000Z', ends_at: '2026-10-26T02:30:00.000Z',
  status: 'BOOKED', reason: 'Synthetic checkup', version: 0,
}
const secondRow = {
  ...appointmentRow, id: '66666666-6666-4666-8666-666666662222',
  dentist_id: dentistAbel.id, dentist_name: 'Dr. Abel', chair_id: null,
  starts_at: '2026-10-26T03:00:00.000Z', ends_at: '2026-10-26T03:30:00.000Z', status: 'CONFIRMED',
}

const backendOnly = (url: URL) => url.pathname.startsWith('/api/')

async function installScheduling(page: Page, options: { events?: unknown[]; error?: boolean } = {}) {
  await page.route(backendOnly, async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/auth/refresh') return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(session) })
    if (options.error) { await route.fulfill({ status: 500, contentType: 'application/json', body: '{"error":"synthetic"}' }); return }
    if (path === '/api/clinic-management/branches') return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([branchA]) })
    if (path.startsWith('/api/scheduling/resources')) return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ dentists: [{ id: dentistAda.id, full_name: 'Dr. Ada' }, { id: dentistAbel.id, full_name: 'Dr. Abel' }], chairs: [{ id: chair.id, name: 'Chair 1' }] }) })
    if (path.startsWith('/api/scheduling/types')) return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([{ id: 'type-1', name: 'Checkup', default_minutes: 30, active: true }]) })
    if (path.startsWith('/api/scheduling/calendar')) return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(options.events ?? [appointmentRow, secondRow]) })
    if (path.startsWith('/api/scheduling/queue')) return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([]) })
    if (path.startsWith('/api/scheduling/appointments/66666666-6666-4666-8666-666666662222/events')) return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([{ id: 'e1', event_type: 'BOOKED', old_status: null, new_status: 'BOOKED', old_starts_at: null, new_starts_at: appointmentRow.starts_at, reason: null, created_at: '2026-10-20T00:00:00Z' }]) })
    if (path.endsWith('/status') || path.endsWith('/reschedule')) {
      const input = route.request().postDataJSON() as { status?: string }
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ ...appointmentRow, status: input?.status ?? 'CONFIRMED' }) })
    }
    if (path.startsWith('/api/scheduling/appointments/')) return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(path.includes(secondRow.id) ? secondRow : appointmentRow) })
    await route.fulfill({ status: 404, contentType: 'application/json', body: '{}' })
  })
}

test.beforeEach(async ({ page }) => { await installScheduling(page); await page.goto('/workspace/calendar') })

test('day view renders branch, dentist filter and appointment rows', async ({ page }) => {
  await expect(page.getByRole('heading', { name: 'Appointment Calendar' })).toBeVisible()
  await expect(page.getByTestId('appointment-row')).toHaveCount(2)
  await pickDropdown(page, 'Dentist filter', 'Dr. Ada')
  await expect(page.getByTestId('appointment-row')).toHaveCount(1)
  await pickDropdown(page, 'Dentist filter', 'All dentists')
  await pickDropdown(page, 'Chair filter', 'Chair 1')
  await expect(page.getByTestId('appointment-row')).toHaveCount(1)
  await pickDropdown(page, 'Chair filter', 'All chairs')
  await expect(page.getByTestId('appointment-row')).toHaveCount(2)
})

test('week and month shifts fetch larger calendar windows', async ({ page }) => {
  await page.getByRole('button', { name: 'Week' }).click()
  await expect(page.getByTestId('appointment-row')).toHaveCount(2)
  await page.getByRole('button', { name: 'Month' }).click()
  await expect(page.getByTestId('appointment-row')).toHaveCount(2)
})

test('empty calendar shows an explicit empty state', async ({ page }) => {
  await installScheduling(page, { events: [] })
  await page.goto('/workspace/calendar')
  await expect(page.getByText('No appointments in selected range.')).toBeVisible()
})

test('calendar API failure surfaces a status message and loading state', async ({ page }) => {
  await installScheduling(page, { error: true })
  await page.goto('/workspace/calendar')
  await expect(page.getByText(/synthetic|500/i).first()).toBeVisible()
})

test('appointment details panel shows lifecycle history and close button', async ({ page }) => {
  await page.getByTestId('appointment-row').nth(1).getByRole('button', { name: 'Cancel' }).click()
  const details = page.getByTestId('appointment-details')
  await expect(details).toBeVisible()
  await expect(details.getByRole('heading', { name: 'Lifecycle history' })).toBeVisible()
  await expect(details.locator('li')).toHaveCount(1)
  await details.getByRole('button', { name: 'Close details' }).click()
  await expect(details).not.toBeVisible()
})

test('cancelling an appointment announces the outcome in a toast', async ({ page }) => {
  await page.getByTestId('appointment-row').nth(1).getByRole('button', { name: 'Cancel' }).click()
  const details = page.getByTestId('appointment-details')
  await expect(details).toBeVisible()
  await details.getByRole('button', { name: 'Cancel appointment' }).click()
  const notifications = page.getByRole('region', { name: 'Notifications' })
  await expect(notifications.getByText('Appointment cancelled')).toBeVisible()
  await notifications.getByRole('button', { name: 'Dismiss notification' }).click()
  await expect(notifications.getByText('Appointment cancelled')).toHaveCount(0)
})

test('a failed appointment update announces the error in a toast', async ({ page }) => {
  await page.route(backendOnly, async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/status')) return route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ detail: 'Synthetic failure' }) })
    return route.fallback()
  })
  await page.getByTestId('appointment-row').nth(1).getByRole('button', { name: 'Cancel' }).click()
  const details = page.getByTestId('appointment-details')
  await expect(details).toBeVisible()
  await details.getByRole('button', { name: 'Cancel appointment' }).click()
  const notifications = page.getByRole('region', { name: 'Notifications' })
  const failure = notifications.getByText('Synthetic failure')
  await expect(failure).toBeVisible()
  await expect(failure).not.toContainText('ApiError')
})

test('check-in workspace shows the empty queue state', async ({ page }) => {
  await page.goto('/workspace/check-in')
  await expect(page.getByRole('heading', { name: 'Patient arrival & queue' })).toBeVisible()
  await expect(page.getByText('No checked-in patients.')).toBeVisible()
})

test('book appointment dropdowns list their options and keep the chosen value', async ({ page }) => {
  await page.getByRole('tab', { name: 'Book appointment' }).click()
  const dentist = page.getByTestId('booking-dentist')
  await expect(dentist).toHaveText('Select dentist')
  await dentist.click()
  const dentistListbox = page.getByRole('listbox', { name: 'Dentist' })
  await expect(dentistListbox).toBeVisible()
  await expect(dentistListbox.getByRole('option')).toHaveCount(2)
  await dentistListbox.getByRole('option', { name: 'Dr. Abel' }).click()
  await expect(dentist).toHaveText('Dr. Abel')
  await expect(dentistListbox).toHaveCount(0)

  const chair = page.getByTestId('booking-chair')
  await chair.click()
  await page.getByRole('listbox', { name: 'Chair' }).getByRole('option', { name: 'Chair 1' }).click()
  await expect(chair).toHaveText('Chair 1')

  const type = page.getByTestId('booking-type')
  await expect(type).toHaveText('Standard (30 min)')
  await type.click()
  await page.getByRole('listbox', { name: 'Appointment type' }).getByRole('option', { name: /Checkup/ }).click()
  await expect(type).toHaveText('Checkup')
  expect(await dropdownText(page, 'Appointment type')).toBe('Checkup')
})

test('dropdown opens with the keyboard and selects with arrows and enter', async ({ page }) => {
  await page.getByRole('tab', { name: 'Book appointment' }).click()
  const dentist = page.getByTestId('booking-dentist')
  await dentist.focus()
  await page.keyboard.press('ArrowDown')
  await expect(page.getByRole('listbox', { name: 'Dentist' })).toBeVisible()
  await page.keyboard.press('ArrowDown')
  await page.keyboard.press('Enter')
  await expect(dentist).toHaveText('Dr. Abel')
  await expect(page.getByRole('listbox', { name: 'Dentist' })).toHaveCount(0)
})

test('dropdown closes on escape and on an outside click without changing the value', async ({ page }) => {
  await page.getByRole('tab', { name: 'Book appointment' }).click()
  const dentist = page.getByTestId('booking-dentist')
  await dentist.click()
  await expect(page.getByRole('listbox', { name: 'Dentist' })).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('listbox', { name: 'Dentist' })).toHaveCount(0)
  await expect(dentist).toHaveText('Select dentist')
  await dentist.click()
  await page.getByRole('heading', { name: 'Book appointment' }).click()
  await expect(page.getByRole('listbox', { name: 'Dentist' })).toHaveCount(0)
})
