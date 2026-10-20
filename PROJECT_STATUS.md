## Current milestone snapshot

M1–M6 were integrated into main and development acceptance was recorded. M7.1–M7.9 have been implemented on a separate feature branch with documented financial closure gaps. Production certification and real-patient/real-money use remain blocked by the security and financial completion gates.

# Dental Clinic SaaS — Project Status

**Last updated:** 2026-10-20
**Authoritative baseline:** M3.10 accepted application source `244ee177b0d85290f5adbd4efe30d99f83704d58`
**Backend:** Java **17 LTS**, Spring Boot 3.5.16, PostgreSQL, Flyway
**Frontend:** React, TypeScript, Vite  
**Overall status:** M1–M3 complete for development; M4 not started; **not production-ready**.

## Progress conventions

- `[x]` Completed and supported by reviewed source or recorded validation.
- `[ ]` Not complete or not verified. Never infer feature completion from an entity, menu, or scaffold.
- Mark a milestone complete only when its acceptance criteria and relevant tests pass with actual evidence.
- Preserve dated findings, blockers, and commits in this file.
- Do not put patient data, access tokens, passwords, or clinic secrets in docs or fixtures.

## Milestone roadmap

| ID | Milestone | Status | Acceptance gate |
|---|---|---|---|
| M0 | Foundation audit & setup | **In progress — local validation and hosted CI passed; live provider check open** | Java 17, local PG migration/RLS/auth tests, frontend checks and hosted workflow passed; real browser/provider verification remains pending |
| M0.5 | Role-specific application shell & connected UI previews | Implemented — review/verification pending | Responsive navigation, safe demo state, role-based previews, tests |
| M0.6 | Patient workflow simulation & UX review | Implemented — verification and human design review pending | Seven-step fictional flow, no persistence, role-safe links, responsive review |
| M0.7 | Permissions, module activation & navigation governance | Implemented — verification pending; backend entitlements not started | Central navigation registry, roles and honest readiness labels; no real feature activation |
| M1 | Clinics, branches, dentists, chairs, staff | **COMPLETE FOR DEVELOPMENT — live E2E and hosted CI passed; production gates remain open** | Complete tenant/branch onboarding, permissions, real-account browser E2E, security and CI gates |
| M2 | Patients, medical history, consent, search | **COMPLETE FOR DEVELOPMENT — production dependency gate remains open** | Registration to searchable protected patient chart; hosted CI passed; high/critical dependency findings tracked for M10 |
| M3 | Multi-dentist/chair scheduling | **COMPLETE FOR DEVELOPMENT — M3.10 local and hosted gates passed** | Appointments, statuses, waitlist, conflict prevention |
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
- [x] Hosted GitHub Actions run `39232339581` passed the Java/PostgreSQL, frontend, synthetic browser, and Gitleaks jobs. The optional OWASP job was skipped under the accepted deferral; see `docs/M1_4_HOSTED_CI_EVIDENCE.md`.

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
| 2026-10-19 | GitHub Actions run 39232339581 | **Passed:** exact SHA `2791a1c578f19174ea609b53ad84ed656fc9faf9`; backend 30/30, frontend 24/24, browser and security jobs passed; OWASP skipped by deferral |
| 2026-10-19 | Live Google/browser UI | **Partial / blocked:** live Google chooser returned an unallowlisted account which was denied; owner sign-in reached the password challenge and awaits owner handoff; no invite issued |

Detailed evidence: [runtime report](docs/M0_4_RUNTIME_VALIDATION_REPORT.md), [database report](docs/M0_4_DATABASE_VALIDATION_REPORT.md), [security report](docs/M0_4_SECURITY_REGRESSION_REPORT.md).

## Historical next recommendation at M0.4 (superseded)

At the M0.4 checkpoint, the recommendation was to complete a live Google smoke check and then begin M1. That recommendation is superseded: M1 is in progress and remains open pending the M1.4.4 gates below.

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

**Status: Local automated and PostgreSQL validation passed; M1.4 NOT CERTIFIED. M1 remains open.**

- [x] Java 17 / Maven clean verify: 26 backend tests passed, 0 failed, 0 skipped; Spring Boot JAR packaged.
- [x] Frontend `npm ci`, tests (15/15), TypeScript/Vite production build, and lint completed. Lint reports two React warnings in `StaffOnboarding.tsx`.
- [x] Fresh isolated PostgreSQL V1–V14 migration and restricted-role RLS/auth integration passed.
- [x] Separate V13 database upgraded by applying V14 only; synthetic patient row persisted; restricted role with platform-admin GUC read zero patient and app_user rows.
- [x] Registration screen visually inspected; registration remains disabled by default and API create attempt returned 403. Unauthenticated Platform Admin API request returned 401.
- [x] Validation, final regression, security, and E2E reports updated.
- [ ] Real Google sign-in; complete authenticated browser onboarding; HTTP invitation acceptance concurrency; responsive multi-route review; saved screenshots; hosted GitHub Actions and dependency advisory scan.
- [ ] Do not activate real patient data or proceed to M2 until remaining M1 gates pass.

Overall automated totals: **41 passed, 0 failed, 0 skipped, 0 blocked** on the successful rerun. The first in-sandbox run was blocked by loopback/socket permissions and was rerun with networking enabled. Existing local `dental` database was not modified. GitHub Actions was reviewed but not pushed/run because no remote is configured and the owner previously deferred workflow use.

See [validation report](docs/M1_4_VALIDATION_REPORT.md), [final regression report](docs/M1_4_FINAL_REGRESSION_REPORT.md), [security certification](docs/M1_4_SECURITY_CERTIFICATION.md), and [E2E report](docs/M1_4_E2E_REPORT.md).

