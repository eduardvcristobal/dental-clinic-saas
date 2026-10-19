# Dental Clinic SaaS — Project Status

**Last updated:** 2026-10-19  
**Authoritative baseline:** M0.3 working repository plus M0.4 validation fixes
**Backend:** Java **17 LTS**, Spring Boot 3.3.5, PostgreSQL, Flyway  
**Frontend:** React, TypeScript, Vite  
**Overall status:** M0.7 frontend governance implemented; M0.6 patient journey simulation implemented; M0.5 visual verification and M0.4 security/CI/provider checks remain; **not pilot-ready**.

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
| M0.5 | Role-specific application shell & connected UI previews | Implemented — review/verification pending | Responsive navigation, safe demo state, role-based previews, tests |
| M0.6 | Patient workflow simulation & UX review | Implemented — verification and human design review pending | Seven-step fictional flow, no persistence, role-safe links, responsive review |
| M0.7 | Permissions, module activation & navigation governance | Implemented — verification pending; backend entitlements not started | Central navigation registry, roles and honest readiness labels; no real feature activation |
| M1 | Clinics, branches, dentists, chairs, staff | M1.1–M1.3 implemented; validation pending | Complete tenant/branch onboarding, permissions and CRUD with tests |
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

## Local platform administration update (2026-10-19)
- Added a separate Google platform-admin login using the backend `CLINIC_PLATFORM_ADMIN_EMAILS` allowlist.
- Platform-admin access tokens and refresh sessions have no clinic ID. V11 adds a privileged RLS policy activated only by a validated platform-admin principal; the initial platform page reads a global clinic overview and aggregate staff/patient counts.
- Added `/platform-admin/login` and `/platform-admin`; aligned local backend and frontend OAuth client IDs to the Google Web client ID provided by the owner.
- The local `dental` database applied V11 and the app started. Backend compilation and frontend production build passed. The M0.4 and platform-admin automated tests have not been rerun for this update; browser/Google login remains unverified.
- The local admin overview currently reports clinic metadata and aggregate staff/patient counts. The business module screens and CRUD APIs remain unimplemented.

### Test totals
- Last recorded M0.4 automated run: **16 passed, 0 failed, 0 skipped, 0 blocked** (13 backend + 3 frontend).
- Platform-admin changes: **0 tests run**. Backend compilation and frontend production build passed; these are build checks, not test passes.

## Validation log

| Date | Check | Result |
|---|---|---|
| 2026-10-19 | `mvn -o -B -ntp clean verify` on JDK 17.0.20 with disposable local PG databases | **Passed:** 13 tests, 0 failed, 0 skipped; package/repackage passed |
| 2026-10-19 | Fresh PostgreSQL V1–V10 and Hibernate validation | **Passed:** clean local scratch DB; 10 successful Flyway versions and checksums recorded |
| 2026-10-19 | Platform-admin tenantless session and V11 RLS policy | **Implemented:** local `dental` DB migration and startup passed; build passed; OAuth browser login not yet verified |
| 2026-10-19 | Restricted-role RLS and same-clinic constraints | **Passed:** no BYPASSRLS/superuser; read/update/delete/spoof, pool reset and FK denial checks passed |
| 2026-10-19 | `npm ci` | **Passed:** locked dependencies installed; 0 npm audit vulnerabilities |
| 2026-10-19 | `npm test && npm run build && npm run lint` | **Passed:** 3 tests, TypeScript/Vite build and lint with no warnings |
| 2026-10-19 | GitHub Actions | **Not run:** no workflow execution available from this workspace |
| 2026-10-19 | Live Google/browser UI | **Not verified:** controlled signed-token HTTP integration passed; no live provider/browser account used |

Detailed evidence: [runtime report](docs/M0_4_RUNTIME_VALIDATION_REPORT.md), [database report](docs/M0_4_DATABASE_VALIDATION_REPORT.md), [security report](docs/M0_4_SECURITY_REGRESSION_REPORT.md).

## Next recommended task

Complete a live Google sign-in smoke check at `/platform-admin/login` (after confirming `http://localhost:5173` is an authorized JavaScript origin). Then begin **M1 — Clinic & Branch Management**. GitHub Actions remains deferred per owner direction; run hosted CI when the owner is ready.

## Change log

