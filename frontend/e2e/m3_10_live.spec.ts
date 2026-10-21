import { expect, test } from '@playwright/test'
import { pickDropdown } from './dropdown'

/** M3.10 live scheduling workflow: real Spring Boot backend + fresh disposable PostgreSQL database.
 * All identities are synthetic local test tokens. */

const backend = process.env.M3_10_BACKEND_URL ?? 'http://127.0.0.1:8083'
const jwks = process.env.M3_10_JWKS_URL ?? 'http://127.0.0.1:17892'
const frontend = 'http://127.0.0.1:' + (process.env.M3_10_FRONTEND_PORT ?? '5176')

const branchA = '31000000-0000-4000-8000-000000000011'
const patientA = '31000000-0000-4000-8000-000000000051'
const dentistFix = '31000000-0000-4000-8000-000000000031'
const chairFix = '31000000-0000-4000-8000-000000000032'

const upcomingMonday = (() => {
  const d = new Date()
  d.setDate(d.getDate() + 1)
  while (d.getDay() !== 1) d.setDate(d.getDate() + 1)
  return d.toISOString().slice(0, 10)
})()

async function login(email: 'owner@m25.test' | 'frontdesk@m25.test' | 'dentist@m25.test' | 'other-owner@m25.test', request: import('@playwright/test').APIRequestContext) {
  const identity = await request.post(`${jwks}/token`, { data: { email } })
  expect(identity.ok()).toBeTruthy()
  const idToken = (await identity.json()).idToken as string
  const slug = email.startsWith('other-owner') ? 'm310-live-b' : 'm310-live-a'
  const response = await request.post(`${backend}/api/auth/google`, {
    headers: { Origin: frontend },
    data: { clinicSlug: slug, idToken },
  })
  expect(response.status()).toBe(200)
  const ses = await response.json()
  const cookie = response.headers()['set-cookie']?.match(/dental_refresh=([^;]+)/)?.[1]
  expect(cookie).toBeTruthy()
  return { accessToken: ses.accessToken as string, cookie: cookie as string }
}

async function signInAs(page: import('@playwright/test').Page, email: 'owner@m25.test' | 'frontdesk@m25.test' | 'dentist@m25.test' | 'other-owner@m25.test', request: import('@playwright/test').APIRequestContext) {
  const creds = await login(email, request)
  await page.context().clearCookies()
  await page.context().addCookies([{
    name: 'dental_refresh', value: creds.cookie, domain: '127.0.0.1', path: '/api/auth',
    httpOnly: true, secure: false, sameSite: 'Strict',
  }])
  return creds
}