## Next recommended task

Finish M1 acceptance by completing live Google login/invitation acceptance with authorized test accounts and reconciling hosted run evidence. Keep M2 blocked until required functional and CI evidence is verified. Complete the deferred Maven vulnerability assessment before production deployment; deferral does not establish security clearance.

## M1.4 local validation update (2026-10-19)

- Successful verification: backend 26/26, frontend 15/15, production build passed, lint completed with two React warnings.
- PostgreSQL 18.6 fresh V1–V14 and V13→V14 upgrade passed in temporary isolated databases; the existing `dental` database was untouched.
- V14 left no `platform_admin_access` policy; `dental_app` could not view patient or app_user rows with the platform-admin GUC set. Synthetic upgrade patient remained present for its clinic.
- Browser smoke verified the registration form and HTTP disabled-registration/API authorization boundaries. Full real Google and onboarding E2E, responsive screenshot files, hosted CI, and dependency advisory scan remain unverified.
- M1 remains open. Do not proceed to M2 or use real patient records.

Final gate checklist: [docs/M1_4_ACCEPTANCE_CHECKLIST.md](docs/M1_4_ACCEPTANCE_CHECKLIST.md).

## M1.4.1 final acceptance closure attempt (historical snapshot; superseded by M1.4.2–M1.4.4)

**Status: M1 remains OPEN.** Automated server and database gates pass locally. Live provider sign-in, hosted CI, complete browser E2E, and current dependency advisory review are not certified.

- [x] Fixed Google Identity Services initialization to initialize once per client/page, while switching between clinic and Platform Admin screens, and added remount/callback unit coverage.
- [x] Removed the two React lint warnings in `StaffOnboarding.tsx`.
- [x] Extended HTTP integration coverage for owner/invitation/dentist/audit lifecycle, invitation replay/expiry/revocation, concurrent claims, and Platform Admin directory-only authorization.
- [x] Fresh isolated PostgreSQL V1–V14 migration and V13→V14 upgrade passed; restricted-role checks remained green. Existing `dental` DB was not touched.
- [x] Backend 30/30; frontend 17/17; TypeScript/Vite production build and Oxlint passed with no warnings.
- [ ] Playwright suite authored but not certified: the local browser runner did not receive the synthetic Google client ID and remained on the missing-configuration page; responsive route assertions and screenshots therefore did not pass. No screenshot evidence is claimed.
- [ ] Real Google account/browser sign-in remains unverified; no owner account session was exercised and no Google credential was simulated as genuine.
- [ ] Hosted CI remains unrun: no Git remote is configured and the owner previously deferred the GitHub Actions workflow.
- [ ] Current npm audit was blocked by DNS failure to `registry.npmjs.org`; prior audit before adding Playwright reported 0 findings. OWASP Maven dependency-check was interrupted while downloading the 409,883-record NVD catalog. Current dependency risk is unverified.

M1.4.1 successful automated totals: **47 passed, 0 failed, 0 skipped** (30 backend + 17 frontend unit tests). The failed Playwright browser run is tracked separately as **0 passed, 3 failed**; these failures are harness/configuration failures and do not represent live Google or backend authentication. No hosted CI run, no actual Google sign-in, and no browser screenshots. **Blocked certification gates: live Google account/sign-in, complete responsive browser suite/evidence, hosted CI, current dependency scans. Do not start M2 or use real patient data.**

See the detailed certification, browser and security results in `docs/M1_4_1_FINAL_CERTIFICATION_REPORT.md`, `docs/M1_4_1_E2E_RESULTS.md`, and `docs/M1_4_1_SECURITY_RESULTS.md`.

## M1.4.2 blocker resolution (2026-10-19, later; point-in-time record)

**Status: M1 remains OPEN** — but the browser, responsive-layout, frontend-dependency gates moved from BLOCKED to PASS, and the earlier browser-failure diagnosis was corrected.

- [x] Playwright blocker root-caused (two real defects, neither was the previously suspected env/port issue): (1) the `**/api/**` route glob intercepted Vite's own `/src/api/*` modules and served them JSON, blanking the app; (2) `slugFromHost('127.0.0.1')` returned the clinic slug `'127'`, hiding the clinic-code input. Both fixed; the loopback/IP-literal fix is covered by 7 new unit tests in `frontend/src/lib/tenant.test.ts`.
- [x] Browser suite green: **3/3 passed in two consecutive runs**, with responsive coverage (12 route/role combos × desktop/tablet/mobile, horizontal-overflow assertions), 4 screenshots and 3 passing-run traces saved under `docs/evidence/m1_4_1/`.
- [x] Playwright config made deterministic: bundled Chromium (no machine-specific Chrome path), `trace: 'on'`, failure screenshot/video, and a fixed-port `webServer` (`127.0.0.1:5174`, `--strictPort`, fresh start with the synthetic client ID).
- [x] Product fixes surfaced by the suite: mobile grid blow-out (`.ws-columns>*{min-width:0}`; document was 606 px in a 390 px viewport), `signOut` now clears the local session even if the Google script call fails, logout/refresh fixture cookie semantics, single-credential GSI double (no double sign-in race).
- [x] npm audit re-run with registry access: **0 vulnerabilities** (all severities zero) on the current tree including Playwright.
- [x] Backend re-run on a fresh disposable PostgreSQL cluster: **30/30, 0 skipped** — the migration/RLS gate (`MigrationAndTenantIsolationTest`) ran this time; reproduced after recreating both test databases.
- [x] GitHub Actions workflow extended (browser job with evidence upload; opt-in OWASP job gated on an `NVD_API_KEY` secret) and YAML-validated.
- [ ] Real Google sign-in and invitation acceptance with authorized test accounts: still blocked — no OAuth client ID, `frontend/.env`, authorized-origin access or test accounts exist in this environment. Exact requirements listed in the certification report.
- [ ] OWASP dependency-check: NVD catalog (409,901 records) still not downloadable anonymously in the available window; no Maven vulnerability count claimed. Opt-in CI job is ready for when an `NVD_API_KEY` exists.
- [ ] Hosted CI at this M1.4.2 checkpoint: still unrun — no Git remote was configured at that checkpoint; nothing was pushed.