- **2026-10-19 — M0.4 validation and hardening:** corrected Java 17 compile errors; added V10 tenant composite constraints; ran fresh V1–V10 PostgreSQL migration, Hibernate validation, restricted-role RLS and same-clinic tests on local PostgreSQL 18.6; added actual HTTP auth/refresh/concurrency tests, claim validation, replay revocation, login/refresh throttles and error-dispatch correction; added frontend API tests and passed TypeScript/Vite/lint. Backend: 13/13 passed. Frontend: 3/3 passed. M0.4 remains in progress pending GitHub Actions and live browser/provider verification.
- **2026-10-19 — Platform-admin access:** added allowlisted tenantless Google sessions, refresh rotation/revocation, no-clinic JWT validation, platform-admin UI and cross-clinic summary endpoint. V11 migration applied to local `dental`; Java compile and frontend build passed. Automated tests and live Google sign-in were not rerun.

## M0.5 — Application UI and role-oriented navigation (2026-10-19)

**Status: Implementation delivered; verification and design review pending.** This milestone does not close M0.4 security/provider/CI blockers and does not start M1.

- [x] Added React application sidebar and responsive page shell for clinic users; existing sign-in and separate Platform Admin routes preserved.
- [x] Added role-specific Command Center layouts for `OWNER`, `ADMIN`, `DENTIST`, and `FRONT_DESK`, the four current clinic backend roles. A dedicated `BILLING_STAFF` role is **not** implemented and must be designed with backend permissions in a later milestone.
- [x] Added Patient 360° preview with medical history, FDI adult odontogram selection, a local-only SOAP draft field, plans, visits, and billing examples.
- [x] Added Calendar (day/week/chair mock views), Patients (fictional client-side search), Billing and Reports previews, and intentional coming-soon pages for other planned modules.
- [x] Added explicit preview/coming-soon capability catalog and visibility by current clinic role. These **are presentation controls only**, never an API permission or entitlement enforcement mechanism.
- [x] All illustrative names, clinical data and currency amounts labeled fictional; no prototype edits persist or call a clinical API.
- [x] Added unit tests for clinic role menu visibility and feature-state labels.
- [ ] Human desktop/mobile visual review, real Google/browser login smoke tests, and hosted CI confirmation.
- [ ] Live clinic data, authenticated global search, real branch selection, notices, and modules remain future milestones.

**Security boundary:** Front-end role gating is not authorization. Real APIs must enforce backend permission checks and PostgreSQL tenant isolation. Platform Admin's privileged RLS test/audit findings remain open for production readiness.

## M0.6 — Patient workflow simulation (2026-10-19)

**Status: Implemented; build/test execution blocked by missing npm dependency cache; user design review pending.**

- [x] Added guided `/workspace/journey` preview with seven steps: registration → booking → arrival → examination → treatment planning → billing → recall.
- [x] Included checklist prompts, previous/next navigation, role-aware links and responsive layout.
- [x] All checklist state is temporary React memory; no business API calls, persistence, invoice posting or clinical signing.
- [x] Added static unit tests for journey order, module restrictions and step boundaries.
- [x] Documented exception pathways and acceptance prompts in `docs/M0_6_WORKFLOW_REVIEW.md`.
- [ ] Frontend `npm ci --offline` blocked on missing `why-is-node-running@3.2.1`; Vitest/build/lint not run for this milestone.
- [ ] Human desktop/tablet design review with dentist and receptionist, plus actual browser checks.
- [ ] M0.4 Platform Admin and RLS/provider/CI security blockers remain open.

**Change policy:** M0.6 does not activate M1–M8 modules. The demo does not grant clinical or financial access.

## M0.7 — Permissions, Module Activation & Navigation Governance (2026-10-19)
- [x] Added central frontend governance decisions for role, module state and demo action; unknown roles/modules fail closed.
- [x] Added separate read-only Module Status preview for Owner/Administrator; navigation distinguishes Preview vs Coming soon.
- [x] Patient 360° demo clinical editing is now checked through centralized role decision.
- [x] Created governance regression tests for unknown roles, clinical preview restrictions and unimplemented modules.
- [ ] Unit tests and full production build require installed frontend dependencies; see current verification report.
- [ ] Backend endpoint-level RBAC, subscription entitlements, and real feature activation are **not implemented** in M0.7 and must be addressed before business APIs ship. Frontend decisions are never trusted for backend security.
- [ ] M0.4 platform-admin RLS privilege review, CI and live Google login verification remain outstanding.

**M0.7 scope:** visual readiness/navigation governance, not a clinic entitlement or clinical authorization release. Next: M1 Clinic & Branch Management, subject to open foundation security gates.

## M1 — Clinic & Branch Management (2026-10-19)

**Status: IN PROGRESS — initial existing-clinic configuration implemented; NOT runtime-validated.**

