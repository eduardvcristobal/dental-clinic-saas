# M2.3 — Patient 360° integration and regression report

## Changes delivered

- Replaced the standalone fictional Patient 360° chart with authenticated patient search, selection and direct clinic-scoped patient detail retrieval.
- Linked selected patients in Patient Registry to Patient 360° via `?patient=<uuid>`.
- Reused M2.2 PatientCare components to display/update medical history, guardian contacts, unsigned consent requests and administrative acknowledgements, and review exact-name/DOB duplicate candidates.
- Removed fictitious clinical findings, invoices, treatment plans and SOAP notes from the context of a real selected patient; the unfinished tabs are explicitly marked Coming soon.

## Safety and privacy decisions

- The patient ID in the URL is a selector only; API authorization and PostgreSQL RLS must independently enforce tenant isolation.
- An administrative consent acknowledgement is **not** a legally signed informed consent.
- Guardian contact details **do not** establish legal guardian authority, even for minors.
- Identity matches are advisory only; no merge/overwrite pathway has been implemented.
- Existing M2.2 clinical authorization checks restrict medical history to OWNER/ADMIN/DENTIST; Front Desk remains limited to appropriate nonclinical data.
- Use synthetic identities only until clinical privacy and release acceptance are completed.

## Verification performed in this environment

- Git source diff and source file structure reviewed.
- **NOT EXECUTED:** Java 17 Maven compile/tests, PostgreSQL migrations V1–V15 and restricted-role isolation, npm production build/lint/Vitest, authenticated Playwright E2E. The environment lacks Maven, Java 17, PostgreSQL and installed frontend dependencies.

## Required acceptance suite for local agent

1. On a clean PostgreSQL 16/18 test database, run Flyway V1–V15 and upgrade tests; verify guardian and consent RLS with restricted role across two clinics.
2. On JDK 17 execute `mvn clean verify` and check medical history denial to FRONT_DESK and cross-clinic UUID requests on all patient-care endpoints.
3. In frontend execute `npm ci`, `npm run lint`, `npm run test`, `npm run build`, and `npm run test:e2e` with authenticated synthetic test accounts.
4. Browser: search/select a patient, deep link to `/workspace/patient-360?patient=<uuid>`, reload, edit/history if permitted, switch tabs, change patients, verify Front Desk never sees clinical details, verify unknown/foreign UUID does not render previous data.
5. Validate minor/guardian/contact interactions and consent state transitions; leave signing and legal authority explicitly incomplete.
6. Update `PROJECT_STATUS.md` with actual counts, fix failures, and keep M2.3 open until evidence is available.