M1.4.2 totals: **57 passed, 0 failed, 0 skipped** (30 backend + 24 frontend + 3 browser), as recorded at that checkpoint. The Maven NVD scan was incomplete then and was formally deferred in M1.4.4; it remains a production security gate. At that checkpoint, hosted CI evidence was also outstanding; run `39232339581` has since been verified. **The live Google sign-in/invitation acceptance gate remains open. Do not start M2 or use real patient data.**

See the detailed certification, browser and security results in `docs/M1_4_1_FINAL_CERTIFICATION_REPORT.md`, `docs/M1_4_1_E2E_RESULTS.md`, and `docs/M1_4_1_SECURITY_RESULTS.md`.

## M1.4.3 — External integration certification handoff (2026-10-19; reconciled in M1.4.4)

**Status at the M1.4.3 handoff: BLOCKED / NOT CERTIFIED. M1 OPEN; M2 and real patient data remain blocked.** The hosted-run assertion added later in commit `07cc18f` conflicts with this handoff and cannot be independently verified from the currently available run evidence; see M1.4.4.

- [x] Reviewed M1.4.2 external certification prerequisites and added read-only preflight script.
- [x] Hardened optional GitHub Actions OWASP scan to fail closed on missing NVD key or scanner errors, with CVSS >= 7 failure threshold.
- [ ] Actual live Google OAuth sign-in and invitation-acceptance evidence (requires authorized OAuth client and test identities).
- [ ] Current Maven OWASP advisory scan (then required NVD access/key; formally deferred by accepted exception in M1.4.4).
- [ ] Hosted CI success evidence (an `origin` remote is configured now; run 39232339581 is documented but its metadata, jobs, SHA, and results have not been independently verified).
- [ ] Independent Java 17/PostgreSQL/full end-to-end certification, final acceptance signoff and M1 closure.

See `docs/M1_4_3_EXTERNAL_CERTIFICATION_REPORT.md`. Previous M1.4.2 reports recorded 57 passing tests; **none were rerun in this handoff**.

## M1.4.4 — NVD deferral and final acceptance reconciliation (2026-10-19)

**Status: M1 OPEN. M2 is not started. The application is not production-ready.**

- [x] Owner accepted deferral of the OWASP Maven/NVD scan to M10 — Production Security Hardening. Status is **DEFERRED — OWNER-ACCEPTED SECURITY EXCEPTION**, never PASS. Maven dependency exposure is unassessed; a completed Maven vulnerability assessment and disposition of applicable findings is required before production deployment.
- [x] Kept the optional `owasp` GitHub Actions job and all existing test/security jobs, dependency review, and secret scanning unchanged.
- [x] Updated `scripts/m1_4_3_preflight.sh`: absent `NVD_API_KEY` emits an advisory and does not set the preflight failure flag. Other prerequisites still determine the exit result.
- [x] Reconciled run 39232339581 from the authenticated GitHub Actions page: push to `main`, exact SHA `2791a1c578f19174ea609b53ad84ed656fc9faf9`; `java17`, `frontend`, `browser`, and `security` succeeded; `owasp` skipped by its optional condition. Backend 30/30, frontend 24/24, synthetic Playwright suite passed, and Gitleaks reported no leaks. See `docs/M1_4_HOSTED_CI_EVIDENCE.md` for job IDs, artifacts, and digests.
- [ ] Real Google sign-in and staff invitation acceptance in isolated clinic `m1-oauth-acceptance-2026`: owner completed real Google Platform Admin sign-in, then real clinic staff sign-in; the clinic workspace and OWNER-only staff page loaded. The invitation form for the user-confirmed second Google account returned `ApiError: Bad Request`; database verification showed zero invitations and zero clinic admin events. Therefore invitation acceptance and staff access remain blocked by the app request failure. No identity token, password, or invitation token was logged or saved.
- [x] Hosted CI run evidence reconciled; it verifies SHA `2791a1c…`, not the current local checkout. The local GitHub CLI token still reports invalid, but the authenticated browser session exposed the run record and evidence.

M1.4.2's recorded local suite totals remain 30 backend + 24 frontend + 3 browser = **57 passed**; these are prior-run records and were not rerun for M1.4.4. Hosted CI evidence is now verified; real provider/invitation verification remains open. The NVD scan is deferred by an owner-accepted exception and remains a production blocker, not an M1 development preflight blocker. Keep M1 open; do not start M2 or use real patient data.

See [M1.4.4 final acceptance report](docs/M1_4_4_FINAL_ACCEPTANCE_REPORT.md), [reconciled checklist](docs/M1_4_ACCEPTANCE_CHECKLIST.md), and [M1.4.3 evidence handoff](docs/M1_4_3_EXTERNAL_CERTIFICATION_REPORT.md).

## M1.4.5 — Invitation fix and live E2E (2026-10-19)