test('owner/front desk/dentist complete the scheduling acceptance workflow against live PostgreSQL', async ({ page, request }) => {
  const owner = await signInAs(page, 'owner@m25.test', request)

  await page.goto('/workspace/calendar')
  await expect(page.getByRole('heading', { name: 'Appointment Calendar' })).toBeVisible()

  // 1. Owner configures an appointment type.
  await page.getByLabel('New appointment type').fill('Live 30 Checkup')
  await page.getByLabel('Duration (minutes)').fill('30')
  await page.getByRole('button', { name: 'Add type' }).click()
  await expect(page.getByText('Appointment type created')).toBeVisible()

  // 2. Owner configures dentist availability (Monday 09:00-17:00).
  {
    const cfg = page.locator('section', { has: page.getByRole('heading', { name: 'Dentist weekly availability' }) })
    await pickDropdown(cfg, 'Dentist', dentistFix)
    await pickDropdown(cfg, 'Weekday', 'Monday')
    await cfg.getByLabel('From', { exact: true }).fill('09:00', { force: true })
    await cfg.getByLabel('To', { exact: true }).fill('17:00', { force: true })
    await cfg.getByRole('button', { name: 'Save hours' }).click()
  }
  await expect(page.getByText('Availability saved')).toBeVisible()

  // 3. Owner blocks the chair for maintenance late Monday.
  {
    await pickDropdown(page, 'Chair', chairFix)
    await page.getByLabel('Blocked from').fill(`${upcomingMonday}T16:00`)
    await page.getByLabel('Reason', { exact: true }).last().fill('maintenance')
    await page.getByRole('button', { name: 'Save chair block' }).click()
  }
  await expect(page.getByText('Chair block saved')).toBeVisible()

  // 4. Front desk searches a patient and books the Monday 10:00 slot.
  const front = await signInAs(page, 'frontdesk@m25.test', request)
  await page.goto('/workspace/calendar')
  await page.getByLabel("Date", { exact: true }).fill(upcomingMonday, { force: true })
  await page.getByLabel('Find patient').fill('Live Fix')
  await page.getByTestId('booking-patient').selectOption(patientA, { force: true })
  await pickDropdown(page, 'Dentist', dentistFix)
  await pickDropdown(page, 'Chair', chairFix)
  await page.getByTestId('booking-start').fill(`${upcomingMonday}T10:00`, { force: true })
  await page.getByRole('button', { name: 'Book appointment' }).click()
  await expect(page.getByText('Appointment created')).toBeVisible()
  await expect(page.getByTestId('appointment-row')).toHaveCount(1)
  await page.getByTestId('appointment-row').first().getByRole('button', { name: 'Confirm' }).click()
  await expect(page.getByText('Appointment: CONFIRMED')).toBeVisible()

  // 5. The dentist signs in and sees the appointment.
  await signInAs(page, 'dentist@m25.test', request)
  await page.goto('/workspace/calendar')
  await page.getByLabel("Date", { exact: true }).fill(upcomingMonday, { force: true })
  await expect(page.getByTestId('appointment-row')).toHaveCount(1)
  await expect(page.getByRole("cell", { name: "Live Fix", exact: true })).toBeVisible()

  // 6. Front desk opens details with lifecycle history and reschedules to Monday 11:00.
  await signInAs(page, 'frontdesk@m25.test', request)
  await page.goto('/workspace/calendar')
  await page.getByLabel("Date", { exact: true }).fill(upcomingMonday, { force: true })
  await page.getByTestId('appointment-row').first().getByRole('button', { name: 'Cancel' }).click()
  await expect(page.getByTestId('appointment-details')).toBeVisible()
  await page.getByTestId('booking-resched-start').fill(`${upcomingMonday}T11:00`, { force: true })
  await page.getByTestId('booking-resched-reason').fill('dentist requested', { force: true })
  await page.getByRole('button', { name: 'Reschedule appointment' }).click()
  await expect(page.getByText('Appointment rescheduled')).toBeVisible()
  await expect(page.getByText(/RESCHEDULED/).first()).toBeVisible()

  // 7. Patient arrives and is checked in; the dentist completes the slot.
  await page.getByTestId('appointment-row').first().getByRole('button', { name: 'Check in' }).click()
  await expect(page.getByText('Appointment: ARRIVED')).toBeVisible()
  await page.getByTestId('appointment-row').first().getByRole('button', { name: 'In chair' }).click()
  await expect(page.getByText('Appointment: IN_CHAIR')).toBeVisible()
  await page.getByTestId('appointment-row').first().getByRole('button', { name: 'Complete' }).click()
  await expect(page.getByText('Appointment: COMPLETED')).toBeVisible()

  // 8. A conflicting booking for the occupied slot is rejected.
  await page.getByLabel('Find patient').fill('Live Fix')
  await page.getByTestId('booking-patient').selectOption(patientA, { force: true })
  await pickDropdown(page, 'Dentist', dentistFix)
  await page.getByTestId('booking-start').fill(`${upcomingMonday}T11:00`, { force: true })
  await page.getByRole('button', { name: 'Book appointment' }).click()
  await expect(page.getByText(/Slot already booked|conflict/i).first()).toBeVisible({ timeout: 30_000 })

  // 9. A separate booking can be cancelled with a recorded reason.
  await page.getByTestId('booking-start').fill(`${upcomingMonday}T12:00`, { force: true })
  await page.getByRole('button', { name: 'Book appointment' }).click()
  await expect(page.getByText('Appointment created')).toBeVisible()
  const bookedRow = page.getByTestId('appointment-row').filter({ hasText: 'BOOKED' })
  await expect(bookedRow).toHaveCount(1)
  await bookedRow.getByRole('button', { name: 'Cancel' }).click()
  const details = page.getByTestId('appointment-details')
  await expect(details).toBeVisible()
  await details.getByLabel('Reason', { exact: true }).fill('patient requested cancellation')
  await details.getByRole('button', { name: 'Cancel appointment' }).click()
  await expect(page.getByText('Appointment cancelled')).toBeVisible()

  // The maintenance block prevents a booking on that chair.
  await pickDropdown(page, 'Chair', chairFix)
  await page.getByTestId('booking-start').fill(`${upcomingMonday}T16:00`, { force: true })
  await page.getByRole('button', { name: 'Book appointment' }).click()
  await expect(page.getByText(/Chair unavailable/i).first()).toBeVisible()

  // 10. A second clinic cannot view or modify the appointment.
  {
    const other = await login('other-owner@m25.test', request)
    const range = `from=${upcomingMonday}T00:00:00Z&to=${upcomingMonday}T23:59:59Z`
    const ownerCalendar = await request.get(`${backend}/api/scheduling/calendar?branchId=${branchA}&${range}`, { headers: { Authorization: `Bearer ${owner.accessToken}` } })
    expect(ownerCalendar.status()).toBe(200)
    const appointments = (await ownerCalendar.json()) as Array<{ id: string; status: string }>
    const completed = appointments.find(a => a.status === 'COMPLETED')
    expect(completed).toBeDefined()
    const foreignBranch = await request.get(`${backend}/api/scheduling/calendar?branchId=${branchA}&${range}`, { headers: { Authorization: `Bearer ${other.accessToken}` } })
    expect(foreignBranch.status()).toBe(200)
    expect(await foreignBranch.json()).toEqual([])
    const foreignAppointment = await request.get(`${backend}/api/scheduling/appointments/${completed!.id}`, { headers: { Authorization: `Bearer ${other.accessToken}` } })
    expect(foreignAppointment.status()).toBe(404)
    const foreignUpdate = await request.post(`${backend}/api/scheduling/appointments/${completed!.id}/status`, {
      headers: { Authorization: `Bearer ${other.accessToken}`, Origin: frontend },
      data: { status: 'CANCELLED', reason: 'cross-clinic attempt' },
    })
    expect(foreignUpdate.status()).toBe(404)
    const ownBranch = await request.get(`${backend}/api/scheduling/calendar?branchId=31000000-0000-4000-8000-000000000012&${range}`, { headers: { Authorization: `Bearer ${other.accessToken}` } })
    expect(ownBranch.status()).toBe(200)
    expect(await ownBranch.json()).toEqual([])
  }

  // 11. Waitlist and notification-outbox records reflect the expected changes.
  {
    const wl = await request.post(`${backend}/api/scheduling/waitlist`, {
      headers: { Authorization: `Bearer ${front.accessToken}`, Origin: frontend },
      data: { patientId: patientA, branchId: branchA, preferredAfter: '2026-01-01T00:00:00Z', preferredBefore: '2027-12-31T00:00:00Z' },
    })
    expect(wl.status()).toBe(201)
    const foreign = await request.post(`${backend}/api/scheduling/waitlist`, {
      headers: { Authorization: `Bearer ${(await login('other-owner@m25.test', request)).accessToken}`, Origin: frontend },
      data: { patientId: patientA, branchId: '31000000-0000-4000-8000-000000000012', preferredAfter: '2026-01-01T00:00:00Z', preferredBefore: '2027-12-31T00:00:00Z' },
    })
    expect(foreign.status()).toBe(400)
    const outbox = await request.get(`${backend}/api/scheduling/appointment-notifications`, { headers: { Authorization: `Bearer ${owner.accessToken}` } })
    expect(outbox.status()).toBe(200)
    const rows = (await outbox.json()) as Array<{ event_type: string; state: string }>
    const kinds = rows.map(r => r.event_type)
    expect(kinds).toContain('BOOKED')
    expect(kinds).toContain('RESCHEDULED')
    expect(kinds).toContain('CONFIRMED')
    expect(kinds).toContain('CANCELLED')
    for (const row of rows) expect(row.state).toBe('PENDING') // delivery stays PENDING until M8; nothing is sent
  }
})
