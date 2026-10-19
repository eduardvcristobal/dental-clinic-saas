import { expect, test } from '@playwright/test'

const backend = process.env.M25_BACKEND_URL ?? 'http://127.0.0.1:8081'
const jwks = process.env.M25_JWKS_URL ?? 'http://127.0.0.1:17890'
const frontend = 'http://127.0.0.1:' + (process.env.M25_FRONTEND_PORT ?? '5174')

async function authenticate(email: string, request: import('@playwright/test').APIRequestContext) {
  const identity = await request.post(`${jwks}/token`, { data: { email } })
  expect(identity.ok()).toBeTruthy()
  const idToken = (await identity.json()).idToken as string
  const login = await request.post(`${backend}/api/auth/google`, {
    headers: { Origin: frontend },
    data: { clinicSlug: email === 'other-owner@m25.test' ? 'm25-live-b' : 'm25-live-a', idToken },
  })
  expect(login.status()).toBe(200)
  const session = await login.json()
  const cookie = login.headers()['set-cookie']?.match(/dental_refresh=([^;]+)/)?.[1]
  expect(cookie).toBeTruthy()
  return { accessToken: session.accessToken as string, cookie: cookie as string }
}

test('authenticated owner completes patient registry and care workflows against live PostgreSQL backend', async ({ page, request }) => {
  const owner = await authenticate('owner@m25.test', request)
  await page.context().addCookies([{
    name: 'dental_refresh', value: owner.cookie, domain: '127.0.0.1', path: '/api/auth',
    httpOnly: true, secure: false, sameSite: 'Strict',
  }])
  await page.goto('/workspace/patients')
  await expect(page.getByRole('heading', { name: 'Patients' })).toBeVisible()

  await page.getByRole('button', { name: 'Register patient' }).first().click()
  await page.getByLabel('First name').fill('Live Synthetic')
  await page.getByLabel('Last name').fill('M2.5 Patient')
  await page.getByLabel('Date of birth').fill('2015-04-03')
  await page.getByRole('button', { name: 'Register patient' }).last().click()
  await expect(page.getByText(/Patient registered: P-000001/)).toBeVisible()
  await expect(page.getByText(/Minors need a separately verified authorized decision-maker/)).toBeVisible()
  await expect(page.getByText(/does not establish legal authority/)).toBeVisible()

  const list = await request.get(`${backend}/api/patients?q=Live%20Synthetic&limit=10`, {
    headers: { Authorization: `Bearer ${owner.accessToken}` },
  })
  expect(list.status()).toBe(200)
  const patients = await list.json() as Array<{ id: string; chart_no: string }>
  expect(patients).toHaveLength(1)
  expect(patients[0].chart_no).toBe('P-000001')
  const patientId = patients[0].id

  await page.getByRole('button', { name: 'Register patient' }).first().click()
  await page.getByLabel('First name').fill('Live Synthetic')
  await page.getByLabel('Last name').fill('M2.5 Patient')
  await page.getByLabel('Date of birth').fill('2015-04-03')
  await page.getByRole('button', { name: 'Register patient' }).last().click()
  await expect(page.getByText(/Possible duplicate found/)).toBeVisible()
  const unchanged = await request.get(`${backend}/api/patients?q=Live%20Synthetic&limit=10`, {
    headers: { Authorization: `Bearer ${owner.accessToken}` },
  })
  expect(await unchanged.json()).toHaveLength(1)
  await page.getByRole('button', { name: /P-000001 · Live Synthetic M2.5 Patient/ }).click()

  await page.getByRole('button', { name: /Live Synthetic M2.5 Patient/ }).click()
  await page.getByLabel('Guardian name').fill('Synthetic Guardian')
  await page.getByLabel('Relationship').fill('Parent')
  await page.getByRole('button', { name: 'Add guardian' }).click()
  await expect(page.getByText('Synthetic Guardian · Parent · Primary')).toBeVisible()

  await page.getByLabel('Form type').fill('Synthetic test consent request')
  await page.getByRole('button', { name: 'Record unsigned consent request' }).click()
  await expect(page.getByText(/UNSIGNED/)).toBeVisible()
  await expect(page.getByText(/not a legal signature/)).toBeVisible()
  await page.getByRole('button', { name: 'Mark administratively acknowledged' }).click()
  await expect(page.getByText(/ACKNOWLEDGED/)).toBeVisible()
  await page.waitForLoadState('networkidle')

  await page.getByLabel('Allergies').fill('synthetic pollen')
  await expect(page.getByLabel('Allergies')).toHaveValue('synthetic pollen')
  const enteredHistory = await page.getByLabel('Allergies').evaluate((el) => {
    const form = el.closest('form')
    return { value: (el as HTMLTextAreaElement).value, allergy: new FormData(form!).get('allergies') }
  })
  expect(enteredHistory).toEqual({ value: 'synthetic pollen', allergy: 'synthetic pollen' })
  let historyRequestBody = ''
  page.on('request', (req) => {
    if (req.method() === 'PUT' && req.url().endsWith('/medical-history')) historyRequestBody = req.postData() ?? ''
  })
  await page.getByRole('button', { name: 'Save medical history' }).click()
  await expect.poll(() => historyRequestBody).toContain('synthetic pollen')
  await expect(page.getByText('Recorded allergies: synthetic pollen')).toBeVisible()
  await page.getByRole('link', { name: /Open full Patient 360/ }).click()
  await expect(page.getByRole('heading', { name: 'Patient 360°' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Live Synthetic M2.5 Patient' })).toBeVisible()

  await page.goto('/workspace/patient-360')
  await page.getByLabel('Search clinic registry').fill('Live Synthetic')
  await expect(page.getByRole('button', { name: /Live Synthetic M2.5 Patient/ })).toBeVisible()
  await page.route('${backend}/api/patients/${patientId}', async (route) => {
    await new Promise((resolve) => setTimeout(resolve, 800))
    await route.continue()
  })
  const delayedPatient = page.waitForResponse((response) => response.url() === backend + '/api/patients/' + patientId)
  await page.getByRole('button', { name: /Live Synthetic M2.5 Patient/ }).click()
  await page.getByRole('button', { name: /Return to patient search/ }).click()
  await page.getByLabel('Search clinic registry').fill('Alternate Synthetic')
  await page.getByRole('button', { name: /Alternate Synthetic/ }).click()
  await expect(page.getByRole('heading', { name: 'Alternate Synthetic' })).toBeVisible()
  await delayedPatient
  await expect(page.getByRole('heading', { name: 'Live Synthetic M2.5 Patient' })).toHaveCount(0)

  await page.goto('/workspace/patient-360?patient=25000000-0000-4000-8000-000000000031')
  await expect(page.getByRole('alert').getByText('Not Found')).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Alternate Synthetic' })).toHaveCount(0)

  const frontDesk = await authenticate('frontdesk@m25.test', request)
  const forbidden = await request.get(`${backend}/api/patients/${patientId}/medical-history`, {
    headers: { Authorization: `Bearer ${frontDesk.accessToken}` },
  })
  expect(forbidden.status()).toBe(403)
  const otherClinic = await authenticate('other-owner@m25.test', request)
  const isolated = await request.get(`${backend}/api/patients/${patientId}`, {
    headers: { Authorization: `Bearer ${otherClinic.accessToken}` },
  })
  expect(isolated.status()).toBe(404)

  await page.goto('/workspace/patients')
  await page.getByLabel('Search by name, chart number or phone').fill('Live Synthetic')
  await expect(page.getByRole('button', { name: /Live Synthetic M2.5 Patient/ })).toBeVisible()
  await page.getByRole('button', { name: /Live Synthetic M2.5 Patient/ }).click()
  await page.getByRole('button', { name: 'Edit demographics' }).click()
  await page.getByLabel('Last name').fill('M2.5 Updated')
  await page.getByRole('button', { name: 'Save changes' }).click()
  await expect(page.getByText(/Patient updated: P-000001/)).toBeVisible()

  page.once('dialog', (dialog) => dialog.accept())
  await page.getByRole('button', { name: 'Archive patient' }).click()
  await expect(page.getByText('Patient archived')).toBeVisible()
  const archived = await request.get(`${backend}/api/patients/${patientId}`, {
    headers: { Authorization: `Bearer ${owner.accessToken}` },
  })
  expect(archived.status()).toBe(404)
})