- [x] M1.1 Backend management routes for existing clinic settings and Owner/Admin role enforcement.
- [x] M1.1 Branch create/update/activation, chair create/update/activation, staff-to-branch assignment/unassignment of existing staff.
- [x] M1.1 Operating hours table in Flyway V12, strict weekday/time and tenant branch constraints, RLS enabled/forced.
- [x] M1.1 React-connected administration screens for clinic settings, branches, chairs and staff assignments.
- [x] M1.1 Authorization/validation unit tests authored (not executed).
- [ ] Run JDK 17 Maven tests and fresh PostgreSQL V1–V12 migration, restricted-role RLS tests, cross-tenant and branch assignment cases.
- [ ] Run full frontend install, lint, Vitest and Vite build (offline install blocked by uncached dependency).
- [ ] M1.2 Secure first-clinic/Owner provisioning, user invitations, dentist mapping and audit events.
- [ ] M1.3 Browser UI tests, staff-privilege checks, scheduling/branch availability behavior.
- [ ] Resolve M0.4 Platform Admin cross-tenant privilege and CI/Google provider tests before real clinical data.

See `docs/M1_CLINIC_MANAGEMENT_REPORT.md`. No M1 completion or production-ready claim.

## M1.2 — Secure clinic onboarding, staff invitations & dentist profiles (2026-10-19)

**Status: IMPLEMENTED, NOT VERIFIED; production rollout blocked.**

- [x] V13 invitation and admin event tables, same-clinic FK constraints and RLS, unique dentist/user association.
- [x] Google-verified initial owner and first-branch creation endpoint; public self-registration **disabled by default**.
- [x] Owner/Admin invitation creation, one-time token disclosure, tenant-scoped listing and revocation.
- [x] Google-verified invitation acceptance bound to recipient email with row-level locking and one-time acceptance.
- [x] Existing-staff dentist registration with in-clinic user validation and admin event records.
- [x] Staff management UI for invitation issuance/revocation and dentist profile creation.
- [x] Negative-path unit tests authored.
- [ ] Java 17 compilation/Maven unit and integration tests (environment missing JDK 17/Maven).
- [ ] PostgreSQL V1–V13 migration, restricted-role cross-tenant and replay/concurrency tests.
- [ ] Actual frontend build, lint, browser checks and public invitation acceptance/clinic signup UI.
- [ ] Email invitation delivery, hosted CI, deployment rate limits and outstanding Platform Admin privilege review.

See `docs/M1_2_ONBOARDING_REPORT.md`. Do not treat M1.2 as fully tested or safe for clinical production until M1.3 is complete.

## M1.3 — Security and onboarding integration (2026-10-19)

**Status: Implemented, runtime and production verification BLOCKED.**

- [x] Added dedicated first-clinic registration and staff invitation acceptance React routes.
- [x] Added forward-only V14 policy hardening to remove blanket Platform Admin table access created by V11.
- [x] Restricted Platform Admin dashboard to clinic-directory metadata; no clinical record aggregates queried.
- [x] Extended PostgreSQL-dependent migration and RLS tests to V14 and platform-admin denial checks.
- [x] Added negative onboarding unit tests and prepared validation report.
- [ ] Run JDK 17 Maven test suite on actual Java 17 and PostgreSQL (this environment lacks JDK 17, Maven and PG).
- [ ] Execute fresh V1–V14 migrations and check existing-database upgrade from V13.
- [ ] Run full two-clinic invitation acceptance, concurrency/replay and audit event integration suite.
- [ ] Verify genuine Google browser signup and invitation acceptance.
- [ ] Verify frontend build, lint, tests, responsive design and hosted CI.
- [ ] Close M0.4 outstanding provider/CI and platform security review before production activation.

**Security/verification report:** `docs/M1_3_SECURITY_VALIDATION_REPORT.md`.
**Do not activate real patient data or claim M1.3 production complete until all security gates pass.**

## M1.4 — Runtime regression & onboarding security certification (2026-10-19)

**Status: PARTIALLY IMPLEMENTED; CERTIFICATION BLOCKED. Do not activate real patient data.**

- [x] Invitation acceptance now removes a URL-bearing secret immediately rather than after success; strict no-referrer page policy added.
- [x] Extended disposable-PostgreSQL test for invitation same-tenant foreign keys, tenant RLS and single-use atomic transitions.
- [x] Added fail-closed local validation runner and evidence report.
- [ ] Execute JDK 17 Maven compile and JUnit tests (only JDK 21; no Maven here).
- [ ] Execute PostgreSQL fresh V1–V14 and V13→V14 upgrade validations (no PG instance here).
- [ ] Execute concurrent invitation acceptance through HTTP and verify one success/one staff record.
- [ ] Verify real Google browser workflows and hosted CI.
- [ ] Close all critical security findings before any patient data rollout.

Evidence: `docs/M1_4_VALIDATION_REPORT.md`. M1 remains open pending actual certification evidence.
