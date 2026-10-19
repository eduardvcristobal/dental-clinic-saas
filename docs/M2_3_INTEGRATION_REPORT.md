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

## M2.5 verification (2026-10-19)
- One authenticated Chromium Playwright test exercised the real backend and PostgreSQL, created a synthetic patient, opened its Patient 360° record, and checked delayed rapid switching and a foreign-clinic deep link.
- The mocked UI suite passed 12/12 tests across Chromium, Firefox, and WebKit, including desktop, tablet, and mobile viewport layouts. These mocked tests are separate from the authenticated live-backend result.
- Java API integration and PostgreSQL tests passed cross-clinic patient/care access checks; foreign UUIDs return 404 and Front Desk medical-history access returns 403.
- The workspace shows only the selected patient’s clinic record; the incomplete tabs remain previews. No fictional clinical content is attached to the live synthetic patient.

The live E2E uses ephemeral test signing keys and synthetic account identities, not real Google credentials. Hosted CI was not accessible from this run; see the M2.5 acceptance report. M2 remains open pending hosted CI evidence.