- [x] Confirmed the previously disputed hosted run `39232339581` in the signed-in GitHub web session: overall success on `main`, SHA `2791a1c578f19174ea609b53ad84ed656fc9faf9`; Java/PostgreSQL, frontend, browser and security jobs succeeded; optional OWASP was skipped. Backend 30 tests and frontend 24 tests passed; browser E2E succeeded with synthetic Google credentials. Gitleaks found no leaks. Dependency Review did not execute on this push event because it is PR-only.
- [x] Saved job conclusions and artifact digests in `docs/M1_4_HOSTED_CI_EVIDENCE.md`; updated the M1 acceptance checklist and M1.4.4 reconciliation report.
- [x] Root cause of the HTTP 400 was an empty `role` in the authenticated invitation request (`Invite[email=smitycristobal@gmail.com, role=, branchId=<valid UUID>]`). The owner and active branch were valid. Backend validation rejected the role before database writes, but returned only generic “Bad Request”; the UI did not prevent submitting an empty role. Added explicit backend role/email/branch validation, safe actionable API problem details, duplicate-pending 409 handling serialized on the clinic row, and a required role selection in the UI. The tenant always comes from the authenticated principal.
- [x] Added regression coverage for valid invitation creation, invalid role, invalid/cross-clinic branch, duplicate invitation, unauthenticated access, and authenticated unauthorized user. Existing HTTP integration tests cover acceptance, replay, expiry, revocation and concurrent acceptance.
- [x] Live owner created a `FRONT_DESK` invitation for `smitycristobal@gmail.com` on `Main Test Branch` in isolated clinic `m1-oauth-acceptance-2026`. Recipient completed real Google verification, accepted the invitation, and signed in through Google. The live app displayed the `Reception / Front Desk` workspace and only Front Desk permitted navigation. A read-only database check confirmed an active membership, correct branch assignment, unrevoked/unexpired accepted invitation, and exactly one invitation-created and one invitation-accepted audit event. Credentials and identity tokens were not saved or logged.
- [ ] Live replay of the same consumed invitation link remains unverified. The app cleared the one-time bearer token from the URL and component state after acceptance; we did not retain or extract that secret to force a second submission. Automated replay rejection passed in the isolated HTTP regression suite.
- [x] Java 17 Maven `clean verify`: 31 tests passed; PostgreSQL V1–V14 migrations and restricted-role RLS included. Frontend Oxlint passed; Vitest 25/25; TypeScript/Vite production build passed. Playwright 4/4 passed. Total: **60 passed, 0 failed, 0 skipped, 0 blocked**.
- [x] Reproduced hosted CI evidence remains run `39232339581`, SHA `2791a1c578f19174ea609b53ad84ed656fc9faf9`; `java17`, `frontend`, `browser`, and `security` succeeded, optional `owasp` skipped. This hosted run is historical and is not a verification of the current M1.4.5 commit.
- [ ] Maven NVD scan: **DEFERRED — OWNER-ACCEPTED SECURITY EXCEPTION** to M10. Dependency risk remains unassessed. A completed Maven vulnerability assessment and disposition of findings is mandatory before production deployment.

M1 remains open pending live replay evidence, M2 has not started, and the product is not production-ready. No real patient records were activated. The M10 Maven vulnerability assessment remains required before production deployment.

See [M1.4.5 final certification report](docs/M1_4_5_FINAL_CERTIFICATION_REPORT.md) and the [M1 final acceptance checklist](docs/M1_4_ACCEPTANCE_CHECKLIST.md).

## M1.4.6 — Final M1 acceptance closure (2026-10-19)

**Status: M1 COMPLETE FOR DEVELOPMENT ACCEPTANCE. M2 has not started. The product is not production-ready.**

