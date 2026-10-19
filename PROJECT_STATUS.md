# Dental Clinic SaaS — Project Status

**Last updated:** 2026-10-19  
**Authoritative baseline:** M0.3 working repository plus M0.4 validation fixes
**Backend:** Java **17 LTS**, Spring Boot 3.3.5, PostgreSQL, Flyway  
**Frontend:** React, TypeScript, Vite  
**Overall status:** M0.4 local gates passed; **CI/provider verification remains; not pilot-ready**.

## Progress conventions

- `[x]` Completed and supported by reviewed source or recorded validation.
- `[ ]` Not complete or not verified. Never infer feature completion from an entity, menu, or scaffold.
- Mark a milestone complete only when its acceptance criteria and relevant tests pass with actual evidence.
- Preserve dated findings, blockers, and commits in this file.
- Do not put patient data, access tokens, passwords, or clinic secrets in docs or fixtures.

## Milestone roadmap

| ID | Milestone | Status | Acceptance gate |
|---|---|---|---|
| M0 | Foundation audit & setup | **In progress — M0.4 local validation passed; CI/provider checks open** | Java 17, local PG migration/RLS/auth tests, frontend checks passed; hosted CI and browser/provider verification pending |
| M1 | Clinics, branches, dentists, chairs, staff | Not started | Complete tenant/branch onboarding, permissions and CRUD with tests |
| M2 | Patients, medical history, consent, search | Not started | Registration to searchable protected patient chart |
| M3 | Multi-dentist/chair scheduling | Not started | Appointments, statuses, waitlist, conflict prevention |
| M4 | Odontogram | Not started | Adult/primary tooth surfaces, statuses, historical entries |
| M5 | Visits and clinical documentation | Not started | SOAP notes, signed addenda, files, prescriptions |
| M6 | Treatment plans | Not started | Procedures, phases, estimates, acceptance and completion |
| M7 | Billing and collections | Not started | Invoices, partial/installment payments, ledger, receipts |
| M8 | Reminders and recalls | Not started | Configurable notifications, reply handling and opt-out |
| M9 | Reports | Not started | Production, collections, no-shows, balances, new patients |
| M10 | Production and security hardening | Not started | Tenant isolation, audit, restore drill, end-to-end regression |
| M11 | Single-clinic pilot | Not started | Clinic operates one full week of live appointments and payments |

## M0.4 delivery status (2026-10-19)

### M0.4.1 — Java 17 runtime validation
- [x] OpenJDK 17.0.20 clean compile, 13 backend tests, JAR package/repackage.
- [x] Spring Boot started on a clean local PostgreSQL database; Hibernate validation passed; restart revalidated migrations.

### M0.4.2 — Database migration validation
- [x] Fresh local PostgreSQL migration V1–V10 succeeded; V1–V9 unchanged; migration history/checksums recorded.
- [x] Hibernate schema validation, 27 forced-RLS tenant tables, 48 composite same-clinic FKs and restricted-role RLS regressions passed.
- [ ] Hosted PostgreSQL 16 workflow has not run yet.

### M0.4.3 — Authentication integration
- [x] Actual HTTP tests cover Google token signature/issuer/audience/expiry/email verification, active/unknown/inactive staff, clinic mismatch, JWT tampering/expiry, refresh rotation/replay/concurrency, logout, cookies and Origin checks using local test signing keys.
- [ ] Live Google JWKS/browser sign-in not run; production provider behavior remains unverified.

### M0.4.4 — Multi-tenant isolation
- [x] Restricted `NOSUPERUSER NOBYPASSRLS` role verified; cross-clinic read/update/delete/spoof and relationship denial passed; missing tenant fails closed; pool tenant state clears on reuse.
- [ ] Broad CRUD checks for future business API modules remain part of those milestones.

### M0.4.5 — Security hardening
- [x] JWT claim validation, replay response, error handling, blank DB password fallback removal, tenant context cleanup, and bounded login/refresh throttles added and tested.
- [ ] Per-process throttling needs a shared edge limiter for scaled production; role/branch authorization and audit coverage belong before business APIs ship.

### M0.4.6 — Frontend/API integration
- [x] Vitest session restoration/coalescing, automatic refresh retry, and session-expiry tests pass; TypeScript, Vite production build and Oxlint pass.
- [ ] Google widget/browser/responsive Command Center verification remains.

### M0.4.7 — CI/CD quality gates
- [x] Workflow authored for Java 17, PostgreSQL 16, Maven verify, frontend typecheck/lint/test/build, Gitleaks and dependency review.
- [ ] GitHub Actions has not executed; workflow result and external scan availability remain unverified.

### M0.4.8 — Documentation and delivery
- [x] Setup docs, environment examples and three validation reports updated with executed results and remaining risks.
- [ ] M0.4 remains in progress until CI and browser/provider checks are complete; no pilot/production readiness claim.

## Validation log

| Date | Check | Result |
|---|---|---|
| 2026-10-19 | `mvn -o -B -ntp clean verify` on JDK 17.0.20 with disposable local PG databases | **Passed:** 13 tests, 0 failed, 0 skipped; package/repackage passed |
| 2026-10-19 | Fresh PostgreSQL V1–V10 and Hibernate validation | **Passed:** clean local scratch DB; 10 successful Flyway versions and checksums recorded |
| 2026-10-19 | Restricted-role RLS and same-clinic constraints | **Passed:** no BYPASSRLS/superuser; read/update/delete/spoof, pool reset and FK denial checks passed |
| 2026-10-19 | `npm ci` | **Passed:** locked dependencies installed; 0 npm audit vulnerabilities |
| 2026-10-19 | `npm test && npm run build && npm run lint` | **Passed:** 3 tests, TypeScript/Vite build and lint with no warnings |
| 2026-10-19 | GitHub Actions | **Not run:** no workflow execution available from this workspace |
| 2026-10-19 | Live Google/browser UI | **Not verified:** controlled signed-token HTTP integration passed; no live provider/browser account used |

Detailed evidence: [runtime report](docs/M0_4_RUNTIME_VALIDATION_REPORT.md), [database report](docs/M0_4_DATABASE_VALIDATION_REPORT.md), [security report](docs/M0_4_SECURITY_REGRESSION_REPORT.md).

## Next recommended task

Run the authored GitHub Actions workflow and review its hosted PostgreSQL 16 and security-scan results; then exercise the Google widget and responsive UI in a browser with a test OAuth client. Complete those checks before starting **M1 — Clinic & Branch Management**.

## Change log

- **2026-10-19 — M0.4 validation and hardening:** corrected Java 17 compile errors; added V10 tenant composite constraints; ran fresh V1–V10 PostgreSQL migration, Hibernate validation, restricted-role RLS and same-clinic tests on local PostgreSQL 18.6; added actual HTTP auth/refresh/concurrency tests, claim validation, replay revocation, login/refresh throttles and error-dispatch correction; added frontend API tests and passed TypeScript/Vite/lint. Backend: 13/13 passed. Frontend: 3/3 passed. M0.4 remains in progress pending GitHub Actions and live browser/provider verification.