- [x] Live invitation replay: created a fresh invitation in disposable database `dental_m146_e2e`, accepted it through real Google verification, then submitted the same invitation from a second in-memory browser form. The app returned HTTP 403, `Invalid or expired invitation`. Before/after read-only checks confirmed the accepted timestamp was unchanged; exactly one `FRONT_DESK` membership and one branch assignment existed, with one creation and one acceptance audit event. No invitation token, Google identity token, or credentials were logged or recorded.
- [x] Live new-clinic self-registration: enabled only in the temporary backend process against `dental_m146_e2e`. Two authorized Google test identities registered two clinics. Each clinic had one active OWNER, one initial branch, `Asia/Manila` timezone, one owner-branch assignment, and one `CLINIC_CREATED` event. Owner Google login and the clinic Settings page worked for the first clinic; the second clinic's owner saw the second clinic's own Settings.
- [x] Tenant isolation: while scoped as the restricted `dental_app` role to the second clinic, RLS returned one own branch and zero branches from the first clinic (`1 | 0`). Existing cross-clinic integration tests also passed.
- [x] Self-registration remains disabled by default: `application.yml` defaults `app.onboarding.self-service-enabled` to `false`, and local `.env.local` remains disabled. After stopping the temporary enabled process and restarting with the local default, a registration request returned HTTP 403, `Clinic registration is not open`. No production configuration was changed.
- [x] Registration failure regression: the Java/PostgreSQL suite verifies invalid slug and timezone (400), duplicate slug (409 with safe detail), invalid Google identity (401), and unapproved Origin (403). Duplicate and invalid attempts created no extra clinics.
- [x] Final local regression: OpenJDK 17.0.20 `mvn clean verify` passed **33/33** tests against fresh local PostgreSQL 18.6 auth and migration databases; V1–V14 migrations and restricted-role RLS included. Frontend Oxlint passed, Vitest **25/25**, TypeScript/Vite production build passed, Playwright **4/4** passed. Total **62 passed, 0 failed, 0 skipped, 0 blocked**.
- [x] Hosted GitHub Actions latest run [`39250767984`](https://github.com/eduardvcristobal/dental-clinic-saas/actions/runs/39250767984) passed on SHA `d705257ca701c7c10e12ab5d279acde5631ea6af`: Java 17/PostgreSQL 16, frontend, browser, and security jobs succeeded; optional OWASP was skipped by accepted deferral. Hosted totals: 33 backend + 25 frontend + 4 Playwright = 62 passed, 0 failed, 0 skipped test cases, 0 blocked. Gitleaks reported no leaks in both hosted runs. A full-history scan found one benign historical cookie-attribute false positive; `.gitleaksignore` suppresses only that fingerprint, and the full scan then reported zero findings. The first run failed because shallow checkout omitted the Gitleaks base commit; security checkout now fetches full history. Dependency Review remains PR-only and was not exercised on this push.
- [ ] Dependency vulnerability scan remains **DEFERRED — OWNER-ACCEPTED SECURITY EXCEPTION**, never PASS; complete a Maven vulnerability assessment before production deployment.

Disposable test database `dental_m146_e2e` contains only test clinic/onboarding data and no patient records. M1 is complete for development acceptance. NVD deferral does not confer production security certification; the Maven vulnerability assessment, privacy/security review, operational controls, and production deployment verification remain mandatory before production use.

See [M1.4.6 final acceptance report](docs/M1_4_6_FINAL_ACCEPTANCE_REPORT.md), [live invitation replay evidence](docs/M1_4_6_LIVE_INVITATION_REPLAY.md), [clinic registration E2E](docs/M1_4_6_CLINIC_REGISTRATION_E2E.md), and [hosted CI evidence](docs/M1_4_6_HOSTED_CI_EVIDENCE.md).

## M2.2 — Patient medical history, guardians, consent & identity review (2026-10-19)

**Status: Local backend, PostgreSQL, frontend, and authenticated browser regression passed under M2.5.**

- V15 adds RLS-protected guardian contacts, one medical history per patient, and UNSIGNED/ACKNOWLEDGED consent status.
- Patient-care reads/writes are clinic-scoped, audit-recorded, and role-restricted; FRONT_DESK receives 403 for clinical history.
- Guardian contacts are self-reported, and administrative consent acknowledgement is not a signature or permission to treat.
- V14→V15 upgrade preserved the synthetic history and legacy consent fields. Review legacy consent meaning before production migration.

See `docs/M2_2_DELIVERY_REPORT.md` and the M2.5 PostgreSQL and security reports. NVD was deferred at the earlier M2.2 checkpoint; the current M2.5 scan completed and failed its CVSS threshold. Current findings are summarized under M2.5 below.

## M2.3 — Patient 360° integration and regression (2026-10-19)

**Status: Live local browser and API regression passed; hosted CI remains blocked at M2.5.**

- Patient 360° resolves clinic-scoped patient identity and keeps unfinished odontogram, visits, treatment plans, and billing as Coming soon screens.
- Live Chromium exercised selected-patient rendering, delayed rapid patient switching, and foreign-clinic deep-link denial against the actual backend.
- The mocked UI suite separately passed on Chromium, Firefox, and WebKit at desktop, tablet, and mobile viewports.
- Patient-specific clinical history remains restricted to OWNER/ADMIN/DENTIST; guardians and consent retain their explicit limitations.

See `docs/M2_3_INTEGRATION_REPORT.md` and `docs/M2_5_FRONTEND_BROWSER_REGRESSION.md`.

## M2.4 — Patient identity, guardian and consent security regression (2026-10-19)

**Status: Local security regressions passed under M2.5; overall M2 acceptance remains open pending hosted CI and dependency finding disposition.**

- Patient 360° renders only the currently selected record; stale async responses and foreign UUIDs are denied in local authenticated regression.
- Guardian relationships remain unverified; UNSIGNED/ACKNOWLEDGED statuses do not constitute signed informed consent.
- Stale/concurrent demographic edits now use expected versions and return conflicts rather than overwriting new data.
- Auth/session/invitation and patient-care DTO string forms redact sensitive values. Spring MVC web body and parameter diagnostics remain at INFO even when ambient DEBUG is set.

See `docs/M2_4_SECURITY_REGRESSION_REPORT.md` and `docs/M2_5_SECURITY_FINDINGS.md`.


## M2.5 — Full Runtime Regression & Patient Management Acceptance (updated 2026-10-20)

**Status: IN PROGRESS — M2 remains open.** Local CI-equivalent checks passed, but hosted CI is blocked and the NVD scan failed the CVSS threshold with critical/high dependency matches. Do not start M3 or use real patient data.

- [x] Java 17.0.20 `mvn -B -ntp clean verify`: 53 backend tests passed, 0 failed, 0 skipped; JAR package/repackage passed.
- [x] PostgreSQL 18.6 fresh V1–V15 migration and restricted-role two-clinic RLS/relationship checks passed.
- [x] Separate V14→V15 upgrade preserved synthetic patient history and legacy consent metadata.
- [x] Authenticated HTTP integration: patient create/search/update, clinic-local chart number, duplicate candidates, stale and concurrent edits, care roles, guardian, consent, archive, and audit checks passed.
- [x] Frontend `npm ci` using the lockfile; `npm run lint` completed with two React state-in-effect warnings, Vitest 26/26, TypeScript/Vite production build passed.
- [x] Authenticated Chromium Playwright with a local synthetic Google JWKS and isolated backend/PostgreSQL: patient workflow, duplicate review, guardian/history/consent, archive, delayed patient switching, cross-clinic denial, and role denial passed (1/1).
- [x] Offline/mocked Playwright UI suite passed 12/12 across Chromium, Firefox, WebKit; each browser tested desktop/tablet/mobile layout coverage. This is distinct from live-backend coverage.
- [x] Auth/session/invitation and patient-care DTO string forms redact sensitive values; Spring MVC body/request-parameter logging is not debug-enabled, even under ambient DEBUG. Final live backend logs contained neither JWTs nor patient/clinical test values.
- [x] 2026-10-20 local CI-equivalent rerun: 53 backend tests, 26 frontend unit tests, and 4 Chromium workflow browser tests passed; typecheck/build/lint and Gitleaks passed (lint retains two warnings; Gitleaks found no leaks in the scanned commits).
- [x] OWASP Dependency-Check 12.1.6 completed using the Dependency-Check maintained NVD 2.0 mirror and generated HTML/JSON reports. Results: 84 dependencies scanned (54 unique); 13 matched vulnerable; 179 dependency/CVE matches; 114 unique CVEs (25 critical, 44 high, 39 medium, 6 low by highest severity). The configured `failBuildOnCVSS=7` policy failed as intended. No applicability triage or suppressions have been applied.
- [x] Local NVD API key presence was checked without printing it; a minimal authenticated NVD API request returned HTTP 200. Full direct API feed downloads hung at 78% on Dependency-Check 12.1.0 and 97% on 12.1.1, so the completed local scan used the upstream-maintained mirror. The optional workflow now uses 12.1.6 with connection/read timeouts and a retry limit.
- [ ] Hosted GitHub Actions on the final M2.5 application commit: **BLOCKED**. `gh auth status` reports an invalid stored credential; GitHub API access failed. No current hosted run is claimed.
- [ ] Critical/high dependency findings must be triaged and remediated; the scan's affected-version matching is best-effort and no manual applicability review has been done. Sonatype OSS Index was skipped because no credentials were configured.
- [ ] Live Google provider authentication is not part of the synthetic authenticated Playwright run; prior M1 acceptance evidence is documented separately.
- [ ] Review the V14→V15 legacy consent mapping before production: existing signed metadata is preserved, while the new `consent_status` default is `UNSIGNED` and is not proof of consent.
- [ ] Complete and review the Maven vulnerability assessment before production deployment. The current completed scan is **FAIL** at the configured CVSS threshold; its mirror is best-effort and is not a production approval.

**Latest rerun test totals:** 83 passed (53 backend + 26 frontend unit + 4 Chromium workflow browser tests), 0 failed, 0 skipped. NVD threshold result: **FAIL** (separate security gate). Hosted CI: **BLOCKED**. Earlier M2.5 wider regression totals remain 92 passed (53 backend + 26 frontend unit + 12 cross-browser mocked + 1 live authenticated browser).

**Security boundary:** M2 development acceptance does not authorize real patient information or production clinical workflows. No automatic patient merges are implemented; duplicate detection remains advisory. Guardian relationships remain unverified; administrative consent acknowledgement is not a signed informed consent.

Reports: `docs/M2_5_FINAL_ACCEPTANCE_REPORT.md`, `docs/M2_5_BACKEND_REGRESSION.md`, `docs/M2_5_POSTGRESQL_TENANT_ISOLATION.md`, `docs/M2_5_FRONTEND_BROWSER_REGRESSION.md`, and `docs/M2_5_SECURITY_FINDINGS.md`. Sanitized evidence is under `docs/evidence/m2_5/`.

## M2.5.1 — Dependency triage, safe remediation, hosted CI closure (2026-10-20)

**Status: M2 COMPLETE FOR DEVELOPMENT on run 39348777480. Production remains not certified; M10 gate stands. M3 not started.**

- Spring Boot parent 3.3.5 → **3.5.16** (latest 3.5 patch; major Boot 4 migration deferred by decision), PostgreSQL driver pinned 42.7.14, and in-BOM patch pins: Tomcat **10.1.61** (embedded HTTP server — resolves confirmed-applicable 9.8/9.1 advisories CVE-2026-32990/65637/65905/65182/55276/59083/59084/68525), Jackson-bom **2.21.8** (CVE-2026-54515), log4j-api **2.26.1** (CVE-2026-34477/34479/34481/49844). No major version jumps; advisories cross-checked from the Dependency-Check HTML report and Apache/Jackson release data.
- Residual findings: Spring Framework 6.2.19, Spring Security 6.5.11, Spring Data JPA 3.5.13 (6 artifacts, 23 unique CVEs, up to 9.8) — the newest released versions of their lines have no upstream fix; every advisory was applicability-reviewed against `backend/src/main` (no XSLT views, no WebFlux/SSE/RSocket, no SpEL evaluation, no DPoP/WebAuthn/LDAP, no client-supplied Sort). Documented as deferred residual risk in the M10 re-scan register. No suppressions file was added and the scan still exits nonzero — the result is not gamed.
- The two `react(set-state-in-effect)` warnings are resolved: `ClinicWorkspace.tsx` Patient360 fetch state keyed to the selected patient (`loaded.id === selectedId`) and `PatientRegistry.tsx` `activeSearch` request-version tracking; lint is clean, tsc/build/26 unit tests green.
- Full local regression on the remediated tree: backend 53/53 (0 skipped; `M04_*`/`M25_*` disposable-DB tests enabled), V1–V15 fresh migration + RLS + tenant isolation, auth integration, patient management, V14→V15 upgrade test; frontend 26/26, lint clean, build; Playwright Chromium 4/4; total 83 passed / 0 failed.
- Hosted CI (verified via GitHub CLI API, not assumed): run **39348777480** on `main` commit **a5894c2fe765d3d9b18df3f1c3d3d9894aba364e** — java17, frontend, browser, security all **success**; `owasp` job skipped (opt-in, not configured). HTTPS push was initially rejected for missing `workflow` OAuth scope; resolved by pushing over SSH; gh token remained valid so no re-login was needed.
- Final decision: **PASS — M2 complete for development acceptance**. Residual risk register + acceptance checklist: `docs/M2_5_1_DEPENDENCY_SECURITY_TRIAGE.md`, `docs/M2_5_1_REMEDIATION_REGRESSION.md`, `docs/M2_5_1_HOSTED_CI_EVIDENCE.md`, `docs/M2_5_1_M2_ACCEPTANCE_CHECKLIST.md`.

## M3.1–M3.9 — Scheduling implementation (2026-10-20)

**Handoff status at the time: implemented in source, not yet runtime certified.** Nine milestone commits completed in sequence. Scheduling includes appointment policies, dentist availability, chair blocks, booking, calendar, lifecycle events, arrival queue, waitlist and notification outbox (without external delivery). Additional correction commits harden resource locking and fix V17 RLS migration quoting. M3.10 acceptance below supersedes this handoff status. See `docs/M3_1_TO_M3_9_DELIVERY_REPORT.md` for commits and historical validation limitations.

**M3.10 acceptance scope:** Java 17, Flyway V1–V21/PostgreSQL restricted-role RLS, frontend tests, authenticated browser regression, concurrency, and hosted CI. Synthetic data only. Residual M2.5.1 dependency security findings remain tracked for M10.

## M3.10 — Local scheduling acceptance (2026-10-20)

**Status: COMPLETE FOR DEVELOPMENT on application SHA `244ee17`.** Java 17 Maven `clean verify` passed 70/70 tests with no skips against fresh disposable PostgreSQL databases. This includes V1–V21 migration, V15→V21 upgrade, restricted-role RLS, concurrency, scheduling lifecycle, waitlist, outbox, and timezone/DST checks. Frontend 38/38 unit tests, build, lint, 10/10 mocked Chromium browser tests, and one live synthetic-identity browser workflow passed. Hosted run [39388194937](https://github.com/eduardvcristobal/dental-clinic-saas/actions/runs/39388194937) passed Java 17/PostgreSQL 16, frontend, browser and security; optional OWASP was skipped. See `docs/M3_10_ACCEPTANCE_REPORT.md`. No real patient data was used.

## M4 — Odontogram & Dental Charting (2026-10-20; feature branch)

**Current state: implemented in source; NOT certified or approved for clinical production.** Branch `feature/m4-odontogram` is independent of `main` and M3.10 agent changes. The historical snapshot at the top of this file refers to the earlier main baseline and must be reconciled before final merge.

- [x] M4.1: V22 clinic-scoped odontogram schema, FDI tooth notation and append-only provenance; Java validator and test cases added.
- [x] M4.2: Interactive adult, primary and mixed dentition in Patient 360, selected-patient API integration, surface choices and frontend unit-test cases added.
- [x] M4.3: Authenticated chart read/observation/correction/retraction API with patient-scoped history.
- [x] M4.4: V23 unique event supersession, V24 SQL correction provenance, role gates and security regression test cases.
- [ ] M4.5: Full Java 17, PostgreSQL 16 migrations/RLS, browser E2E, CI and pre-merge verification — NOT RUN in this authoring environment; require actual evidence.

Changes are limited to the M4 branch. Do not interpret an unrun test as passed, do not merge automatically, and do not enable real patient data. See `docs/M4_5_ODONTOGRAM_ACCEPTANCE.md`.


### M4.5 validation closure — 2026-10-20

The M4 feature branch passed hosted CI on application-code commit `a8ca311`, [GitHub Actions run 39374343574](https://github.com/eduardvcristobal/dental-clinic-saas/actions/runs/39374343574): Java 17/Maven 79 tests (67 executed, 12 environment-gated skips), fresh PostgreSQL 16 V1–V24 migration and V21→V24 legacy preservation, restricted-role RLS and clinical history integrity, frontend 42 Vitest tests with typecheck/lint/build, 6 mocked Chromium Playwright tests and 1 real Spring Boot/PostgreSQL odontogram E2E using synthetic local Google JWKS. Gitleaks passed. Detailed job evidence: `docs/M4_5_ODONTOGRAM_ACCEPTANCE.md`.

**M4 development readiness:** COMPLETE FOR DEVELOPMENT **on feature branch**, pending independent PR review, M3.10 baseline reconciliation and explicit owner approval before merging. No real patient data or clinical production use. Maven OWASP/NVD remains DEFERRED to M10, private-repo Dependency Review is unavailable, 12 unrelated environment-gated backend tests remain skipped, Firefox/WebKit clinical browser tests are not yet run. The legacy V4 tooth records are retained but not backfilled to M4 history.


## M5.2 — SOAP draft documentation and clinical diagnoses (2026-10-20)

**Implemented on feature branch `feature/m5-clinical-encounters`; NOT merged.** V26 creates tenant-isolated SOAP drafts, append-only revisions and structured diagnoses. Clinician-restricted APIs enforce optimistic version updates, preserve every draft revision and block edits to finalized/locked encounters. Patient 360 contains draft SOAP/diagnosis interface.

**Runtime results** for application commit `1c6b13c`: [GitHub Actions run 39378743729](https://github.com/eduardvcristobal/dental-clinic-saas/actions/runs/39378743729): Java 17/PG16 passed (92 tests, 80 executed, 12 skipped), frontend passed (42 Vitest), standard Playwright passed, prior real-backend M4 E2E passed, Gitleaks passed. M5-specific real HTTP/browser authoring E2E remains OPEN; M3 gated tests skipped, OWASP/NVD deferred.

**Next milestones:** M5.3 signing/locking/amendments, M5.4 prescriptions/files, M5.5 odontogram/encounter UX integration, M5.6 comprehensive acceptance. Owner authorization needed before any PR merge; no production patient data. See `docs/M5_2_SOAP_DELIVERY.md`.


## M5.3–M5.6 — Clinical workflow development validation, October 20, 2026

**Branch:** `feature/m5-clinical-encounters` (stacked on M4; `main` unchanged).
**Status:** DEVELOPMENT IMPLEMENTATION TESTED; MERGE APPROVAL / PRODUCTION CERTIFICATION PENDING.

- [x] M5.3: immutable signed SOAP snapshot, authenticated sign-off, note locking and append-only amendments (V27).
- [~] M5.4: prescription **draft** API + secure-identifier document metadata foundation (V28); real upload, secure storage and prescription transmission are NOT implemented.
- [x] M5.5: same-patient visit attribution for tooth-finding events and encounter UI (V29), no invented historic associations.
- [x] M5.6: Java 17/PostgreSQL 16, frontend, browser, clinical E2E and Gitleaks run on application SHA `ee83add`.

[GitHub Actions 39383121391](https://github.com/eduardvcristobal/dental-clinic-saas/actions/runs/39383121391): Java 101 total (89 executed, 12 gated skips) zero failures; frontend 42 unit tests; 6 standard Chromium + 2 real Spring Boot/PostgreSQL browser tests; security Gitleaks passed. OWASP/NVD deferred and private-repo Dependency Review unavailable. Firefox/WebKit, production Google, external file storage and electronic prescribing remain unverified. Signed-note attestation is NOT a qualified cryptographic signature.

**Merge gate:** M3 acceptance → M4 PR review/merge → reconcile M5 → rerun CI → explicit owner approval. Not production-certified. See `docs/M5_6_CLINICAL_ACCEPTANCE.md`.


## M6.1–M6.7 — Dental treatment plans (2026-10-20)

**Status:** Implemented and tested for development on `feature/m6-treatment-planning`; NOT merged into main and NOT production certified. Source depends on stacked M5→M4 and still-unreviewed M3 scheduling baseline.

- [x] M6.1 — Existing V4 procedure catalog reuse, authorized manager catalog additions/deactivation, V30 audit events.
- [x] M6.2 — Patient 360 treatment builder, clinic-scoped dentist/coded procedure selection, tooth and item validation.
- [x] M6.3 — BigDecimal estimate, quantity, discount and clinic-specific currency, billing preview without invoice creation.
- [x] M6.4 — Lifecycle/consent record reference, optimistic status versioning, V31 accepted-price locking and auditable history.
- [~] M6.5 — Treatment item progress and completed-encounter evidence implemented; actual appointment booking link remains NOT implemented.
- [~] M6.6 — Odontogram FDI and encounter patient linkage + read-only billing preview; M7 owns actual invoicing/payments.
- [x] M6.7 — Hosted enabled CI passed for application SHA `e2cfbb1`: [run 39387827145](https://github.com/eduardvcristobal/dental-clinic-saas/actions/runs/39387827145). Java/PG16 106 tests total, 94 executed/12 gated skips, 42 frontend Vitest, six standard Chromium and three real backend clinical browser tests passed; Gitleaks passed.

**Gates:** OWASP/NVD deferred to M10, 12 unrelated tests skipped; Firefox/WebKit, production Google and compliance validation not run. Consent reference is not a verified patient e-signature. Actual scheduling and invoice workflows remain future work. PR #3 targets M5 and must not merge until M3/M4/M5 are accepted, a final sync/retest passes, and the owner explicitly authorizes the merge.

See `docs/M6_TREATMENT_PLANNING_ACCEPTANCE.md`.


## M7 — Billing & Collections (2026-10-20)

**Source:** `feature/m7-billing-collections` stacked on accepted M6 `main`; remains development-only until approval. Uses V5 domain + V32 additive migration.

- [x] M7.1 — V32 ledger constraints, tenant RLS, traceable billing-event audit and idempotency.
- [x] M7.2 — Invoice draft, line validation, optimistic issue and currency snapshot.
- [x] M7.3 — Partial/full payment collection with invoice locking and idempotent retry.
- [~] M7.4 — Installment schedules are saved, but payment allocations/paid status need reconciliation workflow.
- [~] M7.5 — Patient statements and payment acknowledgment metadata; official fiscal receipt printing remains outstanding.
- [~] M7.6 — Manager-only audited payment/invoice void; external refund disbursement and credit notes remain outstanding.
- [x] M7.7 — Validated completed-clinical-item import, immutable plan prices, no duplicate billed item.
- [x] M7.8 — Tenant financial metrics and real Patient 360 billing UI for authorized cashier roles.
- [x] M7.9 — Enabled CI passed on app commit `069fa847`: [hosted run 39420766324](https://github.com/eduardvcristobal/dental-clinic-saas/actions/runs/39420766324). OWASP/NVD remains deferred.

**Clinical and financial production use is NOT certified.** Outstanding features, race/load tests, refund settlement, installment matching, fiscal receipts, tax and audit controls are itemized in `docs/M7_BILLING_ACCEPTANCE.md`.
